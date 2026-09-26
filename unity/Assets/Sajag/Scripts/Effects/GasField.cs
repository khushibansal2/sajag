using UnityEngine;

namespace Sajag.Effects
{
    /// <summary>
    /// The methane layer — beat 2.1, and the best thirty seconds in the whole
    /// demo. Also, notably, the cheapest: this file contains no art at all.
    ///
    /// A 3D scalar field is anchored to the room. The virtual detector reads
    /// the field AT THE DEVICE'S ACTUAL POSITION, so the worker discovers by
    /// physically lifting the phone toward the roof that the cavity reads
    /// dangerously and knee height reads clean. Nobody in the room has walked
    /// through a gas cloud before, and it cannot be faked with a video — which
    /// is exactly why it belongs in the demo and why it is worth real hours.
    ///
    /// The physics is deliberately simple and deliberately CORRECT in the one
    /// way that matters to a DGMS officer watching: methane is lighter than
    /// air and layers at the roof and in cavities; heavier products pool at the
    /// floor of a sump. Get that backwards and you have lost the room, however
    /// good the rendering is.
    ///
    /// BUDGET: a handful of exp() per frame. Rendering the cloud is optional —
    /// see ShowCloud — and the beat works completely without it, because the
    /// lesson is the READING changing as you move, not the pretty volume.
    /// </summary>
    public sealed class GasField : MonoBehaviour
    {
        [System.Serializable]
        public struct Species
        {
            public string id;              // "ch4", "co2"
            public bool rises;             // lighter than air?
            public float peakHeightM;      // metres relative to this anchor
            public float peakPercent;      // concentration at the peak
            public float falloffM;         // vertical sigma
            public float lateralFalloffM;  // horizontal sigma; 0 = fills the gallery
        }

        [SerializeField] private Species[] species;
        [SerializeField] private Transform source;        // the leak point
        [SerializeField] private bool showCloud = true;
        [SerializeField] private ParticleSystem cloud;

        /// <summary>Seeded from the scenario's gas_field block so a new domain
        /// is authored, not coded.</summary>
        public void Configure(Species[] s, Transform leakSource)
        {
            species = s;
            source = leakSource;
        }

        /// <summary>
        /// Concentration in percent at a world position, for one species.
        ///
        /// Gaussian in the vertical, optionally Gaussian in the horizontal. It
        /// is not a fluid simulation and does not pretend to be — what it has
        /// to be is monotonic and legible: move up, the number goes up, every
        /// time, with no noise that makes the worker doubt his own hand. A
        /// noisy reading would teach hesitation, which is the opposite of the
        /// lesson.
        /// </summary>
        public float SampleAt(Vector3 worldPos, string speciesId)
        {
            if (species == null || source == null) return 0f;

            foreach (var s in species)
            {
                if (s.id != speciesId) continue;

                Vector3 local = source.InverseTransformPoint(worldPos);
                float dy = local.y - s.peakHeightM;
                float sigmaV = Mathf.Max(0.05f, s.falloffM);
                float v = Mathf.Exp(-(dy * dy) / (2f * sigmaV * sigmaV));

                // Below a rising gas (or above a sinking one) the concentration
                // falls off faster than the Gaussian tail suggests, because the
                // gas is actively leaving that region rather than diffusing
                // into it. One multiply, and it makes crouching feel like it
                // works — which is the behaviour we are trying to train.
                bool onTheWrongSide = s.rises ? dy < 0f : dy > 0f;
                if (onTheWrongSide) v *= Mathf.Exp(-Mathf.Abs(dy) * 0.55f);

                if (s.lateralFalloffM > 0.01f)
                {
                    float r = new Vector2(local.x, local.z).magnitude;
                    float sigmaH = s.lateralFalloffM;
                    v *= Mathf.Exp(-(r * r) / (2f * sigmaH * sigmaH));
                }

                return s.peakPercent * v;
            }
            return 0f;
        }

