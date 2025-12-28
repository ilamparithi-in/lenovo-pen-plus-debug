package dev.ilamparithi.lppdebug.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dev.ilamparithi.lppdebug.model.ClickType
import dev.ilamparithi.lppdebug.model.DetectionMethod
import dev.ilamparithi.lppdebug.model.StylusEvent
import java.util.Date

/**
 * AccessibilityService for detecting stylus button presses via side effects
 * 
 * DETECTION METHOD: Observes UI changes caused by button presses
 * - Lenovo toolbox window appearing
 * - Screenshot notification
 * - Other system UI reactions
 * 
 * LIMITATION: Cannot determine click type or keycode
 * Can only infer that "something happened"
 */
class StylusAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "StylusAccessibility"
        const val ACTION_STYLUS_EVENT = "dev.ilamparithi.lppdebug.STYLUS_EVENT"
        const val EXTRA_EVENT_TYPE = "event_type"
        const val EXTRA_KEYCODE = "keycode"
        const val EXTRA_TIMESTAMP = "timestamp"
        
        // Known packages that appear when stylus button is pressed
        private const val LENOVO_TOOLBOX_PACKAGE = "com.lenovo.pen.toolbox"
        private const val LENOVO_PEN_PACKAGE = "com.lenovo.pen"
        private const val LENOVO_SMARTPEN_PACKAGE = "com.lenovo.smartpen"
    }
    
    private var lastToolboxAppearance: Long = 0
    private val debounceMs = 1000L // Ignore duplicate events within 1 second
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    handleWindowChange(event)
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    handleContentChange(event)
                }
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                    handleNotification(event)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        }
    }
    
    private fun handleWindowChange(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        Log.d(TAG, "Window changed: $packageName")
        
        // Detect Lenovo pen-related windows
        when {
            packageName.startsWith(LENOVO_TOOLBOX_PACKAGE) ||
            packageName.startsWith(LENOVO_PEN_PACKAGE) ||
            packageName.startsWith(LENOVO_SMARTPEN_PACKAGE) -> {
                
                val now = System.currentTimeMillis()
                if (now - lastToolboxAppearance > debounceMs) {
                    lastToolboxAppearance = now
                    
                    Log.i(TAG, "Lenovo toolbox detected! Inferring button press")
                    
                    broadcastInferredEvent(
                        reason = "Toolbox window appeared"
                    )
                }
            }
        }
    }
    
    private fun handleContentChange(event: AccessibilityEvent) {
        // Monitor for specific content changes that indicate stylus interaction
        // This is less reliable but can catch additional cases
    }
    
    private fun handleNotification(event: AccessibilityEvent) {
        val notification = event.text?.toString() ?: return
        
        // Screenshot notifications often indicate double-click
        if (notification.contains("screenshot", ignoreCase = true)) {
            Log.i(TAG, "Screenshot notification detected - likely double click")
            
            broadcastInferredEvent(
                reason = "Screenshot notification",
                likelyClickType = ClickType.DOUBLE
            )
        }
    }
    
    private fun broadcastInferredEvent(
        reason: String,
        likelyClickType: ClickType? = null
    ) {
        val event = StylusEvent(
            clickType = likelyClickType ?: ClickType.UNKNOWN,
            keycode = -1, // Unknown via accessibility
            detectionMethod = DetectionMethod.ACCESSIBILITY,
            rawLogLine = "Accessibility inferred: $reason"
        )
        
        // Broadcast to main app
        val intent = Intent(ACTION_STYLUS_EVENT).apply {
            putExtra(EXTRA_EVENT_TYPE, event.clickType.name)
            putExtra(EXTRA_KEYCODE, event.keycode)
            putExtra(EXTRA_TIMESTAMP, event.timestamp.time)
            setPackage(packageName)
        }
        sendBroadcast(intent)
        
        Log.d(TAG, "Broadcasted inferred event: $event")
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Accessibility service destroyed")
    }
}
