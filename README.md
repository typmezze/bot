# Screw Out AutoBot v0.6 – experimental

Android/Kotlin project. Android 11+; uses AccessibilityService screenshot/gestures (requires manual permission). Build debug APK with Gradle (`assembleDebug`) or the supplied GitHub Actions workflow. No root required.

## Changes
- Calibrates the **two visible active bin colors** from the upper rim of each bin on every frame (positions taken from supplied 709x1536 screenshot).
- Recognizes candidate colors by hue instead of assuming red/blue. Light and dark variations of the same hue are tolerated; cyan and blue can remain distinct.
- Reduces per-tap wait from 550ms to 350ms, rotation delay from 700ms to 500ms.
- Stops if bin color calibration fails. Never deliberately taps the +100 ad reward button.

## Important limitations
- This is a heuristic experiment, **not** a reliable autonomous level solver. It may select a colored decorative element rather than a screw. The current classifier does not model actual bin capacity, occlusion, screws behind parts, keys, or loss conditions.
- Fixed reference geometry assumes the same portrait layout as the supplied screenshots. Different screens, UI scaling, and level layouts may require recalibration.
- Color calibration uses a small patch near the rim and may be fooled by shadows, animation, or interface overlays. It is not learned persistently.
- Tested by static source review only, not compiled or run on device here. Stop it if it behaves incorrectly.
- Enable Accessibility Service in Android Settings. Accessibility automation may be restricted by game terms or platform policy.

## GitHub Actions
Commit the project to the repository root. In GitHub > Actions, select `Build Android APK` and `Run workflow`; download the build artifact from the completed run.

## v0.7 experimental move planner
- `MovePlanner.kt` scores currently visible, bin-color-matching candidates.
- Penalizes crowded screw candidates and remembers failed tap locations.
- Estimates bin occupancy from confirmed removals and resets it on a detected color change.
- Does **not** know true 3D occlusion, actual bin occupancy, screw layer ordering or full puzzle rules. A visually disappearing screw can be hidden rather than removed. This is not a guaranteed solver.
- On-screen stop control and scan/move safety limits remain in place.
- Not compiled or tested on a physical device in this delivery.


## v0.8 – calibration hotfix
- Overlay moved down to avoid hiding both bin rims (reference portrait layout).
- Samples six locations around each active bin rim and uses consensus hue clustering.
- Retries calibration up to 15 frames instead of stopping immediately.
- Reference screenshot: 709x1536; other resolutions/aspect ratios require calibration.
- The APK uploaded by the user was not reverse engineered. This patch builds on v0.7 source.
- Experimental, not compiled or tested on an Android device.


## v0.9 – flyttbar panel
- Dra i **☰ FLYTTA** för att flytta panelen på skärmen.
- **−** minimerar panelen till en draglist och **+** expanderar den igen.
- **AVSLUTA** stoppar automationen, tar bort panelen och stänger av appens tillgänglighetstjänst (`disableSelf`). Det stänger inte själva spelet. För att starta boten igen, aktivera tillgänglighetstjänsten i Android-inställningarna.
- **STOPP** pausar botens aktivitet utan att stänga av tillgänglighetstjänsten.
- Inga ändringar behövs i GitHub Actions-workflow.


## v1.0 – closed-loop tuning (experimental)
- Target candidates must appear in two consecutive observations before a tap is considered.
- Adaptive delay after gestures: speeds up after a confirmed disappearance (minimum 180 ms), slows down after apparent failure (maximum 800 ms).
- Displays successful/failed attempt counters in the floating panel.
- Calibration retries up to 30 frames. Slightly more tolerant of dark/light saturated rim colors.
- Fixes stale artifact name in GitHub Actions (`ScrewOutAutoBot-v10-APK`).
- **Important:** This is not trained machine learning, and disappearance does not prove that a screw was correctly collected. It does not solve true 3D occlusion, determine all screw shapes, or guarantee level completion. No local Android SDK/Gradle build was available for compilation testing.

## v1.1 – 2-axis camera sweep
- Cycles 12 camera gestures: left/right, up/down, diagonals, and wider sweeps.
- Screenshots are analyzed after each gesture. A confirmed successful removal resets the search cycle.
- Keeps gestures in the central puzzle area to reduce accidental touches on UI.
- NOTE: Exact 3D response to swipes is game-dependent; test vertical directions on your device.
- Does not guarantee that all angles are reachable or that every level can be solved.
- Not compiled or device-tested in this environment.
