# Five-minute demo

One phone, one presenter, one laptop. The drill is the centre of the demo: a
fire that stands on a real floor and stays there while you walk around it.

## Before the demo (once, about 15 minutes)

1. **Install.** `cd android && ./gradlew installWorkerDebug` with the demo phone
   plugged in. Do this the day before, not at the venue.
2. **AR support.** Install or update *Google Play Services for AR* from the Play
   Store. In Sajag, open **Settings** (gear, top right of Home). Under **Drills**
   it should say *AR works on this phone*. If the phone is not on Google's
   ARCore list, drills run in **Camera** mode instead. The demo still works;
   say so out loud rather than hiding it.
3. **Onboarding.** Open Sajag once:
   - pick **English** (or Hindi);
   - enter a name, an employer code such as `CTR-2291`, and **Dhanbad** as
     the district;
   - allow the camera.
4. **Demo mode.** Settings > **Demo mode**: on. The message says *Demo mode is on.
   Demo supervisor PIN: 1234*. While you are in Settings:
   - set **Control room number** to a teammate's phone (PIN 1234);
   - set **Assembly point** to *Main gate*.
5. **The spot.** A well-lit floor or table with some pattern on it: a rug,
   wood grain, tiles with grout. Plain glossy white defeats any AR. Do one full
   practice drill on that spot.
6. **Laptop.** Open `web/dist/index.html`, the compliance dashboard. It works
   with the network off.
7. **Sound.** Phone volume up. Every instruction is spoken.

Practice runs leave drills on the phone. That is fine: the training centre and
the risk map show them as *This phone* data.

## The five minutes

### 0:00 to 0:30: the problem, then Home

> "Workers in Jharkhand's mines, steel plants and mica units learn safety from
> a lecture and a signature. Sajag makes them practise the emergency on an
> ordinary Android phone, scores what they actually do, and gives them a
> certificate an inspector can check with no network."

Show the Home screen, top to bottom:

- the worker card (name, district, ID, modules certified);
- the red **Emergency** card;
- the training modules with their status;
- the supervisor tools.

### 0:30 to 2:45: a live drill in AR

1. Tap **Fire & Explosion Response**. The briefing says *This drill: AR*.
2. Tick **I am in a clear, safe area**. The supervisor types **1234** and taps
   **Sign off**. It shows *Signed off by Demo Supervisor*. Tap **Start the
   drill**.
3. Point the phone at the floor. When it says *Tap the floor or table to place
   the drill there*, tap. The burning switchgear panel appears on the floor.
   **Walk half a circle around it: it stays where it is.**

   > "This is real AR: ARCore tracks the floor and the fire is anchored to it.
   > The chip at the top says AR with a live dot. Every answer records which
   > mode was really on screen, and the certificate says so."

4. Answer quickly and correctly. Timing starts when each question appears, not
   while the instruction is being read, so tap **I'm ready, begin** as soon as
   you have introduced each step.

| Step | Answer |
|---|---|
| 1. Read the fire | **Electrical fire — live panel**. Distance: slide to **3 m**, **Confirm** |
| 2. Pick the extinguisher | **Check the gauge**, then **CO₂ extinguisher** |
| 3. Pull, Aim, Squeeze, Sweep | Tap in order: **Pull, Aim, Squeeze, Sweep**. Aim: slide to **10°**. Drag your finger across the **whole** bar. Distance: **2.5 m** |
| 4. Escape through smoke | Head height: slide to **0.8 m**. Drag along the **whole** wall. **Feel it with the back of my hand first**. **North exit** |
| 5. Sequence the response | **Raise the alarm, Trip the conveyor, Alert your buddy, Go to the assembly point, Report for headcount** |
| Six questions, one at a time | **CO₂**. **Stay low and keep a hand on the wall**. **Feel it with the back of your hand**. **At the base of the fire**. **Raise the alarm**. **Report at the assembly point for headcount** |

> While you answer, say: "No ticks, no hints, no colours while the worker
> answers. If I had picked water here, the drill would stop with a STOP card,
> because on a live panel that kills, and he would have to repeat it."

Do not pick water in the live demo: a STOP means the attempt cannot pass.
Show the STOP card in a rehearsal, or in a second drill if you have time left.

### 2:45 to 3:20: result, certificate, verification

1. The result shows the score, **Passed**, the skill bars and *Supervisor signed
   off on this attempt*. Tap **Get my certificate**.
2. The certificate shows the QR, *Signature verified offline*, the mode chip
   **AR**, and why it is still provisional: *Becomes final when the training
   centre confirms it.*
3. Verify it, either way:
   - **With a second phone:** open Sajag's **Verify** tab, tap **Scan QR code**,
     point it at the certificate. It shows a large **VALID** with a sound.
   - **With the laptop:** on the dashboard's verify tab, click **Load a valid
     one**, then **Tampered**. One changed character and it is **REJECTED**.

> "148 bytes, signed with Ed25519. It checks out in a dark gallery with no
> signal, on the inspector's phone or on this laptop."

### 3:20 to 3:50: emergency and a hazard report

1. Back to **Home**, tap the red **Emergency** card. Point out:
   - *This app is not an alarm*;
   - **Call control room** (the number the supervisor set) and **Call 112**;
   - the **torch**;
   - the steps for fire, gas and a collapsed worker, with **Read aloud**.
2. Tap **Report this hazard**. The form opens with the hazard type filled in.
   Choose **High**, tap **Send report**, and **Send report** again to confirm.
   It says the report is saved and will be sent when there is network.

### 3:50 to 4:30: what the supervisor sees

1. Home > **Risk map**. On **This phone**, Dhanbad is coloured by the report you
   just made and the drills taken on this phone (a high report alone is 4
   points: medium). Tap **Dhanbad** to see why. Switch to **Sample**: the whole
   state, clearly marked *SAMPLE DATA*. Scroll to **How the colour is worked
   out**: the rule is printed on the screen.
2. Back, then **Training centre**. **This phone** shows your drill, signed off.
   **Sample** shows what a centre sees after two months:
   - pass rate and supervisor sign-offs;
   - the steps most often missed, to teach again;
   - the STOP actions.

### 4:30 to 5:00: the passport, and close

Tap the **Passport** tab: the worker ID card with its QR, each module's status,
skill scores, and what to practise next.

> "It runs offline on an ordinary Android 10 phone. It uses real AR where the
> phone supports it, the camera where it does not, and a drawn gallery with no
> camera at all. The certificate always records which one was used."

## If something goes wrong

| What you see | What to do |
|---|---|
| *Too dark to track* | Add light: room lights, or a second phone's torch. |
| *Point at a surface with some pattern* | Move to the rug or table you rehearsed on. |
| No surface after 20 seconds | Tap **Use the camera view instead**. The drill carries on in Camera mode, and the certificate records it. |
| Nobody tapped | After 7 seconds on a found surface, the drill places itself where the phone points. Tap elsewhere to move it. |
| *AR could not start on this phone* | Nothing. The drill has already switched to the camera view. |
| Camera permission denied | Guided mode: a drawn mine gallery. Tap the banner at the top to allow the camera. |
| Wrong PIN | The demo supervisor's PIN is 1234. It is shown under the PIN field in demo mode. |
| No second phone | Use the dashboard's verifier on the laptop. |
