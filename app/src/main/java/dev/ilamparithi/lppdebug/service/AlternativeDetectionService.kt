package dev.ilamparithi.lppdebug.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import dev.ilamparithi.lppdebug.model.ClickType
import dev.ilamparithi.lppdebug.model.DetectionMethod
import dev.ilamparithi.lppdebug.model.StylusEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Service to test alternative detection methods without logcat
 * 
 * Tests:
 * 1. Broadcast receiver for Lenovo intents
 * 2. KeyEvent monitoring
 * 3. MotionEvent stylus button state
 */
class AlternativeDetectionService : Service() {
    
    companion object {
        private const val TAG = "AltDetection"
        
        // Lenovo's protected broadcast action
        private const val LENOVO_STYLUS_ACTION = "lenovo.intent.action.INPUT_DEVICE_CLICK_STATE_CHANGED"
        
        const val ACTION_DETECTION_RESULT = "dev.ilamparithi.lppdebug.DETECTION_RESULT"
        const val EXTRA_METHOD = "method"
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_DATA = "data"
    }
    
    private val _events = MutableSharedFlow<DetectionResult>(replay = 10)
    val events: SharedFlow<DetectionResult> = _events.asSharedFlow()
    
    private val lenovoBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.i(TAG, "✅ BROADCAST RECEIVED! Action: ${intent?.action}")
            
            if (intent?.action == LENOVO_STYLUS_ACTION) {
                val extras = intent.extras
                Log.i(TAG, "Lenovo stylus broadcast extras:")
                extras?.keySet()?.forEach { key ->
                    Log.i(TAG, "  $key = ${extras.get(key)}")
                }
                
                broadcastResult(
                    method = "Broadcast Receiver",
                    success = true,
                    data = "Received Lenovo intent with ${extras?.size() ?: 0} extras"
                )
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Alternative detection service starting...")
        
        // Test 1: Try to register for Lenovo's broadcast
        testBroadcastReceiver()
    }
    
    private fun testBroadcastReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(LENOVO_STYLUS_ACTION)
                // Try different priority levels
                priority = IntentFilter.SYSTEM_HIGH_PRIORITY
            }
            
            registerReceiver(lenovoBroadcastReceiver, filter)
            
            Log.i(TAG, "✅ Successfully registered broadcast receiver for: $LENOVO_STYLUS_ACTION")
            Log.i(TAG, "⏳ Waiting for stylus button press to test...")
            
            broadcastResult(
                method = "Broadcast Receiver Registration",
                success = true,
                data = "Registered (but likely won't receive due to FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT)"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to register broadcast receiver", e)
            broadcastResult(
                method = "Broadcast Receiver Registration",
                success = false,
                data = "Error: ${e.message}"
            )
        }
    }
    
    /**
     * Test if KeyEvent contains stylus button presses
     * NOTE: This needs to be called from an Activity with focus
     */
    fun testKeyEvent(event: KeyEvent): DetectionResult {
        Log.d(TAG, "KeyEvent: action=${event.action}, code=${event.keyCode}, " +
                "device=${event.deviceId}, source=${event.source}")
        
        // Check if this is a stylus-related key
        val isStylusKey = event.keyCode in 600..604
        
        return if (isStylusKey) {
            Log.i(TAG, "✅ FOUND STYLUS KEYCODE: ${event.keyCode}")
            DetectionResult(
                method = "KeyEvent",
                success = true,
                data = "Keycode ${event.keyCode} detected!",
                event = StylusEvent(
                    clickType = ClickType.UNKNOWN,
                    keycode = event.keyCode,
                    detectionMethod = DetectionMethod.ACCESSIBILITY,
                    rawLogLine = "From KeyEvent"
                )
            )
        } else {
            DetectionResult(
                method = "KeyEvent",
                success = false,
                data = "Normal keycode ${event.keyCode}, not stylus"
            )
        }
    }
    
    /**
     * Test if MotionEvent contains stylus button state
     * NOTE: This needs to be called from an Activity/View
     */
    fun testMotionEvent(event: MotionEvent): DetectionResult {
        val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
        
        if (isStylus) {
            // Check button states
            val primaryButton = event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0
            val secondaryButton = event.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY != 0
            
            Log.d(TAG, "Stylus MotionEvent: primary=$primaryButton, secondary=$secondaryButton, " +
                    "action=${event.action}, buttonState=${event.buttonState}")
            
            if (primaryButton || secondaryButton) {
                Log.i(TAG, "✅ STYLUS BUTTON DETECTED in MotionEvent!")
                return DetectionResult(
                    method = "MotionEvent",
                    success = true,
                    data = "Primary=$primaryButton, Secondary=$secondaryButton",
                    event = StylusEvent(
                        clickType = ClickType.UNKNOWN,
                        keycode = -1,
                        detectionMethod = DetectionMethod.ACCESSIBILITY,
                        rawLogLine = "From MotionEvent button state"
                    )
                )
            }
        }
        
        return DetectionResult(
            method = "MotionEvent",
            success = false,
            data = "No stylus button pressed"
        )
    }
    
    private fun broadcastResult(method: String, success: Boolean, data: String) {
        val intent = Intent(ACTION_DETECTION_RESULT).apply {
            putExtra(EXTRA_METHOD, method)
            putExtra(EXTRA_SUCCESS, success)
            putExtra(EXTRA_DATA, data)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(lenovoBroadcastReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}

data class DetectionResult(
    val method: String,
    val success: Boolean,
    val data: String,
    val event: StylusEvent? = null
)
