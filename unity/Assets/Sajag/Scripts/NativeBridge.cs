using UnityEngine;

namespace Sajag
{
    /// <summary>
    /// The Unity-as-a-Library boundary (decision D-01).
    ///
    /// Everything crossing between Unity and the Kotlin host goes through this
    /// one class, as JSON strings. That is deliberate: a narrow, typed, easily
    /// logged seam is the difference between a UaaL integration that takes an
    /// afternoon to debug and one that eats the finale.
    ///
    /// The host calls IN via UnitySendMessage to a "SajagBridge" GameObject.
    /// Unity calls OUT via the AndroidJavaObject below. Nothing else in the
    /// Unity project is allowed to touch AndroidJavaObject.
    ///
    /// Integration order that works (do this in October, never at the finale):
    ///   1. Build the Unity project as a library module (Android > Export Project).
    ///   2. Add it to settings.gradle as :unityLibrary.
    ///   3. Host activity extends UnityPlayerActivity; launch it with the
    ///      attempt id and scenario id as intent extras.
    ///   4. Verify the round trip with ping/pong BEFORE wiring any real scene.
    ///
    /// If UaaL is not working by the end of the October integration sprint,
    /// the pre-decided fallback is two APKs launched by intent. That decision
    /// is made in advance so nobody debates it at hour 22.
    /// </summary>
    public static class NativeBridge
    {
#if UNITY_ANDROID && !UNITY_EDITOR
        private static AndroidJavaObject _host;

        private static AndroidJavaObject Host
        {
            get
            {
                if (_host == null)
                {
                    using (var player = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
                    {
                        _host = player.GetStatic<AndroidJavaObject>("currentActivity");
                    }
                }
                return _host;
            }
        }
#endif

        // ------------------------------------------------------------ out

        /// <summary>Append one telemetry event to the host's signed log.
        /// Fire-and-forget on purpose — a scene must never block on storage.</summary>
        public static void AppendEvent(string json)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            Host.Call("appendEvent", json);
#else
            Debug.Log("[Sajag->host] event " + json);
#endif
        }

        /// <summary>A hard fail stops the beat and asks the host to play the
        /// teaching sequence again. The host owns that flow, not Unity.</summary>
        public static void RequestBeatReplay(string beat, string code)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            Host.Call("requestBeatReplay", beat, code);
#else
            Debug.Log($"[Sajag->host] replay {beat} because {code}");
#endif
        }

        /// <summary>Scene finished. The host scores, mints the provisional
        /// credential and shows the result — Unity never does.</summary>
        public static void ScenarioComplete(string scenarioId)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            Host.Call("onScenarioComplete", scenarioId);
#else
            Debug.Log("[Sajag->host] complete " + scenarioId);
#endif
        }

        // ------------------------------------------------------------- in
        // Called by the host via UnitySendMessage("SajagBridge", "<method>", "<arg>").

        /// <summary>Host hands over the attempt id before any scene loads.</summary>
        public void SetAttempt(string attemptId)
        {
            TelemetryEmitter.ResetForAttempt(attemptId);
        }

        /// <summary>Host pushes the tier the watchdog has settled on. Unity
        /// reads it per event rather than caching it, because the watchdog can
        /// demote in the middle of a beat.</summary>
        public void SetTier(string tier)
        {
            if (int.TryParse(tier, out var t))
            {
                TelemetryEmitter.CurrentTier = () => t;
            }
        }

        /// <summary>Language switch mid-session — the demo does this live.
        /// Voice lines are bundled Opus; text is accompaniment only.</summary>
        public void SetLocale(string locale)
        {
            VoiceDirector.SetLocale(locale);   // "hi", "sat", "en"
        }
    }
}
