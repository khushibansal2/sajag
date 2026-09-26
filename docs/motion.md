# The motion language

The reference the team gave was Fern — the cinematic 3D-documentary look. This
document is the honest translation of that reference onto a phone that is
already running ARCore.

## The inversion that makes it possible

Fern renders offline, with volumetric light, real depth of field and long
render times per frame. You are targeting 30 fps on a Snapdragon 680 / Helio
G85 — a **33 ms frame**, shared with ARCore's tracking and camera passthrough,
and it has to hold for twelve minutes without thermal throttling. You cannot
render that, and any plan that assumes you can will die in November.

But the reference is chasing something the camera feed gives you for free.
Volumetrics, DOF and careful three-point lighting exist to manufacture *depth,
scale and believable light*. Point a phone at a real conveyor gallery and you
have all three, correct, for zero milliseconds.

So the inversion is: **do not render a world. Render three objects into a real
one, and spend the entire budget on those three.**

## What actually carries the feel

Most of the impact of that style is timing, not rendering. Three things do the
work, and all three are free:

**Heavy ease-out.** Things start fast and settle slowly over a long tail.
Linear motion reads as a game; `ExpoOut` and `QuintOut` read as a camera move.
Nothing in this app is Linear except a genuinely constant process — smoke
descending, a gas reading drifting — and `Choreographer.Validate()` warns at
load if you make something else linear.

**Staggered entrances.** Four extinguishers appearing together is a menu. The
same four at 90 ms apart is a reveal, and the eye reads them in the order you
chose. One constant, `Ease.Stagger`.

**Held beats.** A pause before the important thing is the strongest tool here
and it costs a float. See the 0.6 s gap before `hand-over` in every staging
block.

## The one visual rule

**The hazard is the only bright thing.** Over a live camera feed you cannot
control the environment's exposure, so you control contrast instead: push
everything that is not the hazard down and warm, and the eye goes exactly where
the drill needs it. That is the whole of the look. `CineGrade` owns it.

Corollary: fire is a **light** first and a sprite second. A flickering warm
light thrown onto the worker's actual wall is what sells a fire that is not
there. Sprites alone always read as a sticker on the camera feed.

## Grain, which matters more than it looks

The hardest part of compositing CG over a live camera feed is that the CG is
too clean. A phone sensor in a dim gallery is noisy; perfectly smooth virtual
smoke sitting on grainy footage reads instantly as pasted on. Matched grain
applied **over both** unifies them. One hash per pixel, and it is the highest
impact-per-instruction line in the whole renderer.

Grain also rises as the blackout deepens, because a real sensor gets noisier as
light drops. Getting that relationship backwards is a tell.

## Frame budget

**Read this caveat first.** None of these figures have been measured on a real
Helio G85 — there is no such device in the loop that produced this repo. What
is reliable here is the **ordering and the relative cost**, which follows from
how mobile GPUs work and does not depend on the exact chip. The absolute
milliseconds are planning estimates and nothing more. Profile them in the
October vertical slice with the Unity Profiler and Android GPU Inspector, and
replace this table with real numbers before anyone puts it on a slide.

Planning rule: 30 fps is a 33 ms frame, shared with ARCore. Target your own
rendering at **under a third of it**, because sustained twelve-minute sessions
throttle long before the peak budget does.

| Element | Relative cost | Notes |
|---|---|---|
| 1 realtime point light (fire) | **most expensive** | per-pixel additional light; worth it, see below |
| ~120 particles, 1 draw call | moderate | hard ceiling at 130 in `FireVFX` |
| Atmosphere quad | low | ALU-bound, 2 triangles; grain is most of it |
| Heat shimmer quad | low | **first thing to cut** if a beat is tight |
| Gas field sampling | negligible | a handful of `exp()` on the CPU |

**Do not add:** a second realtime light, soft particles (needs a depth buffer
you are not writing), bloom (full-screen and the single most expensive thing
you could reach for), or a `ScriptableRendererFeature` blit — a full-screen
read-modify-write is bandwidth, and bandwidth is the scarcest thing on a
mid-range mobile GPU. That is why the atmosphere is an overlay quad.

## Staging lives in the scenario file

