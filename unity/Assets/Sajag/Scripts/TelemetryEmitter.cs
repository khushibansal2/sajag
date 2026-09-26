using System;
using System.Collections.Generic;
using UnityEngine;

namespace Sajag
{
    /// <summary>
    /// The only way a Unity scene is allowed to talk about what the worker did.
    ///
    /// Unity is a content player here, not the system of record (decision
    /// D-01). It never scores anything, never decides pass or fail, and never
    /// touches the database. It emits typed facts — "he chose the CO2
    /// cylinder", "he aimed 9 degrees off the base", "he was upright in smoke"
    /// — and the Kotlin host appends them to the signed, append-only log where
    /// the scoring engine reads them.
    ///
    /// That split is what makes an attempt replayable years later: the scene
    /// can be rebuilt, re-lit, re-authored, and the recorded facts still mean
    /// exactly what they meant on the day.
    ///
    /// If you ever find yourself adding a score to this file, stop. The rubric
    /// lives in the scenario JSON and the engine lives in Kotlin/Python.
    /// </summary>
    public static class TelemetryEmitter
    {
        /// <summary>Set by the host when it launches the Unity activity.</summary>
        public static string AttemptId { get; internal set; }

        /// <summary>Tier actually in use for THIS event — the watchdog can
        /// demote mid-scene, so never cache this at scene start.</summary>
        public static Func<int> CurrentTier = () => 1;

        private static int _seq;

        // ---------------------------------------------------------- choices

        /// <summary>Equipment selection, hazard classification, exit choice.</summary>
        public static void Choice(string beat, string itemId, IEnumerable<string> chosen)
        {
            Emit(beat, itemId, "ITEM", new Dictionary<string, object> {
                { "chosen", new List<string>(chosen) }
            });
        }

        /// <summary>Ordered procedure — P-A-S-S, permit steps, donning order.</summary>
        public static void Sequence(string beat, string itemId, IEnumerable<string> order)
        {
            Emit(beat, itemId, "ITEM", new Dictionary<string, object> {
                { "order", new List<string>(order) }
            });
        }

        /// <summary>A measured quantity: aim angle, standoff, head height.
        /// Rounded to 4 dp so the canonical log is byte-identical on every
        /// device — float formatting differs across ABIs and that would break
        /// the attempt digest.</summary>
        public static void Measure(string beat, string itemId, float value)
        {
            Emit(beat, itemId, "ITEM", new Dictionary<string, object> {
                { "value", Math.Round(value, 4) }
            });
        }

        /// <summary>Elapsed time for a timed item. The rubric applies the
        /// tier calibration factor; the scene does not.</summary>
        public static void Latency(string beat, string itemId, float seconds)
        {
            Emit(beat, itemId, "ITEM", new Dictionary<string, object> {
                { "ms", Mathf.RoundToInt(seconds * 1000f) }
            });
        }

        /// <summary>Did he do it at all — gauge check, atmosphere test,
        /// comms check, standby posted.</summary>
        public static void Flag(string beat, string itemId, bool performed)
        {
            Emit(beat, itemId, "ITEM", new Dictionary<string, object> {
                { "value", performed }
            });
        }

        // ------------------------------------------------------- hard fails

        /// <summary>
        /// A hard fail STOPS the beat. It is not a deduction — the host halts
        /// the scene, replays the teaching, and sends the worker back. Emitting
        /// this and then letting the scene continue is a bug: the worker would
        /// be certified-blocked without understanding why.
        /// </summary>
        public static void HardFail(string beat, string code)
        {
            Emit(beat, null, code, new Dictionary<string, object>());
            NativeBridge.RequestBeatReplay(beat, code);
        }

        // ------------------------------------------------------- tier change

        /// <summary>Recorded so the replay shows exactly when the scene stopped
        /// being world-anchored, and so a reviewer can see the gallery was too
        /// dark rather than assuming the worker fumbled.</summary>
        public static void TierDemoted(string beat, int from, int to, string why)
        {
            Emit(beat, null, "TIER_DEMOTED", new Dictionary<string, object> {
                { "from", from }, { "to", to }, { "why", why }
            });
        }

        // ------------------------------------------------------------ core

        private static void Emit(
            string beat, string itemId, string type, Dictionary<string, object> payload)
        {
            if (string.IsNullOrEmpty(AttemptId))
            {
                Debug.LogError("[Sajag] telemetry emitted with no attempt — the host " +
                               "must call SetAttempt() before loading a scenario. " +
                               "Dropping the event rather than writing an orphan.");
                return;
            }

            var envelope = new Dictionary<string, object> {
                { "attemptId", AttemptId },
                { "seq", _seq++ },
                { "atMs", DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() },
                { "beat", beat },
                { "item", itemId },
                { "type", type },
                { "tier", CurrentTier() },
                { "payload", payload },
            };

            NativeBridge.AppendEvent(MiniJson.Serialize(envelope));
        }

        internal static void ResetForAttempt(string attemptId)
        {
            AttemptId = attemptId;
            _seq = 0;
        }
    }
}
