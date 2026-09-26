using UnityEngine;
using Sajag.Motion;

namespace Sajag.Effects
{
    /// <summary>
    /// Drives HazardAtmosphere.shader, and owns the one visual rule that keeps
    /// this app from looking like a game: THE HAZARD IS THE ONLY BRIGHT THING.
    ///
    /// Over a live camera feed you cannot control the environment's exposure,
    /// so you control contrast instead — push everything that is not the
    /// hazard down and warm, and the eye goes exactly where the drill needs it.
    /// That is the whole of the cinematic look here. There is no bloom, no
    /// depth of field and no volumetric light in this app, and there should
    /// not be: the camera feed already supplies real depth, real lighting and
    /// real scale, which is what those effects exist to manufacture.
    ///
    /// Attach to a quad parented to the AR camera, sized to fill the frustum.
    /// SetUp() does that sizing so nobody has to fiddle with it per device.
    /// </summary>
    [RequireComponent(typeof(MeshRenderer))]
    public sealed class CineGrade : MonoBehaviour
    {
        private static readonly int FillId     = Shader.PropertyToID("_Fill");
        private static readonly int BlackoutId = Shader.PropertyToID("_Blackout");
        private static readonly int EyeId      = Shader.PropertyToID("_EyeRadius");
        private static readonly int VignetteId = Shader.PropertyToID("_Vignette");
        private static readonly int GrainId    = Shader.PropertyToID("_Grain");
        private static readonly int BounceId   = Shader.PropertyToID("_BounceAmount");

        [SerializeField] private Camera arCamera;
        [SerializeField] private FireVFX fire;

        private MaterialPropertyBlock _mpb;
        private MeshRenderer _renderer;

        private float _fill, _blackout, _eye = 0.55f, _vignette = 0.22f, _grain = 0.35f;

        private void Awake()
        {
            _renderer = GetComponent<MeshRenderer>();
            _mpb = new MaterialPropertyBlock();
            if (arCamera == null) arCamera = Camera.main;
            FitToFrustum();
            Push();
        }

        /// <summary>Size the quad to exactly fill the near plane, so it works
        /// on any aspect ratio and any FOV without per-device tuning. Sitting
        /// at the near plane also means it never clips a hazard that the
        /// worker has walked right up to.</summary>
        private void FitToFrustum()
        {
            if (arCamera == null) return;
            float z = arCamera.nearClipPlane + 0.01f;
            float h = 2f * z * Mathf.Tan(arCamera.fieldOfView * 0.5f * Mathf.Deg2Rad);
            float w = h * arCamera.aspect;
            transform.localPosition = new Vector3(0f, 0f, z);
            transform.localRotation = Quaternion.identity;
            transform.localScale = new Vector3(w * 1.05f, h * 1.05f, 1f);  // slight bleed
        }

        // ------------------------------------------------------------ cues

        /// <summary>Choreographer cue "smoke-fall". Linear is correct here and
        /// is one of only two cues allowed to be — smoke descending is a
        /// genuinely constant process, and easing it would read as the app
        /// deciding when to scare you.</summary>
        public void SmokeFill(float t) { _fill = Mathf.Clamp01(t) * 0.72f; Push(); }

        /// <summary>Beat 1.4. The blackout is NOT total — see the shader note.
        /// A worker who is completely blind learns nothing; a worker at 4%
        /// visibility learns to keep a hand on the wall.</summary>
        public void Blackout(float t)
        {
            _blackout = Mathf.Clamp01(t);
            // The visible island shrinks as the smoke thickens, so the worker
            // feels the world closing in rather than a curtain dropping.
            _eye = Mathf.Lerp(0.75f, 0.14f, Ease.QuintOut(_blackout));
            Push();
        }

        /// <summary>Raised slightly during blackout: a phone sensor gets
        /// noisier as light drops, so the overlay should too. Getting this
        /// wrong is what makes a composite look pasted on.</summary>
        public void Grain(float t) { _grain = Mathf.Lerp(0.3f, 0.62f, Mathf.Clamp01(t)); Push(); }

        public void Vignette(float t) { _vignette = Mathf.Clamp01(t) * 0.4f; Push(); }

        /// <summary>Fires ambient-only, so it is allowed to keep running while
        /// the worker is deciding.</summary>
        private void Update()
        {
            if (fire == null) return;
            float bounce = fire.IsOut ? 0f : 0.55f;
            _mpb ??= new MaterialPropertyBlock();
            _renderer.GetPropertyBlock(_mpb);
            _mpb.SetFloat(BounceId, bounce);
            _renderer.SetPropertyBlock(_mpb);
        }

        private void Push()
        {
            if (_renderer == null) return;
            _mpb ??= new MaterialPropertyBlock();
            _renderer.GetPropertyBlock(_mpb);
            _mpb.SetFloat(FillId, _fill);
            _mpb.SetFloat(BlackoutId, _blackout);
            _mpb.SetFloat(EyeId, _eye);
            _mpb.SetFloat(VignetteId, _vignette);
            _mpb.SetFloat(GrainId, _grain);
            _renderer.SetPropertyBlock(_mpb);   // no material instance, no GC
        }

        /// <summary>
        /// Wire the standard cues. Call once when a beat loads.
        ///
        /// Note what is NOT bound: there is no "dramatic flash", no "impact
        /// shake", no "slow motion". Every one of those is available and every
        /// one would be wrong — this is a safety drill, and a worker who
        /// remembers the effects instead of the procedure has learned nothing.
        /// Restraint is the aesthetic.
        /// </summary>
        public void BindTo(Choreographer c)
        {
            c.Bind("smoke-fall",  SmokeFill);
            c.Bind("smoke-clear", t => SmokeFill(1f - t));
            c.Bind("blackout",    Blackout);
            c.Bind("grain-up",    Grain);
            c.Bind("vignette",    Vignette);
        }
    }
}