        /// <summary>Oxygen displacement. A worker who only watches the methane
        /// number misses that the atmosphere is also going oxygen-deficient,
        /// and beat 2.2 scores whether he read both.</summary>
        public float OxygenPercentAt(Vector3 worldPos)
        {
            const float ambient = 20.9f;
            float displaced = 0f;
            if (species != null)
                foreach (var s in species)
                    displaced += SampleAt(worldPos, s.id);
            return Mathf.Max(0f, ambient * (1f - displaced / 100f));
        }

        /// <summary>
        /// Optional visualisation. Intentionally faint and intentionally
        /// optional: the demo is stronger when the worker CANNOT see the gas
        /// and has to find it with the instrument, because that is the actual
        /// job. Turn the cloud on for the jury, off for training realism, and
        /// say so out loud when you show it.
        /// </summary>
        private void Update()
        {
            if (!showCloud || cloud == null || species == null || species.Length == 0) return;

            var main = cloud.main;
            var s = species[0];
            main.startColor = new ParticleSystem.MinMaxGradient(
                new Color(0.55f, 0.72f, 0.85f, 0.055f));   // barely there, on purpose
            cloud.transform.localPosition = new Vector3(0f, s.peakHeightM, 0f);

            var shape = cloud.shape;
            shape.scale = new Vector3(
                Mathf.Max(1f, s.lateralFalloffM * 2f),
                Mathf.Max(0.2f, s.falloffM * 1.5f),
                Mathf.Max(1f, s.lateralFalloffM * 2f));
        }

#if UNITY_EDITOR
        /// <summary>Scene-view slice so the author can see the layer while
        /// placing the leak, without shipping any of it.</summary>
        private void OnDrawGizmosSelected()
        {
            if (species == null || source == null) return;
            for (float y = -2f; y <= 3f; y += 0.25f)
            {
                Vector3 p = source.position + Vector3.up * y;
                float c = SampleAt(p, species[0].id);
                Gizmos.color = new Color(1f, 0.4f, 0.2f, Mathf.Clamp01(c / 5f));
                Gizmos.DrawCube(p, new Vector3(2f, 0.2f, 2f));
            }
        }
#endif
    }

    /// <summary>
    /// The instrument the worker actually reads. Deliberately laggy.
    ///
    /// A real four-gas detector has a diffusion-limited sensor with a T90 of
    /// several seconds — it does not track your hand instantly. Modelling that
    /// lag is not decoration: it teaches the worker to HOLD the probe at a
    /// sampling height and wait, which is the correct field technique, and it
    /// is the difference between a training tool and a magic wand.
    /// </summary>
    [RequireComponent(typeof(GasField))]
    public sealed class DetectorReadout : MonoBehaviour
    {
        [SerializeField] private GasField field;
        [SerializeField] private Transform probe;         // the device/camera

        public float Methane { get; private set; }
        public float Oxygen { get; private set; } = 20.9f;

        /// <summary>Seconds to reach 90% of a step change. Real sensors sit
        /// around 10-30 s; 4 s keeps the drill moving while still forcing the
        /// worker to hold still.</summary>
        [SerializeField] private float t90Seconds = 4f;

        /// <summary>True once the reading has settled enough that a decision
        /// based on it is fair. Beat 2.2 must not score a withdrawal call taken
        /// off a number that was still climbing.</summary>
        public bool Settled { get; private set; }

        private float _settleTimer;

        private void Update()
        {
            if (field == null || probe == null) return;

            float targetCh4 = field.SampleAt(probe.position, "ch4");
            float targetO2 = field.OxygenPercentAt(probe.position);

            float k = 1f - Mathf.Exp(-2.303f * Time.deltaTime / Mathf.Max(0.1f, t90Seconds));
            float beforeCh4 = Methane;
            Methane = Mathf.Lerp(Methane, targetCh4, k);
            Oxygen = Mathf.Lerp(Oxygen, targetO2, k);

            bool stable = Mathf.Abs(Methane - beforeCh4) < 0.004f;
            _settleTimer = stable ? _settleTimer + Time.deltaTime : 0f;
            Settled = _settleTimer > 0.8f;
        }
    }
}
