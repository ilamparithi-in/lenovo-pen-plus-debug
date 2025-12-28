package dev.ilamparithi.lppdebug.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ilamparithi.lppdebug.model.ClickType
import dev.ilamparithi.lppdebug.model.DetectionMethod
import dev.ilamparithi.lppdebug.model.StylusEvent
import dev.ilamparithi.lppdebug.service.ServiceStatus
import dev.ilamparithi.lppdebug.service.ShizukuLogcatService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for managing stylus event detection state
 */
class StylusEventViewModel(application: Application) : AndroidViewModel(application) {

    private val shizukuService = ShizukuLogcatService(application)

    private val _events = MutableStateFlow<List<StylusEvent>>(emptyList())
    val events: StateFlow<List<StylusEvent>> = _events.asStateFlow()

    private val _lastEvent = MutableStateFlow<StylusEvent?>(null)
    val lastEvent: StateFlow<StylusEvent?> = _lastEvent.asStateFlow()

    private val _serviceStatus = MutableStateFlow<ServiceStatus>(ServiceStatus.NOT_RUNNING)
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

    init {
        // Collect service status updates
        viewModelScope.launch {
            shizukuService.status.collect { status ->
                _serviceStatus.value = status
            }
        }

        // Collect stylus events
        viewModelScope.launch {
            shizukuService.events.collect { event ->
                // Update last event
                _lastEvent.value = event

                // Add to event history (keep last 100 events)
                _events.update { currentEvents ->
                    (listOf(event) + currentEvents).take(100)
                }
            }
        }

        // Check initial Shizuku status
        checkShizukuStatus()
    }

    fun checkShizukuStatus() {
        shizukuService.checkShizukuStatus()
    }

    fun requestPermission() {
        shizukuService.requestPermission()
    }

    fun startMonitoring() {
        shizukuService.startMonitoring()
    }

    fun stopMonitoring() {
        shizukuService.stopMonitoring()
    }

    fun clearEvents() {
        _events.value = emptyList()
        _lastEvent.value = null
    }
    
    /**
     * Manually add KeyEvent detection
     * Called when app receives keycode 600-604 at application layer
     * 
     * KeyCode mapping (confirmed via logcat):
     * 600 = Single press
     * 601 = Double press
     * 602 = Triple press
     * 603 = Long press
     * 604 = Long press + Click screen
     */
    fun addKeyEventDetection(keycode: Int) {
        val clickType = when (keycode) {
            600 -> ClickType.SINGLE
            601 -> ClickType.DOUBLE
            602 -> ClickType.TRIPLE
            603 -> ClickType.LONG_PRESS
            604 -> ClickType.LONG_PRESS_AND_CLICK
            else -> ClickType.UNKNOWN
        }
        
        val buttonName = when (keycode) {
            600 -> "Single press"
            601 -> "Double press"
            602 -> "Triple press"
            603 -> "Long press"
            604 -> "Long press + Click"
            else -> "Unknown"
        }
        
        val event = StylusEvent(
            clickType = clickType,
            keycode = keycode,
            detectionMethod = DetectionMethod.KEYEVENT,
            rawLogLine = "KeyEvent intercepted at app layer: $buttonName"
        )
        
        _lastEvent.value = event
        _events.value = listOf(event) + _events.value
    }

    override fun onCleared() {
        super.onCleared()
        shizukuService.dispose()
    }
}
