package dev.ilamparithi.lppdebug.model

import java.util.Date

/**
 * Stylus button event model
 * 
 * Maps Lenovo proprietary keycodes to human-readable events.
 * 
 * CONFIRMED WORKING (ALL KEYCODES):
 * - 600: Single press (ACTION_UP only)
 * - 601: Double press (ACTION_DOWN + ACTION_UP)
 * - 602: Triple press (ACTION_UP only)
 * - 603: Long press (ACTION_UP only)
 * - 604: Long press + Click (ACTION_UP only)
 */
data class StylusEvent(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Date = Date(),
    val clickType: ClickType,
    val keycode: Int,
    val detectionMethod: DetectionMethod,
    val rawLogLine: String? = null
)

/**
 * Click types decoded from logcat clickStatusType OR KeyEvent keycodes
 * 
 * Mapping (confirmed via logcat analysis):
 * - statusType 0 / keycode 600 = Single press
 * - statusType 1 / keycode 601 = Double press
 * - statusType 2 / keycode 602 = Triple press
 * - statusType 3 / keycode 603 = Long press
 * - statusType 4 / keycode 604 = Long press + Click screen
 */
enum class ClickType(val statusType: Int, val description: String) {
    SINGLE(0, "Single Press"),
    DOUBLE(1, "Double Press"),
    TRIPLE(2, "Triple Press"),
    LONG_PRESS(3, "Long Press"),
    LONG_PRESS_AND_CLICK(4, "Long Press + Click"),
    UNKNOWN(-1, "Unknown");
    
    companion object {
        fun fromStatusType(type: Int): ClickType {
            return values().find { it.statusType == type } ?: UNKNOWN
        }
    }
}

/**
 * Lenovo vendor-specific keycodes (confirmed working)
 * 
 * These are NOT part of Android KeyEvent.KEYCODE_* constants.
 * They ARE accessible via onKeyUp() in regular apps (no permissions needed!)
 * 
 * Event patterns:
 * - 600: ACTION_UP only
 * - 601: ACTION_DOWN + ACTION_UP (only button that sends both)
 * - 602: ACTION_UP only
 * - 603: ACTION_UP only
 * - 604: ACTION_UP only
 */
enum class StylusKeycode(val code: Int, val description: String) {
    KEYCODE_600(600, "Single Press"),
    KEYCODE_601(601, "Double Press"),
    KEYCODE_602(602, "Triple Press"),
    KEYCODE_603(603, "Long Press"),
    KEYCODE_604(604, "Long Press + Click"),
    UNKNOWN(-1, "Unknown Keycode");
    
    companion object {
        fun fromCode(code: Int): StylusKeycode {
            return values().find { it.code == code } ?: UNKNOWN
        }
    }
}

/**
 * Detection method indicates how we observed the event
 */
enum class DetectionMethod(val label: String, val color: Int) {
    /**
     * LOGCAT: Shizuku logcat parsing
     * Direct observation of BluetoothPenInputPolicy logs
     * Requires Shizuku permission
     * Detects all button types (600-604)
     */
    LOGCAT(
        "DIRECT — via logcat",
        android.graphics.Color.parseColor("#00FF00")
    ),
    
    /**
     * KEYEVENT: KeyEvent interception (RECOMMENDED)
     * Keycodes 600-604 reach app layer via onKeyUp()
     * NO PERMISSIONS NEEDED - works in any app!
     * Detects all button types (600-604)
     * 
     * Detecting ONLY in onKeyUp() because:
     * - 600, 602, 603, 604 send ACTION_UP only
     * - 601 sends both DOWN and UP (would cause duplicate if detected in both)
     */
    KEYEVENT(
        "DIRECT — via KeyEvent",
        android.graphics.Color.parseColor("#00FFFF")
    ),
    
    /**
     * ACCESSIBILITY: AccessibilityService (NOT WORKING)
     * Observed side effects only (toolbox appearance, window changes)
     * Does NOT see the actual button press
     * Only works if Lenovo toolbox is enabled
     */
    ACCESSIBILITY(
        "INFERRED — via UI behavior",
        android.graphics.Color.parseColor("#FFA500")
    );
}

/**
 * Parsed logcat line data
 */
data class LogcatEntry(
    val timestamp: String,
    val pid: Int,
    val tid: Int,
    val level: String,
    val tag: String,
    val message: String
) {
    /**
     * Parse stylus event from BluetoothPenInputPolicy log
     * Example: "processStylusPenKeyEvent: keycode 602,mIsStylusPenRemoteControl false,clickStatusType 2"
     */
    fun parseStylusEvent(): StylusEvent? {
        if (tag != "BluetoothPenInputPolicy") return null
        
        val keycodeMatch = Regex("keycode (\\d+)").find(message)
        val clickTypeMatch = Regex("clickStatusType (\\d+)").find(message)
        
        if (keycodeMatch == null || clickTypeMatch == null) return null
        
        val keycode = keycodeMatch.groupValues[1].toInt()
        val clickStatusType = clickTypeMatch.groupValues[1].toInt()
        
        return StylusEvent(
            clickType = ClickType.fromStatusType(clickStatusType),
            keycode = keycode,
            detectionMethod = DetectionMethod.LOGCAT,
            rawLogLine = message
        )
    }
}
