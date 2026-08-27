package ltechnologies.onionphone.imsnitch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ltechnologies.onionphone.imsnitch.data.UserPreferences
import ltechnologies.onionphone.imsnitch.detection.DetectionEngine
import ltechnologies.onionphone.imsnitch.detection.DetectionResult
import ltechnologies.onionphone.imsnitch.mitigation.AirplaneModeController
import ltechnologies.onionphone.imsnitch.service.CellMonitorService
import ltechnologies.onionphone.imsnitch.telephony.CellularReader

data class HomeUiState(
    val hasPermissions: Boolean = false,
    val monitoring: Boolean = false,
    val autoAirplane: Boolean = false,
    val canWriteSecureSettings: Boolean = false,
    val airplaneOn: Boolean = false,
    val adbGrantHint: String = "",
    val statusLine: String = "",
    val lastResult: DetectionResult? = null,
    val mitigationMessage: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = UserPreferences(app)
    private val reader = CellularReader(app)
    private val engine = DetectionEngine()
    private val airplane = AirplaneModeController(app)

    private val permissions = MutableStateFlow(reader.hasRequiredPermissions())
    private val mitigationMessage = MutableStateFlow<String?>(null)
    private val oneShot = MutableStateFlow<DetectionResult?>(null)
    private val secureWrite = MutableStateFlow(airplane.canWriteSecureSettings())
    private val airplaneOn = MutableStateFlow(airplane.isAirplaneModeOn())

    private val prefsSlice = combine(
        prefs.monitoringEnabled,
        prefs.autoAirplaneOnCritical,
    ) { monitoring, autoAirplane ->
        monitoring to autoAirplane
    }

    private val liveSlice = combine(
        permissions,
        CellMonitorService.state,
        oneShot,
    ) { hasPerms, svc, shot ->
        Triple(hasPerms, svc, shot)
    }

    private val deviceSlice = combine(
        mitigationMessage,
        secureWrite,
        airplaneOn,
    ) { mitMsg, canSecure, air ->
        Triple(mitMsg, canSecure, air)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        prefsSlice,
        liveSlice,
        deviceSlice,
    ) { pref, live, device ->
        val (monitoringPref, autoAirplane) = pref
        val (hasPerms, svc, shot) = live
        val (mitMsg, canSecure, air) = device
        val last = svc.lastResult ?: shot
        HomeUiState(
            hasPermissions = hasPerms,
            monitoring = svc.running || monitoringPref,
            autoAirplane = autoAirplane,
            canWriteSecureSettings = canSecure,
            airplaneOn = air,
            adbGrantHint = airplane.adbGrantHint(),
            statusLine = svc.statusLine.ifBlank {
                last?.let { "score ${it.aggregateScore}" }.orEmpty()
            },
            lastResult = last,
            mitigationMessage = mitMsg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refreshPermissions() {
        permissions.value = reader.hasRequiredPermissions()
        secureWrite.value = airplane.canWriteSecureSettings()
        airplaneOn.value = airplane.isAirplaneModeOn()
    }

    fun onPermissionsResult(@Suppress("UNUSED_PARAMETER") result: Map<String, Boolean>) {
        refreshPermissions()
    }

    fun setMonitoring(enabled: Boolean) {
        val ctx = getApplication<Application>()
        if (enabled) {
            if (!reader.hasRequiredPermissions()) {
                mitigationMessage.value = "Grant location + phone permissions first"
                return
            }
            CellMonitorService.start(ctx)
        } else {
            CellMonitorService.stop(ctx)
            viewModelScope.launch { prefs.setMonitoringEnabled(false) }
        }
    }

    fun setAutoAirplane(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoAirplaneOnCritical(enabled) }
    }

    fun refreshOnce() {
        viewModelScope.launch {
            refreshPermissions()
            val snap = reader.readSnapshot() ?: return@launch
            oneShot.value = engine.evaluate(snap)
        }
    }

    fun openAirplaneSettings() {
        getApplication<Application>().startActivity(airplane.openAirplaneSettingsIntent())
    }

    fun openMobileNetworkSettings() {
        getApplication<Application>().startActivity(airplane.openMobileNetworkSettingsIntent())
    }

    fun mitigateNow() {
        when (val r = airplane.enableAirplaneModeOrGuide()) {
            is AirplaneModeController.Result.Enabled ->
                mitigationMessage.value = "Airplane mode enabled"
            is AirplaneModeController.Result.AlreadyOn ->
                mitigationMessage.value = "Airplane mode already on"
            is AirplaneModeController.Result.OpenedSettings -> {
                getApplication<Application>().startActivity(r.intent)
                mitigationMessage.value =
                    "Opened system panel — apps cannot toggle airplane mode without WRITE_SECURE_SETTINGS"
            }
            is AirplaneModeController.Result.Failed ->
                mitigationMessage.value = "Failed: ${r.reason}"
        }
        refreshPermissions()
    }
}
