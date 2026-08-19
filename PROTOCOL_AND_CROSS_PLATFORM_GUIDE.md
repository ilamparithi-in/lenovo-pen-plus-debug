# Lenovo Pen Plus Protocol & Cross-Platform Implementation Guide

This document details the underlying Bluetooth Low Energy (BLE) / GATT protocol used by the **Lenovo Tab Pen Plus** (`AP500U` / `AP501U`), along with practical methods to detect button presses and generate OS-wide native input events on **Linux**, **Windows**, and **non-ZUI Android**.

---

## 1. Protocol Reverse Engineering & Discovery

While the stylus coordinate/pressure digitizer operates over electro-static protocols (LPP 2.0 / USI 2.0) with the screen, the physical button on the stylus body communicates over **Bluetooth Low Energy (BLE)** via standard GATT services.

### GATT Characteristic Details
* **GATT Service**: Human Interface Device (HID) Service (`00001812-0000-1000-8000-00805f9b34fb`)
* **Report Characteristic**: `00002a4d-0000-1000-8000-00805f9b34fb` (BlueZ path: `.../service0022/char0029`)
* **Report Payload**: 2-byte structure: `[ Report ID (0x02), Gesture Bitmask ]`

### Complete Payload & Event Mapping Matrix

| Gesture | Raw Bytes (Hex) | Byte Array | Bit | ZUI `clickStatusType` | ZUI Framework `KeyEvent` |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Single Press** | `02 01` | `[2, 1]` | `1 << 0` | `0` | **`600`** |
| **Double Press** | `02 02` | `[2, 2]` | `1 << 1` | `1` | **`601`** |
| **Triple Press** | `02 04` | `[2, 4]` | `1 << 2` | `2` | **`602`** |
| **Long Press** | `02 08` | `[2, 8]` | `1 << 3` | `3` | **`603`** |
| **Long Press + Click** | `02 10` | `[2, 16]` | `1 << 4` | `4` | **`604`** |

---

## 2. Why Other OSes Don't Get Native KeyEvents Automatically

1. **Vendor Custom HID Report**: While the pen pairs as a standard HID device, it transmits button events under Report ID `0x02` using proprietary bit flags rather than standard Consumer Control (Play/Pause, Volume) or standard Keyboard scan codes.
2. **Missing Kernel/Driver Mapping**: Default OS drivers (Windows HID stack, Linux `hid-generic` / `uhid`) do not parse Report ID `0x02` into standard scan codes.
3. **ZUI Framework Simulation**: On Lenovo tablets, a background service (`BluetoothPenInputPolicy`) monitors this characteristic notification and injects synthetic Android `KeyEvent`s (codes 600–604).

---

## 3. Implementing OS-Wide Events on Linux

### A. Real-Time Monitor
To inspect button presses live in the terminal:
```bash
python3 lenovo_pen_monitor.py
```

### B. Linux System-Wide Input Daemon (`uinput`)
The included [`lenovo_pen_uinput_daemon.py`](lenovo_pen_uinput_daemon.py) creates a virtual keyboard device using Linux `/dev/uinput` and translates GATT notifications directly into native Linux keycodes:

- Single Press $\rightarrow$ `KEY_F13`
- Double Press $\rightarrow$ `KEY_F14`
- Triple Press $\rightarrow$ `KEY_F15`
- Long Press $\rightarrow$ `KEY_F16`
- Long Press + Click $\rightarrow$ `KEY_F17`

You can run this as a `systemd` user service:
```ini
[Unit]
Description=Lenovo Tab Pen Plus Event Daemon
After=bluetooth.target

[Service]
ExecStart=/usr/bin/python3 /path/to/lenovo_pen_uinput_daemon.py
Restart=always

[Install]
WantedBy=default.target
```

---

## 4. Implementing OS-Wide Events on Windows

On Windows, you can implement a background service using:

### Option 1: WinRT / C# Background Tray App (`Windows.Devices.Bluetooth.GenericAttributeProfile`)
1. Connect to the paired device via `BluetoothLEDevice.FromIdAsync(deviceId)`.
2. Get the HID Service: `GetGattServicesForUuidAsync(GattServiceUuids.HumanInterfaceDevice)`.
3. Locate characteristic `0x2A4D` and subscribe to `ValueChanged`:
   ```csharp
   characteristic.ValueChanged += (sender, args) => {
       var reader = DataReader.FromBuffer(args.CharacteristicValue);
       byte[] data = new byte[reader.UnconsumedBufferLength];
       reader.ReadBytes(data);
       if (data.Length >= 2 && data[0] == 0x02) {
           byte bitmask = data[1];
           // SendInput (P/Invoke) to synthesize keystrokes, F13-F24, or Media keys
           SynthesizeKeyPress(bitmask);
       }
   };
   await characteristic.WriteClientCharacteristicConfigurationDescriptorAsync(
       GattClientCharacteristicConfigurationDescriptorValue.Notify);
   ```
4. Use `SendInput()` Win32 API to dispatch keys system-wide.

### Option 2: Windows Virtual HID Driver (VHID / Virtual Pen)
* Use Windows Driver Framework (KMDF / UMDF) to register a Virtual Digitizer / Pen Button HID driver or hook into Windows Ink Workspace / RadialController APIs.

---

## 5. Implementing on Non-ZUI Android Devices

In standard Android apps without Lenovo framework binaries:
1. Connect using `BluetoothDevice.connectGatt(context, false, gattCallback)`.
2. In `onServicesDiscovered`, find characteristic `00002a4d-0000-1000-8000-00805f9b34fb`.
3. Enable notifications on the characteristic and write `ENABLE_NOTIFICATION_VALUE` to its `0x2902` descriptor.
4. Handle the byte stream in `onCharacteristicChanged(gatt, characteristic, value)`:
   - Check `value[0] == 2` and parse `value[1]`.
