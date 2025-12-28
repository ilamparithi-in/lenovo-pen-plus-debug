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
 *     // IMPORTANT: Only emit on ACTION_UP to avoid duplicate detections
 *     // If you override dispatchKeyEvent(), call penListener.onKeyEvent(event)
 *     override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
 *         if (penListener.onKeyUp(keyCode, event)) return true
 *         return super.onKeyUp(keyCode, event)
 *     }
 *
 *     override fun dispatchKeyEvent(event: KeyEvent): Boolean {
 *         if (penListener.onKeyEvent(event)) return true
 *         return super.dispatchKeyEvent(event)
 *     }
 * }
 * ```
 */
class LenovoPenButtonListener(
    private val onButtonPressed: (PenButtonEvent) -> Unit,
    private val onDebug: ((String) -> Unit)? = null
) {
    // Track the last down event so we only emit once (601 sends DOWN + UP)
    private var pendingKeyCode: Int? = null
    private var lastEmittedAt: Long = 0L

    /**
     * Drop-in handler for dispatchKeyEvent() or onKeyUp().
     * Consumes stylus keycodes 600-604 and emits exactly once per press.
     */
    fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        if (!event.isLenovoPenButton()) return false

        // Ignore long repeats from the system to avoid noise
        if (event.repeatCount > 0) return true

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                // Store and consume to avoid the follow-up ACTION_UP firing twice
                pendingKeyCode = event.keyCode
                onDebug?.invoke("Pen button DOWN code=${event.keyCode}")
                return true
            }

            KeyEvent.ACTION_UP -> {
                val codeToEmit = pendingKeyCode ?: event.keyCode
                pendingKeyCode = null

                PenButtonEvent.fromKeyCode(codeToEmit)?.let { buttonEvent ->
                    // Guard against spurious double-ups from rapid repeats
                    if (event.eventTime != lastEmittedAt) {
                        lastEmittedAt = event.eventTime
                        onButtonPressed(buttonEvent)
                        onDebug?.invoke("Pen button UP code=$codeToEmit -> $buttonEvent")
                    }
                }
                return true
            }
        }

        return false
    }

    /**
     * Convenience wrapper that matches Activity.onKeyUp signature.
     * Returns true if the event was handled as a pen button.
     */
    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean = onKeyEvent(event)
}

/**
 * Lenovo Pen Plus button events (keycodes 600-604)
 */
enum class PenButtonEvent(val keyCode: Int) {
    SINGLE_PRESS(600),            // 600 - Single button press
    DOUBLE_PRESS(601),            // 601 - Double button press
    TRIPLE_PRESS(602),            // 602 - Triple button press
    LONG_PRESS(603),              // 603 - Long button press
    LONG_PRESS_AND_CLICK(604);    // 604 - Long button press + Click screen

    companion object {
        private val lookup = values().associateBy { it.keyCode }
        fun fromKeyCode(keyCode: Int): PenButtonEvent? = lookup[keyCode]
    }
}

private fun KeyEvent.isLenovoPenButton(): Boolean = keyCode in 600..604
