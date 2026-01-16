# Lenovo Pen Plus Debug

**An investigatory tool for detecting and displaying Lenovo proprietary stylus button events on Lenovo Idea Tab Pro**

---

## What?

I first tried to detect the opening and closing of the pen toolbox that Lenovo's OS offers (not a very good method) using accessibility (which did not work lol). Then I decided to check `adb logcat` output for any stylus related event. I did not find it being related to ~~stylii~~ styluses, but I did find **Bluetooth** logs. This led me to the finding that the button on the pen is not a HID button, but rather a Bluetooth button (like the buttons found for control in audio devices).

I first used **Shizuku** (ADB-level access) to monitor logcat output from the Android framework's `BluetoothPenInputPolicy` class, then found **KeyEvents** being fired in the same logcat output, so later added a detection for the firing of those events and read the gesture type by the `keyCode` (which was mapped by experimentation).

## Detection Methods

### Direct observation via logcat parsing  
- Monitors `BluetoothPenInputPolicy` framework logs
- Detects keycodes: 600, 601, 602, 603, 604
- Parses `clickStatusType`: 0=Single, 1=Double, 2=Triple, 3=Long Press, 4=Long Press + Click

Shizuku Logcat Parsing:

**Log Format Parsed:**
```
12-25 10:30:45.123 D/BluetoothPenInputPolicy( 1234): processStylusPenKeyEvent: keycode 602,mIsStylusPenRemoteControl false,clickStatusType 2
```

**Extracted Data:**
- `keycode`: 600/601/602/603/604 (vendor-specific)
- `clickStatusType`: 0=Single, 1=Double, 2=Triple, 3=Long Press, 4=Long Press + Click

No root required to read these logs.

### KeyEvent based detection

The app hooks Lenovo’s vendor keycodes in `MainActivity.onKeyUp()` and treats `keyCode`s 600..604 as “pen button events”. It checks if (keyCode in 600..604), maps each code to a label, logs it, then calls viewModel.addKeyEventDetection(keyCode) and returns true to consume the event.

Only code 601 produces both ACTION_DOWN and ACTION_UP, while 600/602/603/604 are ACTION_UP only. Detecting in both onKeyDown and onKeyUp would double-count 601, so I removed onKeyDown and detect only on key-up.

This works only when the activity is in the foreground. A class: [`dev.ilamparithi.lppdebug.LenovoPenButtonListener`](app/src/main/java/dev/ilamparithi/lppdebug/helper/LenovoPenButtonListener.kt) is included to serve as an example for usage in other apps. It does not have any dependencies. I created a [patch for termux-x11](termux-x11-lenovo-pen-button.patch) for my personal use. Please feel free to modify and use the patch as required, to add this detection in termux-x11.

*** 

## What the app shows

- **Last Detected Event**: Large display showing the most recent button press
- **Click Type**: Single/Double/Triple/Long Press/Long Press + Click
- **Timestamp**: Precise time of event
- **Event History**: Scrollable list of past 100 events
- **Keycode**: Vendor-specific keycode (600-604)

## Limitations

### This app cannot:
- Intercept the `lenovo.intent.action.INPUT_DEVICE_CLICK_STATE_CHANGED` broadcast (protected by system)
- Prevent default Lenovo toolbox behavior (you can still disable it in settings)
- Inject custom actions on button press
- Access vendor-specific framework APIs

### Why These Limitations Exist:
1. **Protected Broadcast**: The `lenovo.intent.action.INPUT_DEVICE_CLICK_STATE_CHANGED` intent is part of the `protected-broadcasts`, making it impossible for third-party apps to receive
2. **Framework-Level Processing**: Events are handled at the Android framework level before reaching app layer

**It cannot modify stylus behavior.**

### Tested with
- Lenovo Idea Tab Pro + Lenovo Pen Plus stylus

---

## Setup Instructions

### Step 1: Install and start [Shizuku](https://github.com/RikkaApps/Shizuku) (optional)

1. Download and install Shizuku from Google Play or GitHub
2. Open Shizuku app
3. Follow the setup wizard to start Shizuku service

### Step 3: Install this app

1. Build and install the APK (or grab a build from releases):
   ```bash
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

2. Open "LenovoPenPlusDebug" app

### Step 4: Grant Permissions (optional)

1. The app will show "Permission Required" status
2. Tap "Grant Shizuku Permission"
3. Shizuku will show a permission dialog
4. Tap "Allow"

### Step 5: Start Monitoring

1. Tap "Start Monitoring" button
2. Status indicator will turn green: "✅ Monitoring Active"
3. Press any button on your Lenovo Pen Plus
4. Check if the event gets logged on screen

---
