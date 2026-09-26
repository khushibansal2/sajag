using System;
using System.Collections.Generic;
using UnityEngine;

namespace Sajag.Motion
{
    /// <summary>
    /// Stages a beat as a sequence of cues, driven by the SAME scenario JSON
    /// that defines the scoring rubric.
    ///
    /// This is the single most unusual idea in the codebase, so it is worth
    /// stating plainly: one file says both what the worker must do and how the
    /// scene reveals it. A `staging` block sits next to the `rubric` block in
    /// fire-01.json. Authoring a new safety domain is therefore one file —
    /// not a file, plus a scene, plus an Animator controller, plus a timeline
    /// asset that nobody can merge.
    ///
    ///     "staging": [
    ///       { "at": 0.0,  "cue": "ignite",     "for": 0.9, "ease": "expoIn" },
    ///       { "at": 0.9,  "cue": "light-on",   "for": 0.6 },
    ///       { "at": 1.4,  "cue": "label:panel","for": 0.3, "ease": "quintOut" },
    ///       { "at": 2.0,  "cue": "smoke-fall", "for": 2.4, "ease": "linear" },
    ///       { "at": 2.2,  "cue": "hand-over" }
    ///     ]
    ///
    /// Three rules the staging obeys, enforced here rather than left to taste:
    ///
    ///   ONE THING AT A TIME. Two cues may overlap only if one of them is
    ///   ambient (light, smoke). Two attention-seeking cues at once is how a
    ///   worker misses the one that matters, and in this domain that is not a
    ///   polish problem.
    ///
    ///   NOTHING MOVES WHILE THE WORKER IS DECIDING. Once `hand-over` fires,
    ///   ambient cues continue and everything else is frozen. Motion during a
    ///   decision biases the decision, and we are scoring that decision.
    ///
    ///   THE CLOCK IS THE SAME AT EVERY TIER. Tier 3 has no 3D scene, but it
    ///   runs the identical cue list against 2D plates, so the pacing a Tier 3
    ///   worker experiences matches the Tier 1 one. That is what makes the
    ///   tier-calibrated timing rubric defensible rather than arbitrary.
    /// </summary>
    public sealed class Choreographer : MonoBehaviour
    {
        [Serializable]
        public struct Cue
        {
            public float at;        // seconds from beat start
            public string cue;      // handler id
            public float @for;      // duration; 0 = instantaneous
            public string ease;     // curve name, see Ease.ByName
            public float stagger;   // per-child delay for group cues
        }

        /// <summary>Cues that are allowed to run alongside another cue.
        /// Everything else is exclusive.</summary>
        private static readonly HashSet<string> Ambient = new()
        {
            "light-on", "light-off", "smoke-fall", "smoke-clear",
            "gas-drift", "grain-up", "vignette",
        };

        public event Action<string> OnHandOver;

        private readonly List<Cue> _cues = new();
        private readonly List<Tween> _live = new();
        private readonly Dictionary<string, Action<float>> _handlers = new();
        private float _clock;
        private int _next;
        private bool _handedOver;
        private string _beat;

        /// <summary>Register what a cue id actually does. The float is eased
        /// progress 0..1; a cue with `for: 0` receives exactly one call at 1.</summary>
        public void Bind(string cueId, Action<float> handler) => _handlers[cueId] = handler;

        public void Play(string beat, IEnumerable<Cue> cues)
        {
            _beat = beat;
            _cues.Clear();
            _cues.AddRange(cues);
            _cues.Sort((a, b) => a.at.CompareTo(b.at));
            _live.Clear();
            _clock = 0f;
            _next = 0;
            _handedOver = false;
            Validate();
        }

        private void Update()
        {
            float dt = Time.deltaTime;
            _clock += dt;

            while (_next < _cues.Count && _cues[_next].at <= _clock)
            {
                Fire(_cues[_next]);
                _next++;
            }

            for (int i = _live.Count - 1; i >= 0; i--)
            {
                _live[i].Step(dt);
                if (_live[i].Done) _live.RemoveAt(i);
            }
        }

        private void Fire(Cue cue)
        {
            if (cue.cue == "hand-over")
            {
                _handedOver = true;
                // The worker's clock starts HERE, not at beat start. Scoring a
                // decision from the moment the scene began would punish him for
                // the time the reveal took, which is our pacing, not his
                // hesitation.
                TelemetryEmitter.Measure(_beat, "stage-ready", _clock);
                OnHandOver?.Invoke(_beat);
                return;
            }

            if (!_handlers.TryGetValue(cue.cue, out var handler))
            {
                Debug.LogWarning($"[Sajag] beat {_beat}: no handler bound for cue '{cue.cue}'. " +
                                 "Skipping — a missing visual must never stall the drill.");
                return;
            }

            if (_handedOver && !Ambient.Contains(cue.cue))
            {
                Debug.LogWarning($"[Sajag] beat {_beat}: cue '{cue.cue}' would move the scene " +
                                 "while the worker is deciding. Suppressed.");
                return;
            }

            if (cue.@for <= 0f) { handler(1f); return; }
            _live.Add(new Tween(cue.@for, Ease.ByName(cue.ease), handler));
        }

        /// <summary>
        /// Catch staging mistakes at load, in the editor, rather than at the
        /// evaluator's table. Every one of these has a real failure behind it.
        /// </summary>
        private void Validate()
        {
            bool sawHandOver = false;
            for (int i = 0; i < _cues.Count; i++)
            {
                var c = _cues[i];
                if (c.cue == "hand-over") { sawHandOver = true; continue; }

                if (!Ambient.Contains(c.cue))
                {
                    for (int j = 0; j < _cues.Count; j++)
                    {
                        if (i == j) continue;
                        var o = _cues[j];
                        if (Ambient.Contains(o.cue) || o.cue == "hand-over") continue;
                        bool overlaps = c.at < o.at + Mathf.Max(o.@for, 0.001f) &&
                                        o.at < c.at + Mathf.Max(c.@for, 0.001f);
                        if (overlaps && i < j)
                            Debug.LogWarning(
                                $"[Sajag] beat {_beat}: '{c.cue}' and '{o.cue}' overlap. " +
                                "Two things competing for attention is how a worker misses " +
                                "the one that matters.");
                    }
                }

                if (c.@for > 0f && c.ease == "linear" && c.cue != "smoke-fall" &&
                    c.cue != "gas-drift")
                    Debug.LogWarning(
                        $"[Sajag] beat {_beat}: '{c.cue}' is linear. Linear motion reads as " +
                        "a game; use expoOut unless this is a genuinely constant process.");
            }

            if (!sawHandOver)
                Debug.LogError($"[Sajag] beat {_beat}: no 'hand-over' cue. The worker would " +
                               "never be given control and the beat cannot be scored.");
        }

        /// <summary>Stagger a group so siblings arrive in sequence. Four
        /// extinguishers appearing together is a menu; at 90 ms apart it is a
        /// reveal, and the eye reads left to right in the order you chose.</summary>
        public static IEnumerable<Cue> Group(string cuePrefix, int count, float at,
                                             float duration, string ease = "weighted",
                                             float stagger = Ease.Stagger)
        {
            for (int i = 0; i < count; i++)
                yield return new Cue
                {
                    at = at + i * stagger,
                    cue = $"{cuePrefix}:{i}",
                    @for = duration,
                    ease = ease,
                };
        }
    }
}
