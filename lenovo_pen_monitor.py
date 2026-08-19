#!/usr/bin/env python3
"""
Lenovo Tab Pen Plus - Live Gesture & KeyEvent Monitor
Listens to BlueZ GATT notifications and displays raw bytes + mapped events live.
"""
import sys
import datetime
import dbus
from dbus.mainloop.glib import DBusGMainLoop
from gi.repository import GLib

DBusGMainLoop(set_as_default=True)
bus = dbus.SystemBus()
manager = dbus.Interface(bus.get_object("org.bluez", "/"), "org.freedesktop.DBus.ObjectManager")
objects = manager.GetManagedObjects()

# Finding the Lenovo Pen
pen_dev_path = None
char_path = None

for path, interfaces in objects.items():
    if "org.bluez.Device1" in interfaces:
        name = interfaces["org.bluez.Device1"].get("Name", "")
        if "Lenovo Tab Pen" in name or "Pen Plus" in name:
            pen_dev_path = path
            break

if not pen_dev_path:
    print("❌ Lenovo Tab Pen Plus not found in connected Bluetooth devices.")
    print("   Please connect/pair it first via bluetoothctl or Settings.")
    sys.exit(1)

# Find the HID Report characteristic (UUID 00002a4d)
for path, interfaces in objects.items():
    if path.startswith(pen_dev_path) and "org.bluez.GattCharacteristic1" in interfaces:
        uuid = interfaces["org.bluez.GattCharacteristic1"].get("UUID", "")
        if uuid.lower().startswith("00002a4d"):
            char_path = path
            break

if not char_path:
    print(f"❌ Could not locate characteristic 00002a4d under {pen_dev_path}")
    sys.exit(1)

GESTURE_MAP = {
    (2, 1):  ("Single Press",        "600", "0 (clickStatusType 0)"),
    (2, 2):  ("Double Press",        "601", "1 (clickStatusType 1)"),
    (2, 4):  ("Triple Press",        "602", "2 (clickStatusType 2)"),
    (2, 8):  ("Long Press",          "603", "3 (clickStatusType 3)"),
    (2, 16): ("Long Press + Click",  "604", "4 (clickStatusType 4)"),
}

print("=" * 80)
print("  LENOVO TAB PEN PLUS - LIVE EVENT MONITOR")
print("=" * 80)
print(f"  Device Path    : {pen_dev_path}")
print(f"  Characteristic : {char_path}")
print("=" * 80)
print(f"{'TIMESTAMP':<12} | {'RAW BYTES':<10} | {'GESTURE':<22} | {'MAPPED ANDROID KEY'}")
print("-" * 80)

def properties_changed(interface, changed, invalidated, path):
    if path == char_path and "Value" in changed:
        val = tuple(int(x) for x in changed["Value"])
        hex_val = " ".join(f"{x:02X}" for x in val)
        now = datetime.datetime.now().strftime("%H:%M:%S.%f")[:-3]
        
        if val in GESTURE_MAP:
            name, keycode, zui_status = GESTURE_MAP[val]
            print(f"{now:<12} | {hex_val:<10} | \033[92m{name:<22}\033[0m | KeyCode: \033[96m{keycode:<4}\033[0m (clickStatusType: {zui_status})")
        else:
            print(f"{now:<12} | {hex_val:<10} | \033[93mUnknown/Raw\033[0m            | Raw: {list(val)}")

bus.add_signal_receiver(
    properties_changed,
    dbus_interface="org.freedesktop.DBus.Properties",
    signal_name="PropertiesChanged",
    path_keyword="path"
)

# Start notification
try:
    char_obj = bus.get_object("org.bluez", char_path)
    char_iface = dbus.Interface(char_obj, "org.bluez.GattCharacteristic1")
    char_iface.StartNotify()
except Exception as e:
    print(f"StartNotify info: {e}")

print("✨ Ready! Press the physical pen button to see events in real-time. (Press Ctrl+C to quit)\n")

loop = GLib.MainLoop()
try:
    loop.run()
except KeyboardInterrupt:
    print("\n👋 Monitoring stopped.")
