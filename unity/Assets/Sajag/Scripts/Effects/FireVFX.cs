using UnityEngine;
using Sajag.Motion;

namespace Sajag.Effects
{
    /// <summary>
    /// Fire, built entirely in code — no prefab, no authored particle asset,
    /// no texture that is not generated at startup.
    ///
    /// WHY IT LOOKS LIKE SOMETHING RATHER THAN LIKE A PARTICLE DEMO
    ///
    /// One decision does most of the work: the fire is a LIGHT first and a
    /// sprite second. A flickering warm light thrown onto the worker's real
    /// surroundings — his actual wall, his actual floor, lit by ARCore's
    /// estimated ambient — is what sells a fire that is not there. Sprites
    /// alone always read as a sticker on the camera feed. Light lands on
    /// geometry the phone can already see.
    ///
    /// That is also why this is affordable. We are compositing over a live
    /// camera feed: reality supplies the set, the depth and the scale, so the
    /// entire budget goes into one light and about 120 particles.
    ///
    /// COST, in order, highest first — NOT measured on real hardware, see
    /// docs/motion.md. The ordering is reliable; the milliseconds are not.
    ///   1 realtime point light      the expensive, worth-it part
    ///   ~120 particles, 1 draw call moderate; hard ceiling at 130 below
    ///   heat shimmer (quad)         low — first thing to cut
    ///
    /// The light needs URP Asset > Lighting > Additional Lights > Per Pixel,
    /// or it silently does nothing. That setting has cost people a day.
    ///
    /// Do not add: a second realtime light, soft particles (needs a depth
    /// buffer you are not writing), bloom (full-screen, the most expensive
    /// thing you could reach for here).
    /// </summary>
    [RequireComponent(typeof(ParticleSystem))]
    public sealed class FireVFX : MonoBehaviour
    {
        [SerializeField] private Light fireLight;
        [SerializeField] private Transform shimmer;

        // Warm core through to a cooler smoky tip. Deliberately NOT the cartoon
        // orange-to-yellow ramp: real flame on an electrical panel is paler and
        // dirtier, and the whole app's colour language is ISO signage, so a
        // saturated orange would collide with the amber we use for warnings.
        private static readonly Color CoreHot  = new(1.00f, 0.85f, 0.52f);
        private static readonly Color MidFlame = new(0.95f, 0.44f, 0.13f);
        private static readonly Color TipSmoke = new(0.28f, 0.24f, 0.23f);

        private ParticleSystem _ps;
        private ParticleSystem.EmissionModule _emission;
        private float _intensity;          // 0 = out, 1 = fully involved
        private float _targetIntensity;
        private float _baseLightRange;

        private const float MaxEmission = 55f;     // particles/sec at full
        private const float MaxLightLumens = 2.6f;

        private void Awake()
        {
            _ps = GetComponent<ParticleSystem>();
            Configure();
            _baseLightRange = fireLight != null ? fireLight.range : 4f;
            SetIntensity(0f);
        }

