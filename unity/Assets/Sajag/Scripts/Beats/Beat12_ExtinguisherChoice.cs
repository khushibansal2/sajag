using System.Collections.Generic;
using UnityEngine;
using Sajag.Effects;
using Sajag.Motion;

namespace Sajag.Beats
{
    /// <summary>
    /// Beat 1.2 — select the right cylinder from a rack of four.
    ///
    /// This is the vertical slice. It is deliberately the FIRST beat to build
    /// end to end, for three reasons:
    ///
    ///   * it exercises every part of the seam — choreographer staging,
    ///     telemetry out, a hard fail, a taught replay, tier fallback — in
    ///     about forty lines of actual logic;
    ///   * it is the moment in the jury demo where you fail on purpose, so it
    ///     has to be the most solid thing in the build;
    ///   * if TelemetryEmitter can express this cleanly, it can express the
    ///     other twenty-four beats, and if it cannot, you want to know in
    ///     October rather than in December.
    ///
    /// Note how little is here. The rubric lives in fire-01.json, the scoring
    /// lives in Kotlin, the staging lives in the same JSON as the rubric. This
    /// class knows about cylinders and nothing else — which is what makes
    /// authoring beat 26 a content job.
    /// </summary>
    public sealed class Beat12_ExtinguisherChoice : MonoBehaviour
    {
        private const string BeatId = "1.2";

        /// <summary>Order matters — it is the left-to-right order on the rack,
        /// and therefore the order the staggered reveal walks through. Water
        /// sits first because that is the one a worker reaches for.</summary>
        private static readonly string[] Cylinders = { "water", "foam", "co2", "dcp" };

        /// <summary>ISO/IS extinguisher body-band colours. These are not a
        /// palette choice — they are the colours on the real cylinders in the
        /// plant, and using anything else would teach the wrong recognition.
        /// Confirm the exact Indian band scheme with the domain reviewer before
        /// the pilot; IS 15683 governs it.</summary>
        private static readonly Color[] BandColours =
        {
            new(0.10f, 0.42f, 0.78f),   // water   — blue band
            new(0.92f, 0.78f, 0.20f),   // foam    — yellow band
            new(0.12f, 0.12f, 0.13f),   // CO2     — black band
            new(0.78f, 0.20f, 0.12f),   // DCP     — red band
        };

        [SerializeField] private Choreographer choreographer;
        [SerializeField] private FireVFX fire;
        [SerializeField] private Transform rackRoot;
        [SerializeField] private Transform[] cylinderSlots = new Transform[4];

        private readonly List<string> _chosen = new();
        private bool _gaugeChecked;
        private bool _live;               // has the worker been handed control
        private float _handOverTime;

        // --------------------------------------------------------------- setup

        public void Begin(IEnumerable<Choreographer.Cue> staging)
        {
            _chosen.Clear();
            _gaugeChecked = false;
            _live = false;

            // Bind what this beat can show. CineGrade binds the ambient cues
            // separately; a beat only ever binds its own props.
            choreographer.Bind("rack-in", RackIn);
            for (int i = 0; i < cylinderSlots.Length; i++)
            {
                int idx = i;                       // capture, not the loop var
                choreographer.Bind($"cyl:{idx}", t => CylinderIn(idx, t));
            }
            choreographer.OnHandOver += OnHandOver;
            choreographer.Play(BeatId, staging);
        }

        private void OnHandOver(string beat)
        {
            _live = true;
            _handOverTime = Time.time;
        }

        // ------------------------------------------------------------ staging

        private void RackIn(float t)
        {
            if (rackRoot == null) return;
            // Arrives from slightly below and settles. Weighted, not BackOut —
            // a steel rack does not bounce, and the one permitted overshoot in
            // this beat is spent on the cylinders.
            rackRoot.localPosition = Vector3.Lerp(new Vector3(0f, -0.35f, 0f), Vector3.zero, t);
            rackRoot.localScale = Vector3.one * Mathf.Lerp(0.92f, 1f, t);
        }

        private void CylinderIn(int index, float t)
        {
            if (index >= cylinderSlots.Length || cylinderSlots[index] == null) return;
            var slot = cylinderSlots[index];
            slot.gameObject.SetActive(true);
            slot.localPosition = new Vector3(slot.localPosition.x,
                                             Mathf.Lerp(-0.22f, 0f, t),
                                             slot.localPosition.z);
            slot.localScale = Vector3.one * Mathf.Lerp(0.8f, 1f, t);
        }

        // ------------------------------------------------------------- input

        /// <summary>Called by whichever tier is running — a raycast hit at
        /// Tier 1, a marker-relative tap at Tier 2, a button at Tier 3. The
        /// beat does not know or care which, because the rubric must not.</summary>
        public void OnCylinderPicked(int index)
        {
            if (!_live || index < 0 || index >= Cylinders.Length) return;

            string id = Cylinders[index];
            _chosen.Clear();
            _chosen.Add(id);

            TelemetryEmitter.Latency(BeatId, "select-latency", Time.time - _handOverTime);
            TelemetryEmitter.Flag(BeatId, "gauge-check", _gaugeChecked);
            TelemetryEmitter.Choice(BeatId, "ext-choice", _chosen);

            // Water or foam on an energised panel is a HARD FAIL, not a
            // deduction. The scene plays the consequence, the host blocks
            // certification, and the worker comes back to this beat until he
            // does it clean. Training and testing are the same act.
            if (id == "water" || id == "foam")
            {
                PlayConsequence(index);
                TelemetryEmitter.HardFail(BeatId, "WATER_ON_ELECTRICAL");
                _live = false;
                return;
            }

            NativeBridge.ScenarioComplete($"{BeatId}:passed");
            _live = false;
        }

        /// <summary>Inspecting the cylinder is scored separately from choosing
        /// it. An in-date CO2 with a dead gauge is still the wrong cylinder to
        /// carry to a fire.</summary>
        public void OnGaugeInspected() => _gaugeChecked = true;

        // ------------------------------------------------------- consequence

        /// <summary>
        /// The fire flares instead of going out.
        ///
        /// Deliberately NOT an explosion, a screen shake or a red flash. The
        /// worker should remember that water made it worse, not that the app
        /// did something dramatic. Restraint is the whole aesthetic, and here
        /// it is also the pedagogy.
        /// </summary>
        private void PlayConsequence(int index)
        {
            if (fire != null) fire.SetIntensity(1f);
            if (index < cylinderSlots.Length && cylinderSlots[index] != null)
                cylinderSlots[index].gameObject.SetActive(false);   // it is spent
        }

        // ------------------------------------------------------------- colours

        /// <summary>Used by the scene builder so the band colours live in one
        /// place rather than being typed into a material by hand.</summary>
        public static Color BandColourFor(int index) =>
            index >= 0 && index < BandColours.Length ? BandColours[index] : Color.grey;

        public static string CylinderIdFor(int index) =>
            index >= 0 && index < Cylinders.Length ? Cylinders[index] : "unknown";

        public static int CylinderCount => Cylinders.Length;

        private void OnDestroy()
        {
            if (choreographer != null) choreographer.OnHandOver -= OnHandOver;
        }
    }
}
