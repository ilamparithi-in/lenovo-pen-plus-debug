package dev.ilamparithi.lppdebug.helper

import android.view.KeyEvent

/**
 * PORTABLE IMPLEMENTATION FOR OTHER APPS
 * 
 * Simple listener for Lenovo Pen Plus button presses via KeyEvent detection.
 * Copy this file to any Android project to detect stylus button presses.
 * 
 * REQUIREMENTS:
 * - None! No permissions, no Shizuku, no special setup
 * - Tested on Lenovo tablets with Pen Plus stylus
 * - App must be in foreground to receive events
 * 
 * CONFIRMED WORKING (ALL BUTTONS):
 * - ✅ Single press (keycode 600) - ACTION_UP only
 * - ✅ Double press (keycode 601) - ACTION_DOWN + ACTION_UP
 * - ✅ Triple press (keycode 602) - ACTION_UP only
 * - ✅ Long press (keycode 603) - ACTION_UP only
 * - ✅ Long press + Click (keycode 604) - ACTION_UP only
 * 
 * USAGE:
 * ```kotlin
 * class MainActivity : AppCompatActivity() {
 *     private val penListener = LenovoPenButtonListener { event ->
 *         when (event) {
 *             PenButtonEvent.SINGLE_PRESS -> handleSingle()
 *             PenButtonEvent.DOUBLE_PRESS -> handleDouble()
 *             PenButtonEvent.TRIPLE_PRESS -> handleTriple()
 *             PenButtonEvent.LONG_PRESS -> handleLong()
 *             PenButtonEvent.LONG_PRESS_AND_CLICK -> handleLongClick()
 *         }
 *     }
 *     
 *     // IMPORTANT: Only override onKeyUp() to avoid duplicate detections!
 *     override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
 *         if (penListener.onKeyUp(keyCode, event)) {
 *             return true  // Event was handled
 *         }
 *         return super.onKeyUp(keyCode, event)
 *     }
 * }
 * ```
 */
class LenovoPenButtonListener(
    private val onButtonPressed: (PenButtonEvent) -> Unit
) {
    
    /**
     * Call this from your Activity's onKeyUp() method.
     * 
     * CRITICAL: Detect ONLY in onKeyUp() because:
     * - KeyCode 600, 602, 603, 604 send ACTION_UP only
     * - KeyCode 601 sends both DOWN and UP (would cause duplicate if detected in both)
     * 
     * @return true if the event was a pen button press and was handled
     */
    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return false
        
        val buttonEvent = when (keyCode) {
            600 -> PenButtonEvent.SINGLE_PRESS
            601 -> PenButtonEvent.DOUBLE_PRESS
            602 -> PenButtonEvent.TRIPLE_PRESS
            603 -> PenButtonEvent.LONG_PRESS
            604 -> PenButtonEvent.LONG_PRESS_AND_CLICK
            else -> return false
        }
        
        onButtonPressed(buttonEvent)
        return true  // Consume the event
    }
}

/**
 * Lenovo Pen Plus button events (keycodes 600-604)
 */
enum class PenButtonEvent {
    SINGLE_PRESS,           // 600 - Single button press
    DOUBLE_PRESS,           // 601 - Double button press
    TRIPLE_PRESS,           // 602 - Triple button press
    LONG_PRESS,             // 603 - Long button press
    LONG_PRESS_AND_CLICK    // 604 - Long button press + Click screen
}
