# BuzzOff

An alarm clock for people who set lots of alarms. Group alarms into
**categories** (Morning, Nap, Gym…). When you're up, tap **I'm up**, enter your
PIN, and the rest of today's alarms in that category are silenced at once.

## Features

- **Categories** – "I'm up" (from the home screen or the ringing screen)
  silences only the category that rang. Repeating alarms return tomorrow;
  *Turn back on* undoes it.
- **Alarm editor** – set the time on the **clock dial** or **type it** with the
  keyboard; repeat Once / Every day / Weekdays / Weekends / Custom days;
  label, sound, vibration.
- **Backup alarms** – when creating an alarm, add up to 10 more after it
  (every 2/5/10/15 min).
- **Delete** – swipe an alarm left, or use the bin icon in the editor. Both
  offer Undo.
- Next-alarm countdown, snooze banner with Cancel, gentle volume ramp,
  configurable snooze and auto-stop, a *Ring a test alarm* button.
- Alarms survive reboots, time changes and app updates.

## Build & install

Open this folder in Android Studio and press ▶ Run with the phone connected
(USB debugging on), or:

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## OnePlus setup

1. Open BuzzOff and tap **Fix** on every card at the top.
2. Settings › Apps › BuzzOff › Battery → **Unrestricted** / *Allow background activity*.
3. Settings › *Ring a test alarm now*, then try a real alarm 2 minutes out with the screen off.
