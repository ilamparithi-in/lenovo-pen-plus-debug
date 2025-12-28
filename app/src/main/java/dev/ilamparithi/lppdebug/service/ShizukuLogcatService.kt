package dev.ilamparithi.lppdebug.service

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import dev.ilamparithi.lppdebug.model.LogcatEntry
import dev.ilamparithi.lppdebug.model.StylusEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku-based logcat monitoring service
 * 
 * Uses Shizuku API to execute logcat with ADB-level privilege.
 * Filters for BluetoothPenInputPolicy events containing stylus button data.
 * 
 * NO ROOT REQUIRED - Shizuku provides shell-level access via ADB wireless.
 */
class ShizukuLogcatService(private val context: Context) {
    
    companion object {
        private const val TAG = "ShizukuLogcatService"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
        
        // Logcat command to monitor stylus events
        // Filter: Only BluetoothPenInputPolicy with DEBUG level
        private const val LOGCAT_COMMAND = "logcat -v time *:S BluetoothPenInputPolicy:D"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO)
    private var logcatJob: Job? = null
    
    private val _events = MutableSharedFlow<StylusEvent>(replay = 10)
    val events: SharedFlow<StylusEvent> = _events.asSharedFlow()
    
    private val _status = MutableSharedFlow<ServiceStatus>(replay = 1)
    val status: SharedFlow<ServiceStatus> = _status.asSharedFlow()
    
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Shizuku permission granted")
                scope.launch {
                    _status.emit(ServiceStatus.READY)
                }
            } else {
                Log.e(TAG, "Shizuku permission denied")
                scope.launch {
                    _status.emit(ServiceStatus.PERMISSION_DENIED)
                }
            }
        }
    }
    
    init {
        Shizuku.addRequestPermissionResultListener(permissionListener)
        checkShizukuStatus()
    }
    
    /**
     * Check Shizuku availability and request permission if needed
     */
    fun checkShizukuStatus() {
        scope.launch {
            try {
                when {
                    !Shizuku.pingBinder() -> {
                        Log.w(TAG, "Shizuku binder not available - service not running")
                        _status.emit(ServiceStatus.NOT_RUNNING)
                    }
                    
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                        Log.d(TAG, "Shizuku permission already granted")
                        _status.emit(ServiceStatus.READY)
                    }
                    
                    Shizuku.shouldShowRequestPermissionRationale() -> {
                        Log.d(TAG, "Should show permission rationale")
                        _status.emit(ServiceStatus.PERMISSION_NEEDED)
                    }
                    
                    else -> {
                        Log.d(TAG, "Requesting Shizuku permission")
                        _status.emit(ServiceStatus.PERMISSION_NEEDED)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking Shizuku status", e)
                _status.emit(ServiceStatus.ERROR(e.message ?: "Unknown error"))
            }
        }
    }
    
    /**
     * Request Shizuku permission from user
     */
    fun requestPermission() {
        try {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting permission", e)
            scope.launch {
                _status.emit(ServiceStatus.ERROR(e.message ?: "Failed to request permission"))
            }
        }
    }
    
    /**
     * Start monitoring logcat for stylus events
     */
    fun startMonitoring() {
        if (logcatJob?.isActive == true) {
            Log.w(TAG, "Monitoring already active")
            return
        }
        
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot start monitoring - no Shizuku permission")
            scope.launch {
                _status.emit(ServiceStatus.PERMISSION_DENIED)
            }
            return
        }
        
        logcatJob = scope.launch {
            var process: Process? = null
            try {
                _status.emit(ServiceStatus.MONITORING)
                Log.d(TAG, "Starting logcat monitoring: $LOGCAT_COMMAND")
                
                // Use reflection to access Shizuku.newProcess() which is hidden but functional
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                
                process = method.invoke(
                    null,
                    arrayOf("sh", "-c", LOGCAT_COMMAND),
                    null,
                    null
                ) as Process
                
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        processLogcatLine(line)
                    }
                }
                
                Log.d(TAG, "Logcat monitoring ended")
                _status.emit(ServiceStatus.STOPPED)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during monitoring", e)
                _status.emit(ServiceStatus.ERROR(e.message ?: "Monitoring failed"))
            } finally {
                process?.destroy()
            }
        }
    }
    
    /**
     * Stop monitoring logcat
     */
    fun stopMonitoring() {
        logcatJob?.cancel()
        logcatJob = null
        scope.launch {
            _status.emit(ServiceStatus.STOPPED)
        }
    }
    
    /**
     * Parse logcat line and extract stylus event if present
     * 
     * Expected format (time format):
     * 12-25 10:30:45.123 D/BluetoothPenInputPolicy( 1234): processStylusPenKeyEvent: keycode 602,mIsStylusPenRemoteControl false,clickStatusType 2
     */
    private suspend fun processLogcatLine(line: String) {
        try {
            // Parse time-format logcat line
            val entry = parseLogcatLine(line) ?: return
            
            // Only process BluetoothPenInputPolicy logs
            if (entry.tag != "BluetoothPenInputPolicy") return
            
            // Extract stylus event from message
            val event = entry.parseStylusEvent()
            if (event != null) {
                Log.d(TAG, "Detected stylus event: ${event.clickType} (keycode ${event.keycode})")
                _events.emit(event)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing logcat line: $line", e)
        }
    }
    
    /**
     * Parse logcat line in time format
     * Format: 12-25 10:30:45.123 D/Tag( PID): Message
     */
    private fun parseLogcatLine(line: String): LogcatEntry? {
        val regex = Regex("""(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEF])/([^(]+)\(\s*(\d+)\):\s*(.*)""")
        val match = regex.find(line) ?: return null
        
        return LogcatEntry(
            timestamp = match.groupValues[1],
            pid = match.groupValues[4].toInt(),
            tid = 0, // Not available in time format
            level = match.groupValues[2],
            tag = match.groupValues[3].trim(),
            message = match.groupValues[5]
        )
    }
    
    /**
     * Clean up resources
     */
    fun dispose() {
        stopMonitoring()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        scope.cancel()
    }
}

/**
 * Service status states
 */
sealed class ServiceStatus {
    object NOT_RUNNING : ServiceStatus()
    object PERMISSION_NEEDED : ServiceStatus()
    object PERMISSION_DENIED : ServiceStatus()
    object READY : ServiceStatus()
    object MONITORING : ServiceStatus()
    object STOPPED : ServiceStatus()
    data class ERROR(val message: String) : ServiceStatus()
}
