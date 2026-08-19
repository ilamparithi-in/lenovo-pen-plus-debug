#!/usr/bin/env python3
"""
Lenovo Tab Pen Plus - Linux uinput Virtual Keyboard / Action Daemon
Maps Bluetooth GATT button gestures to system-wide Linux keypresses / shortcuts.
Requires: python-dbus, python-gobject, python-evdev (or uinput)
"""
import sys
import os
import datetime
import dbus
from dbus.mainloop.glib import DBusGMainLoop
from gi.repository import GLib

try:
    import evdev
    from evdev import UInput, ecodes as e
    HAS_EVDEV = True
except ImportError:
    HAS_EVDEV = False

# Default Linux Key mappings for gestures
# You can change these to any keycodes you like (e.g. e.KEY_F13, e.KEY_PLAYPAUSE, etc.)
KEY_MAPPINGS = {}
if HAS_EVDEV:
    KEY_MAPPINGS = {
        (2, 1): [e.KEY_F13],            # Single Press -> F13
        (2, 2): [e.KEY_F14],            # Double Press -> F14
        (2, 4): [e.KEY_F15],            # Triple Press -> F15
        (2, 8): [e.KEY_F16],            # Long Press -> F16
        (2, 16): [e.KEY_F17],           # Long Press + Click -> F17
    }

GESTURE_NAMES = {
    (2, 1): "Single Press",
    (2, 2): "Double Press",
    (2, 4): "Triple Press",
    (2, 8): "Long Press",
    (2, 16): "Long Press + Click",
}

def create_uinput_device():
    if not HAS_EVDEV:
        return None
    try:
        cap = {
            e.EV_KEY: [e.KEY_F13, e.KEY_F14, e.KEY_F15, e.KEY_F16, e.KEY_F17,
                       e.KEY_PLAYPAUSE, e.KEY_NEXTSONG, e.KEY_PREVSONG]
        }
        return UInput(cap, name="Lenovo Tab Pen Plus Virtual Buttons")
    except PermissionError:
        print("⚠️ Permission denied for /dev/uinput. Run with sudo or add user to 'input' group to emit virtual keys.")
        return None

def main():
    DBusGMainLoop(set_as_default=True)
    bus = dbus.SystemBus()
    manager = dbus.Interface(bus.get_object("org.bluez", "/"), "org.freedesktop.DBus.ObjectManager")
    objects = manager.GetManagedObjects()

    pen_dev_path = None
    char_path = None

    for path, interfaces in objects.items():
        if "org.bluez.Device1" in interfaces:
            name = interfaces["org.bluez.Device1"].get("Name", "")
            if "Lenovo Tab Pen" in name or "Pen Plus" in name:
                pen_dev_path = path
                break

    if not pen_dev_path:
        print("❌ Lenovo Tab Pen Plus not connected.")
        sys.exit(1)

    for path, interfaces in objects.items():
        if path.startswith(pen_dev_path) and "org.bluez.GattCharacteristic1" in interfaces:
            uuid = interfaces["org.bluez.GattCharacteristic1"].get("UUID", "")
            if uuid.lower().startswith("00002a4d"):
                char_path = path
                break

    if not char_path:
        print(f"❌ HID Report characteristic (00002a4d) not found under {pen_dev_path}")
        sys.exit(1)

    ui = create_uinput_device()
    if ui:
        print("✅ Virtual uinput keyboard device initialized.")
    else:
        print("ℹ️ Running in logging mode (no uinput emission).")

    def properties_changed(interface, changed, invalidated, path):
        if path == char_path and "Value" in changed:
            val = tuple(int(x) for x in changed["Value"])
            now = datetime.datetime.now().strftime("%H:%M:%S.%f")[:-3]
            gesture = GESTURE_NAMES.get(val, "Unknown")
            print(f"[{now}] Detected: {gesture} ({val})")

            if ui and val in KEY_MAPPINGS:
                keys = KEY_MAPPINGS[val]
                for key in keys:
                    ui.write(e.EV_KEY, key, 1)
                ui.syn()
                for key in keys:
                    ui.write(e.EV_KEY, key, 0)
                ui.syn()
                print(f"       -> Emitted Linux keys: {keys}")

    bus.add_signal_receiver(
        properties_changed,
        dbus_interface="org.freedesktop.DBus.Properties",
        signal_name="PropertiesChanged",
        path_keyword="path"
    )

    try:
        char_obj = bus.get_object("org.bluez", char_path)
        char_iface = dbus.Interface(char_obj, "org.bluez.GattCharacteristic1")
        char_iface.StartNotify()
    except Exception as e:
        print(f"StartNotify info: {e}")

    print("Listening for pen button presses...")
    loop = GLib.MainLoop()
    try:
        loop.run()
    except KeyboardInterrupt:
        if ui:
            ui.close()
        print("\nDaemon terminated.")

if __name__ == "__main__":
    main()