        /// <summary>
        /// Everything an authored .prefab would hold, expressed as code.
        ///
        /// Worth the verbosity: a prefab is a binary-ish asset that six people
        /// cannot merge during a 36-hour hackathon, and every number in it is
        /// invisible in a diff. Here the reviewer can see that lifetime is 1.4 s
        /// and argue about it.
        /// </summary>
        private void Configure()
        {
            var main = _ps.main;                       // modules are structs —
            main.duration = 4f;                        // assign to a local, then
            main.loop = true;                          // set through it
            main.startLifetime = new ParticleSystem.MinMaxCurve(0.9f, 1.6f);
            main.startSpeed = new ParticleSystem.MinMaxCurve(0.35f, 0.9f);
            main.startSize = new ParticleSystem.MinMaxCurve(0.12f, 0.34f);
            main.startRotation = new ParticleSystem.MinMaxCurve(0f, Mathf.PI * 2f);
            main.gravityModifier = -0.22f;             // flame rises
            main.simulationSpace = ParticleSystemSimulationSpace.World;
            main.maxParticles = 130;                   // hard ceiling, see budget
            main.startColor = new ParticleSystem.MinMaxGradient(CoreHot, MidFlame);

            _emission = _ps.emission;
            _emission.rateOverTime = 0f;               // driven by SetIntensity

            var shape = _ps.shape;
            shape.enabled = true;
            shape.shapeType = ParticleSystemShapeType.Cone;
            shape.angle = 14f;
            shape.radius = 0.18f;

            // Colour over lifetime: hot at birth, smoky at death. This single
            // gradient is most of why a particle system reads as fire rather
            // than as orange dots.
            var col = _ps.colorOverLifetime;
            col.enabled = true;
            var grad = new Gradient();
            grad.SetKeys(
                new[]
                {
                    new GradientColorKey(CoreHot, 0.00f),
                    new GradientColorKey(MidFlame, 0.35f),
                    new GradientColorKey(TipSmoke, 1.00f),
                },
                new[]
                {
                    new GradientAlphaKey(0.00f, 0.00f),   // fade in, never pop
                    new GradientAlphaKey(0.85f, 0.15f),
                    new GradientAlphaKey(0.55f, 0.60f),
                    new GradientAlphaKey(0.00f, 1.00f),
                });
            col.color = new ParticleSystem.MinMaxGradient(grad);

            // Shrink toward the tip. Flame narrows; smoke would widen, which is
            // why smoke is a separate system (SmokeVolume) and not this one
            // with different numbers.
            var size = _ps.sizeOverLifetime;
            size.enabled = true;
            size.size = new ParticleSystem.MinMaxCurve(1f, new AnimationCurve(
                new Keyframe(0f, 0.55f), new Keyframe(0.3f, 1f), new Keyframe(1f, 0.35f)));

            // A little turbulence so the column never looks extruded.
            var noise = _ps.noise;
            noise.enabled = true;
            noise.strength = 0.35f;
            noise.frequency = 1.4f;
            noise.scrollSpeed = 0.6f;
            noise.quality = ParticleSystemNoiseQuality.Low;   // mobile

            var renderer = _ps.GetComponent<ParticleSystemRenderer>();
            renderer.renderMode = ParticleSystemRenderMode.Billboard;
            renderer.sortMode = ParticleSystemSortMode.YoungestInFront;
            renderer.alignment = ParticleSystemRenderSpace.View;
        }

        /// <summary>Bound to the choreographer's "ignite" cue. Use ExpoIn —
        /// fire is consequence, and consequence should keep coming rather than
        /// easing politely to a stop.</summary>
        public void SetIntensity(float t)
        {
            _targetIntensity = Mathf.Clamp01(t);
        }

        /// <summary>Bound to the extinguisher. The worker sees the emission
        /// rate fall as he sweeps correctly — which is the pedagogical point
        /// of beat 1.3 and cannot be reproduced by a poster.</summary>
        public void Suppress(float amount01)
        {
            _targetIntensity = Mathf.Clamp01(_targetIntensity - amount01);
        }

        public bool IsOut => _intensity < 0.02f;

        private void Update()
        {
            // Fire never snaps. Even suppression has a tail, because a fire
            // that vanishes the instant you aim correctly teaches the wrong
            // thing about how long a discharge actually takes.
            _intensity = Mathf.MoveTowards(_intensity, _targetIntensity, Time.deltaTime * 0.8f);

            _emission.rateOverTime = _intensity * MaxEmission;

            if (fireLight == null) return;

            // The flicker is applied to intensity AND range. Intensity alone
            // reads as a dimmer being turned; range makes the pool of light
            // breathe, which is what the eye actually recognises as flame.
            float f = Ease.Flicker(Time.time) * 0.18f;
            fireLight.intensity = Mathf.Max(0f, MaxLightLumens * _intensity * (1f + f));
            fireLight.range = _baseLightRange * (0.9f + 0.1f * _intensity) * (1f + f * 0.5f);
            fireLight.color = Color.Lerp(MidFlame, CoreHot, 0.35f + 0.25f * _intensity);

            if (shimmer != null)
            {
                shimmer.gameObject.SetActive(_intensity > 0.15f);
                shimmer.localScale = Vector3.one * (0.8f + 0.6f * _intensity);
            }
        }
    }
}