The unusual idea, stated plainly: **one file says both what the worker must do
and how the scene reveals it.** A `staging` block sits next to `rubric` in
`fire-01.json`. Authoring a new safety domain is one file — not a file plus a
scene plus an Animator controller plus a timeline asset nobody can merge.

```json
"staging": [
  { "at": 0.0, "cue": "smoke-fall", "for": 2.4, "ease": "linear"  },
  { "at": 1.2, "cue": "grain-up",   "for": 1.8, "ease": "quintOut"},
  { "at": 1.8, "cue": "blackout",   "for": 2.6, "ease": "expoIn"  },
  { "at": 2.4, "cue": "hand-over" }
]
```

Three rules the choreographer enforces rather than leaving to taste:

1. **One thing at a time.** Two attention-seeking cues may not overlap. Ambient
   cues (light, smoke, grain, vignette) are exempt. A worker who misses the cue
   that matters is not a polish problem in this domain.
2. **Nothing moves while the worker is deciding.** After `hand-over`, ambient
   continues and everything else is frozen. Motion during a decision biases the
   decision, and we are scoring that decision.
3. **The clock is identical in every mode.** Guided mode has no 3D scene but
   runs the same cue list against 2D plates, so pacing matches. That is what
   makes the per-mode timing rubric defensible rather than arbitrary.

`hand-over` also starts the worker's clock. Timing rubrics measure from there,
never from beat start — otherwise our reveal pacing would be scored as his
hesitation.

## Why tweens and not the Animator

The rubric needs to know *when* the door finished opening, how long the nozzle
stayed on the base, how long the smoke had been falling before he moved. An
Animator state machine knows all of that and tells you none of it without extra
plumbing. `Motion.Tween` owns its own clock and hands the number straight to
`TelemetryEmitter`.

Animator controllers are also binary assets that six people cannot merge during
a 36-hour hackathon. Tweens are text.

## What we deliberately do not do

No dramatic flash. No impact shake. No slow motion. No bloom. Every one is
available and every one would be wrong: this is a safety drill, and a worker who
remembers the effects instead of the procedure has learned nothing.

Restraint is the aesthetic. It also happens to be what fits in the frame.

## Gotchas that will each cost you a day

These are not style notes. Each one produces a silent failure — the thing
simply does not appear, with no error — which is the worst kind at 2 a.m.

**The fire light will do nothing until you change a project setting.** URP
defaults can have Additional Lights set to *Per Vertex*, or disabled. Open your
URP Asset > Lighting > Additional Lights > **Per Pixel**, and raise
*Per Object Limit* to at least 2. Without this, `FireVFX` runs, the particles
render, and the light that does most of the work is simply absent.

**`MaterialPropertyBlock` opts that renderer out of the SRP Batcher.** That is
a deliberate trade in `CineGrade`: it is one quad, and a property block avoids
instantiating a material (which would leak one per scene load). Do NOT copy the
pattern onto the props — for anything you draw more than once, use material
variants and keep the batcher.

**Android 10 has no platform Ed25519.** The JDK has had it since 15, which is
why `CodecVectorTest.java` needs no dependency — but `minSdk 29` does not.
Conscrypt only exposes Ed25519 on much newer releases. `CredentialCodec.kt`
therefore imports BouncyCastle, and that is not an oversight; dropping it to
"use the platform" will compile and then fail on exactly the cheap phones this
whole product exists for.

**ParticleSystem modules are structs.** `ps.main.startColor = x` does not
compile; `var m = ps.main; m.startColor = x;` does, and writes through. Every
module in `FireVFX` follows that pattern — keep it.

**`Choreographer` warns, it does not throw.** A missing cue handler logs and
continues, because a missing visual must never stall a drill mid-beat. Read the
console on first run of any new scenario; the warnings are the review.

## What still needs a human

One rigged humanoid, for the collapse in beat 2.5. Everything else in both
modules is objects, particles and shader parameters — code by nature.

Three ways to close it: ragdoll physics on a free Mixamo rig (code, ten
minutes, looks better than hand-keyed); buy a rigged miner; or design it out —
the buddy is a cap lamp on the floor and a radio going silent, which is cheaper
and arguably more unsettling in exactly the way that beat wants.

Santali and Hindi voice lines also need a human. That is not an animation
problem, and it is not automatable — see the README.
