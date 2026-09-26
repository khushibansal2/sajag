// Sajag — the atmosphere pass.
//
// One transparent quad, parented to the camera, covering the frustum. It does
// four jobs that would otherwise be four separate post-process passes:
//
//   1. smoke fill        — the descending layer in beat 1.4
//   2. zero visibility   — the blackout the worker navigates by touch
//   3. grain             — the composite trick, see below
//   4. vignette          — draws the eye to the hazard, costs nothing
//
// WHY A QUAD AND NOT A RENDERER FEATURE
// A ScriptableRendererFeature blit reads and writes a full-screen buffer. On
// a Helio G85 at 720p that bandwidth is roughly 1.5 ms before any maths. An
// overlay quad with ZTest Always is two triangles and one pass of cheap ALU,
// and it survives URP version churn between now and December, which a custom
// renderer feature reliably does not.
//
// WHY GRAIN MATTERS MORE THAN IT LOOKS
// The hardest thing about compositing CG over a live camera feed is that the
// CG is too clean. A phone sensor in a dim gallery is noisy; perfectly smooth
// virtual smoke sitting on top of grainy camera footage reads instantly as a
// sticker. Adding matched grain OVER BOTH unifies them. It is the single
// highest-impact line in this file and it costs one hash per pixel.
//
// BUDGET: ~0.5 ms at 720p on the reference device. Set _Grain to 0 to drop it
// to ~0.35 ms if a beat needs the headroom.

Shader "Sajag/HazardAtmosphere"
{
    Properties
    {
        _SmokeColor   ("Smoke Colour", Color) = (0.17, 0.16, 0.15, 1)
        _Fill         ("Smoke Fill", Range(0,1)) = 0
        _Blackout     ("Blackout", Range(0,1)) = 0
        _EyeRadius    ("Visible Radius", Range(0.02,1.5)) = 0.55
        _Vignette     ("Vignette", Range(0,1)) = 0.25
        _Grain        ("Grain", Range(0,1)) = 0.35
        _WarmBounce   ("Firelight Bounce", Color) = (0.95, 0.44, 0.13, 1)
        _BounceAmount ("Firelight Bounce Amount", Range(0,1)) = 0
    }

    SubShader
    {
        Tags
        {
            "RenderType"     = "Transparent"
            "Queue"          = "Overlay"
            "RenderPipeline" = "UniversalPipeline"
            "IgnoreProjector"= "True"
        }

        Pass
        {
            Name "Atmosphere"
            Blend SrcAlpha OneMinusSrcAlpha
            ZWrite Off
            ZTest Always
            Cull Off

            HLSLPROGRAM
            #pragma vertex vert
            #pragma fragment frag
            #pragma target 3.0

            #include "Packages/com.unity.render-pipelines.universal/ShaderLibrary/Core.hlsl"

            CBUFFER_START(UnityPerMaterial)
                float4 _SmokeColor;
                float4 _WarmBounce;
                float  _Fill;
                float  _Blackout;
                float  _EyeRadius;
                float  _Vignette;
                float  _Grain;
                float  _BounceAmount;
            CBUFFER_END

            struct Attributes
            {
                float4 positionOS : POSITION;
                float2 uv         : TEXCOORD0;
            };

            struct Varyings
            {
                float4 positionCS : SV_POSITION;
                float2 uv         : TEXCOORD0;
            };

            Varyings vert(Attributes IN)
            {
                Varyings OUT;
                OUT.positionCS = TransformObjectToHClip(IN.positionOS.xyz);
                OUT.uv = IN.uv;
                return OUT;
            }

            // Cheap hash. Not a good RNG — it does not need to be, it needs to
            // be uncorrelated frame to frame and one instruction wide.
            float hash21(float2 p)
            {
                p = frac(p * float2(123.34, 456.21));
                p += dot(p, p + 45.32);
                return frac(p.x * p.y);
            }

            half4 frag(Varyings IN) : SV_Target
            {
                float2 uv = IN.uv;
                float2 c  = uv - 0.5;
                float  r  = length(c) * 2.0;          // 0 centre, ~1.41 corner

                // --- smoke: denser toward the top of frame, because it layers
                // downward from the roof. A uniform fill reads as a filter;
                // a gradient reads as a volume the worker is standing under.
                float vertical = saturate(uv.y * 1.25 + 0.15);
                float smoke = _Fill * vertical;

                // --- blackout with a soft island of visibility around centre.
                // Not fully opaque even at 1.0: leaving ~4% through is what
                // lets a worker keep his bearings by wall contact instead of
                // simply being blind, which is the technique beat 1.4 teaches.
                float island = smoothstep(_EyeRadius, _EyeRadius * 0.25, r);
                float dark = _Blackout * (1.0 - island * 0.55);
                dark = min(dark, 0.96);

                // --- vignette, always on, always subtle.
                float vig = _Vignette * smoothstep(0.45, 1.25, r);

                float alpha = saturate(smoke + dark + vig);

                half3 col = _SmokeColor.rgb;

                // --- firelight bouncing into the smoke. Tints the lower half
                // warm when a fire is burning, which is what actually happens
                // and what makes the overlay feel lit rather than painted.
                float bounce = _BounceAmount * saturate(1.0 - uv.y * 1.4);
                col = lerp(col, _WarmBounce.rgb, bounce * 0.55);

                // --- grain over everything: the composite trick.
                float g = hash21(uv * _ScreenParams.xy * 0.5 + frac(_Time.y) * 91.7);
                col += (g - 0.5) * _Grain * 0.14;
                alpha += (g - 0.5) * _Grain * 0.05;

                return half4(saturate(col), saturate(alpha));
            }
            ENDHLSL
        }
    }
    FallBack Off
}
