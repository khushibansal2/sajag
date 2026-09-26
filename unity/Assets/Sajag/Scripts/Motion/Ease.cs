using System;
using UnityEngine;

namespace Sajag.Motion
{
    /// <summary>
    /// The motion vocabulary. Read this before touching any other file in
    /// Motion/ — everything else assumes these curves.
    ///
    /// THE LOOK WE ARE AFTER, AND WHY IT IS CHEAP
    ///
    /// The cinematic-documentary feel people recognise comes far more from
    /// timing than from rendering. Three things do most of the work, and all
    /// three are free on a Helio G85:
    ///
    ///   1. Heavy ease-out. Things start fast and settle slowly over a long
    ///      tail. Linear motion reads as a game; ExpoOut and QuintOut read as
    ///      a camera move. Nothing in this app is allowed to be Linear except
    ///      a genuinely constant process (a gas reading drifting, a timer).
    ///
    ///   2. Staggered entrances. Four objects appearing together is a menu.
    ///      The same four at 90 ms apart is a reveal. See Choreographer.
    ///
    ///   3. Held beats. A pause before the important thing is the single
    ///      strongest tool here, and it costs a float.
    ///
    /// What we deliberately do NOT copy: volumetric light, real depth of
    /// field, heavy bloom. Those are offline-render luxuries. We are
    /// compositing over a live camera feed with about 6-8 ms of GPU to spend,
    /// and the feed already supplies real depth, real lighting and real scale
    /// — the things an offline render has to manufacture. Draw three objects
    /// beautifully instead of a world adequately.
    ///
    /// DURATIONS — use these constants, do not invent numbers per call site.
    /// Consistent timing across twenty-five beats is most of what makes an
    /// app feel authored rather than assembled.
    /// </summary>
    public static class Ease
    {
        // ---- the house durations, in seconds -------------------------------
        public const float Instant = 0.12f;   // acknowledgement: a tap landed
        public const float Quick   = 0.28f;   // UI, labels, small props
        public const float Settle  = 0.55f;   // an object arriving in the world
        public const float Reveal  = 0.90f;   // the hazard appearing — the money shot
        public const float Dread   = 2.40f;   // smoke descending, gas creeping

        // ---- the house stagger ---------------------------------------------
        public const float Stagger = 0.09f;   // between siblings in a group

        public delegate float Curve(float t);

        /// <summary>Default for anything entering the world. Long tail.</summary>
        public static float ExpoOut(float t) =>
            t >= 1f ? 1f : 1f - Mathf.Pow(2f, -10f * t);

        /// <summary>Slightly less extreme than ExpoOut. Use for UI.</summary>
        public static float QuintOut(float t) => 1f - Mathf.Pow(1f - t, 5f);

        /// <summary>Both ends eased. Use for camera-like moves and for anything
        /// that starts and stops in view.</summary>
        public static float QuintInOut(float t) => t < 0.5f
            ? 16f * t * t * t * t * t
            : 1f - Mathf.Pow(-2f * t + 2f, 5f) / 2f;

        /// <summary>Overshoots and comes back. Use ONCE per beat at most — on
        /// the object the worker must notice. Overused it reads as cartoonish,
        /// which is the wrong register for a fire.</summary>
        public static float BackOut(float t)
        {
            const float c1 = 1.70158f, c3 = c1 + 1f;
            return 1f + c3 * Mathf.Pow(t - 1f, 3f) + c1 * Mathf.Pow(t - 1f, 2f);
        }

        /// <summary>Critically-damped settle — arrives without overshoot but
        /// with weight. The right curve for a heavy object: an extinguisher
        /// cylinder, a steel door.</summary>
        public static float Weighted(float t)
        {
            float u = 1f - t;
            return 1f - u * u * (1f + 3f * t);
        }

        /// <summary>Ramps in slowly then accelerates. Reserved for consequence:
        /// fire spreading, a reading climbing toward the withdrawal threshold.
        /// Dread should never ease out — it should keep coming.</summary>
        public static float ExpoIn(float t) =>
            t <= 0f ? 0f : Mathf.Pow(2f, 10f * t - 10f);

        /// <summary>Constant. Permitted only for genuinely constant processes.</summary>
        public static float Linear(float t) => t;

        public static Curve ByName(string name) => name switch
        {
            "expoOut"    => ExpoOut,
            "quintOut"   => QuintOut,
            "quintInOut" => QuintInOut,
            "backOut"    => BackOut,
            "weighted"   => Weighted,
            "expoIn"     => ExpoIn,
            "linear"     => Linear,
            _            => ExpoOut,
        };

        /// <summary>
        /// A flicker signal built from three out-of-phase sine waves so it
        /// never visibly repeats. Used for firelight intensity.
        ///
        /// Why not a noise texture: this is one line of maths per frame per
        /// light versus a texture fetch, and on the target device we are
        /// counting both. Why three waves: two produce an audible-looking
        /// beat pattern within a few seconds, which the eye picks up as fake.
        /// </summary>
        public static float Flicker(float time, float speed = 1f)
        {
            float a = Mathf.Sin(time * 11.13f * speed);
            float b = Mathf.Sin(time * 17.77f * speed + 1.7f) * 0.6f;
            float c = Mathf.Sin(time * 29.31f * speed + 4.1f) * 0.25f;
            return (a + b + c) / 1.85f;   // roughly -1..1
        }
    }

    /// <summary>
    /// A tween that reports its own timing to the assessment engine.
    ///
    /// This is the reason not to use the Animator for gameplay motion. The
    /// scoring rubric needs to know WHEN the door finished opening, how long
    /// the worker held the nozzle on the base, how long the smoke had been
    /// descending when he finally moved. An Animator state machine knows all
    /// of that and tells you none of it without extra plumbing. A tween that
    /// owns its own clock can hand the number straight to TelemetryEmitter.
    /// </summary>
    public sealed class Tween
    {
        public float Elapsed { get; private set; }
        public float Duration { get; }
        public bool Done => Elapsed >= Duration;
        public float Progress => Duration <= 0f ? 1f : Mathf.Clamp01(Elapsed / Duration);
        public float Value => _curve(Progress);

        private readonly Ease.Curve _curve;
        private readonly Action<float> _apply;
        private readonly Action _onComplete;
        private bool _completed;

        public Tween(float duration, Ease.Curve curve, Action<float> apply,
                     Action onComplete = null)
        {
            Duration = Mathf.Max(0f, duration);
            _curve = curve ?? Ease.ExpoOut;
            _apply = apply;
            _onComplete = onComplete;
        }

        /// <summary>Drive from a single owner per beat — never from OnUpdate on
        /// dozens of MonoBehaviours. One update loop is measurably cheaper and,
        /// more importantly, gives the choreographer a single ordered clock,
        /// which is what makes staggering reproducible across tiers.</summary>
        public void Step(float dt)
        {
            if (_completed) return;
            Elapsed += dt;
            _apply?.Invoke(Value);
            if (Done)
            {
                _completed = true;
                _apply?.Invoke(_curve(1f));   // land exactly on the end value
                _onComplete?.Invoke();
            }
        }
    }
}
