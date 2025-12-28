package dev.ilamparithi.lppdebug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import dev.ilamparithi.lppdebug.model.StylusEvent
import dev.ilamparithi.lppdebug.service.AlternativeDetectionService
import dev.ilamparithi.lppdebug.service.ServiceStatus
import dev.ilamparithi.lppdebug.viewmodel.StylusEventViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var viewModel: StylusEventViewModel
    private var alternativeService: AlternativeDetectionService? = null

    // UI Components
    private lateinit var statusIndicator: View
    private lateinit var statusText: TextView
    private lateinit var btnStartStop: Button
    private lateinit var btnRequestPermission: Button
    private lateinit var btnClear: Button
    private lateinit var btnTestAlternatives: Button
    private lateinit var lastEventCard: View
    private lateinit var lastEventType: TextView
    private lateinit var lastEventTimestamp: TextView
    private lateinit var lastEventConfidence: TextView
    private lateinit var lastEventKeycode: TextView
    private lateinit var eventColumnsScroll: View
    private lateinit var eventColumnsContainer: android.widget.LinearLayout
    private lateinit var noEventsText: TextView
    
    // Column views for each detection method
    private val methodColumns = mutableMapOf<dev.ilamparithi.lppdebug.model.DetectionMethod, android.widget.LinearLayout>()
    
    private val detectionResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val method = intent?.getStringExtra(AlternativeDetectionService.EXTRA_METHOD) ?: return
            val success = intent.getBooleanExtra(AlternativeDetectionService.EXTRA_SUCCESS, false)
            val data = intent.getStringExtra(AlternativeDetectionService.EXTRA_DATA) ?: ""
            
            Log.i(TAG, "Detection result: $method = $success: $data")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viewModel = ViewModelProvider(this)[StylusEventViewModel::class.java]

        initViews()
        observeViewModel()
        setupAlternativeDetection()

        // Show info dialog on first launch
        showInfoDialog()
    }
    
    private fun setupAlternativeDetection() {
        // Register receiver for detection results
        val filter = IntentFilter(AlternativeDetectionService.ACTION_DETECTION_RESULT)
        registerReceiver(detectionResultReceiver, filter, RECEIVER_NOT_EXPORTED)
        
        // Start alternative detection service
        startService(Intent(this, AlternativeDetectionService::class.java))
    }
    
    // REMOVED: onKeyDown detection to avoid duplicates
    // KeyCode 601 sends both DOWN and UP, causing duplicate detections
    // All other codes (600, 602, 603, 604) send only UP
    // Solution: Detect ONLY in onKeyUp()
    
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        event?.let {
            Log.d(TAG, "⌨️ KeyUp: code=$keyCode, device=${it.deviceId}")
            if (keyCode in 600..604) {
                val buttonName = when (keyCode) {
                    600 -> "SINGLE PRESS"
                    601 -> "DOUBLE PRESS"
                    602 -> "TRIPLE PRESS"
                    603 -> "LONG PRESS"
                    604 -> "LONG PRESS + CLICK"
                    else -> "UNKNOWN"
                }
                Log.i(TAG, "✅ STYLUS BUTTON DETECTED: $keyCode ($buttonName)")
                
                // Add to event history (no popup)
                viewModel.addKeyEventDetection(keyCode)
                return true  // Consume the event
            }
        }
        return super.onKeyUp(keyCode, event)
    }
    
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            if (it.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
                val primary = it.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0
                val secondary = it.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY != 0
                
                if (primary || secondary) {
                    Log.i(TAG, "✅ STYLUS BUTTON IN MOTION EVENT: primary=$primary, secondary=$secondary")
                    showDetectionResult("MotionEvent", true, "Button state detected!")
                }
            }
        }
        return super.onTouchEvent(event)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(detectionResultReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }

    private fun initViews() {
        statusIndicator = findViewById(R.id.statusIndicator)
        statusText = findViewById(R.id.statusText)
        btnStartStop = findViewById(R.id.btnStartStop)
        btnRequestPermission = findViewById(R.id.btnRequestPermission)
        btnClear = findViewById(R.id.btnClear)
        btnTestAlternatives = findViewById(R.id.btnTestAlternatives)
        lastEventCard = findViewById(R.id.lastEventCard)
        lastEventType = findViewById(R.id.lastEventType)
        lastEventTimestamp = findViewById(R.id.lastEventTimestamp)
        lastEventConfidence = findViewById(R.id.lastEventConfidence)
        lastEventKeycode = findViewById(R.id.lastEventKeycode)
        eventColumnsScroll = findViewById(R.id.eventColumnsScroll)
        eventColumnsContainer = findViewById(R.id.eventColumnsContainer)
        noEventsText = findViewById(R.id.noEventsText)

        btnStartStop.setOnClickListener {
            when (viewModel.serviceStatus.value) {
                ServiceStatus.READY, ServiceStatus.STOPPED -> viewModel.startMonitoring()
                ServiceStatus.MONITORING -> viewModel.stopMonitoring()
                else -> {}
            }
        }

        btnRequestPermission.setOnClickListener {
            viewModel.requestPermission()
        }

        btnClear.setOnClickListener {
            viewModel.clearEvents()
        }
        
        btnTestAlternatives.setOnClickListener {
            showAlternativeTestDialog()
        }
    }
    
    private fun showAlternativeTestDialog() {
        AlertDialog.Builder(this)
            .setTitle("Alternative Detection Methods")
            .setMessage("""
                Testing non-logcat detection methods:
                
                ✅ Broadcast Receiver: Registered
                   (Likely won't work - protected broadcast)
                
                ✅ KeyEvent monitoring: Active
                   (Press stylus button to test)
                
                ✅ MotionEvent monitoring: Active
                   (Touch screen with stylus)
                
                ✅ AccessibilityService: Available
                   (Enable in Settings → Accessibility)
                
                Press stylus button now to test all methods!
            """.trimIndent())
            .setPositiveButton("Enable Accessibility") { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton("Close", null)
            .show()
    }
    
    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            Log.e(TAG, "Error opening accessibility settings", e)
        }
    }
    
    private fun showDetectionResult(method: String, success: Boolean, message: String) {
        runOnUiThread {
            val icon = if (success) "✅" else "❌"
            AlertDialog.Builder(this)
                .setTitle("$icon $method")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun observeViewModel() {
        // Observe service status
        lifecycleScope.launch {
            viewModel.serviceStatus.collect { status ->
                updateStatusUI(status)
            }
        }

        // Observe last event
        lifecycleScope.launch {
            viewModel.lastEvent.collect { event ->
                updateLastEventUI(event)
            }
        }

        // Observe event history and update columns
        lifecycleScope.launch {
            viewModel.events.collect { events ->
                updateEventColumns(events)
                noEventsText.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
                eventColumnsScroll.visibility = if (events.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }
    
    private fun updateEventColumns(events: List<StylusEvent>) {
        // Group events by detection method
        val groupedEvents = events.groupBy { it.detectionMethod }
        
        // Remove columns for methods with no events
        val methodsToRemove = methodColumns.keys.filter { it !in groupedEvents.keys }
        methodsToRemove.forEach { method ->
            methodColumns[method]?.let { eventColumnsContainer.removeView(it) }
            methodColumns.remove(method)
        }
        
        // Add or update columns for each detection method
        groupedEvents.forEach { (method, methodEvents) ->
            val column = methodColumns.getOrPut(method) {
                createMethodColumn(method)
            }
            
            // Update column content
            updateMethodColumn(column, method, methodEvents)
        }
    }
    
    private fun createMethodColumn(method: dev.ilamparithi.lppdebug.model.DetectionMethod): android.widget.LinearLayout {
        val column = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(android.R.dimen.app_icon_size) * 2,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width) / 4
            }
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
        }
        
        // Add header
        val header = TextView(this).apply {
            text = method.label
            setTextColor(method.color)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        column.addView(header)
        
        // Add to container
        eventColumnsContainer.addView(column)
        return column
    }
    
    private fun updateMethodColumn(
        column: android.widget.LinearLayout,
        method: dev.ilamparithi.lppdebug.model.DetectionMethod,
        events: List<StylusEvent>
    ) {
        // Remove all views except header (first child)
        while (column.childCount > 1) {
            column.removeViewAt(1)
        }
        
        // Add events (most recent first, limit to 10)
        events.take(10).forEach { event ->
            val eventView = createEventView(event)
            column.addView(eventView)
        }
    }
    
    private fun createEventView(event: StylusEvent): View {
        return android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8
            }
            setPadding(8, 8, 8, 8)
            setBackgroundColor(android.graphics.Color.WHITE)
            
            // Event type
            addView(TextView(this@MainActivity).apply {
                text = event.clickType.description
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            
            // Timestamp
            addView(TextView(this@MainActivity).apply {
                text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(event.timestamp)
                textSize = 10f
                setTextColor(android.graphics.Color.GRAY)
            })
            
            // Keycode
            addView(TextView(this@MainActivity).apply {
                text = "KC: ${event.keycode}"
                textSize = 10f
                setTextColor(android.graphics.Color.DKGRAY)
            })
        }
    }

    private fun updateStatusUI(status: ServiceStatus) {
        when (status) {
            ServiceStatus.NOT_RUNNING -> {
                statusIndicator.setBackgroundColor(Color.parseColor("#FFA500"))
                statusText.text = "⚡ KeyEvent Mode (Shizuku not running)"
                btnStartStop.visibility = View.GONE
                btnRequestPermission.visibility = View.GONE
            }
            ServiceStatus.PERMISSION_NEEDED -> {
                statusIndicator.setBackgroundColor(Color.parseColor("#FFA500"))
                statusText.text = "⚡ KeyEvent Mode (Shizuku available - grant permission for logcat)"
                btnStartStop.visibility = View.GONE
                btnRequestPermission.visibility = View.VISIBLE
            }
            ServiceStatus.PERMISSION_DENIED -> {
                statusIndicator.setBackgroundColor(Color.parseColor("#FFA500"))
                statusText.text = "⚡ KeyEvent Mode (Shizuku permission denied)"
                btnStartStop.visibility = View.GONE
                btnRequestPermission.visibility = View.VISIBLE
            }
            ServiceStatus.READY -> {
                statusIndicator.setBackgroundColor(Color.YELLOW)
                statusText.text = "⏸️ Logcat Ready - Press to start"
                btnStartStop.visibility = View.VISIBLE
                btnStartStop.isEnabled = true
                btnStartStop.text = "Start Logcat Monitoring"
                btnRequestPermission.visibility = View.GONE
            }
            ServiceStatus.MONITORING -> {
                statusIndicator.setBackgroundColor(Color.GREEN)
                statusText.text = "✅ Logcat + KeyEvent Active"
                btnStartStop.visibility = View.VISIBLE
                btnStartStop.isEnabled = true
                btnStartStop.text = "Stop Logcat Monitoring"
                btnRequestPermission.visibility = View.GONE
            }
            ServiceStatus.STOPPED -> {
                statusIndicator.setBackgroundColor(Color.GRAY)
                statusText.text = "⚡ KeyEvent Mode (Logcat stopped)"
                btnStartStop.visibility = View.VISIBLE
                btnStartStop.isEnabled = true
                btnStartStop.text = "Start Logcat Monitoring"
                btnRequestPermission.visibility = View.GONE
            }
            is ServiceStatus.ERROR -> {
                statusIndicator.setBackgroundColor(Color.parseColor("#FFA500"))
                statusText.text = "⚡ KeyEvent Mode (${status.message})"
                btnStartStop.visibility = View.GONE
                btnRequestPermission.visibility = View.GONE
            }
        }
    }

    private fun updateLastEventUI(event: StylusEvent?) {
        if (event == null) {
            lastEventCard.visibility = View.GONE
            return
        }

        lastEventCard.visibility = View.VISIBLE
        lastEventType.text = event.clickType.description
        lastEventTimestamp.text = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(event.timestamp)
        lastEventConfidence.text = event.detectionMethod.label
        lastEventConfidence.setTextColor(event.detectionMethod.color)
        lastEventKeycode.text = "Keycode: ${event.keycode}"
    }

    private fun showInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("Lenovo Pen Plus Debug")
            .setMessage("""
                This app detects Lenovo proprietary stylus button events using Shizuku.

                REQUIREMENTS:
                • Shizuku app installed and running
                • Wireless ADB enabled OR USB debugging

                DETECTION METHOD:
                ✅ Direct observation via logcat
                • Monitors BluetoothPenInputPolicy logs
                • No root access required

                LIMITATIONS:
                ⚠️ Cannot intercept the broadcast intent
                ⚠️ Cannot prevent default handling

                This is a diagnostic tool showing what the system sees internally.
            """.trimIndent())
            .setPositiveButton("Got it", null)
            .show()
    }
}
