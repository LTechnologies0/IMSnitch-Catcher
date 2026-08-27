package ltechnologies.onionphone.imsnitch.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ltechnologies.onionphone.imsnitch.data.UserPreferences
import ltechnologies.onionphone.imsnitch.detection.DetectionEngine
import ltechnologies.onionphone.imsnitch.detection.DetectionResult
import ltechnologies.onionphone.imsnitch.detection.ThreatSeverity
import ltechnologies.onionphone.imsnitch.mitigation.AirplaneModeController
import ltechnologies.onionphone.imsnitch.notify.AlertNotifier
import ltechnologies.onionphone.imsnitch.telephony.CellularReader

class CellMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var reader: CellularReader
    private lateinit var engine: DetectionEngine
    private lateinit var notifier: AlertNotifier
    private lateinit var airplane: AirplaneModeController
    private lateinit var prefs: UserPreferences
    private var loopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        reader = CellularReader(this)
        engine = DetectionEngine()
        notifier = AlertNotifier(this)
        airplane = AirplaneModeController(this)
        prefs = UserPreferences(this)
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                return START_NOT_STICKY
            }
            else -> startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        val notif = notifier.buildMonitorNotification(getString(ltechnologies.onionphone.imsnitch.R.string.monitor_starting))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                AlertNotifier.NOTIF_MONITOR,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(AlertNotifier.NOTIF_MONITOR, notif)
        }

        scope.launch { prefs.setMonitoringEnabled(true) }
        loopJob?.cancel()
        loopJob = scope.launch {
            while (isActive) {
                pollOnce()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun pollOnce() {
        try {
            val snapshot = reader.readSnapshot()
            if (snapshot == null) {
                publish(
                    UiMonitorState(
                        running = true,
                        statusLine = getString(ltechnologies.onionphone.imsnitch.R.string.status_need_perms),
                        lastResult = null,
                    ),
                )
                updateFg(getString(ltechnologies.onionphone.imsnitch.R.string.status_need_perms))
                return
            }

            val result = engine.evaluate(snapshot)
            val status = buildStatus(result)
            publish(
                UiMonitorState(
                    running = true,
                    statusLine = status,
                    lastResult = result,
                ),
            )
            updateFg(status)

            if (result.isAlert) {
                val last = prefs.lastAlertAtMs.first()
                if (result.snapshot.timestampMs - last > ALERT_COOLDOWN_MS) {
                    notifier.notifyThreat(result)
                    prefs.setLastAlertAtMs(result.snapshot.timestampMs)
                    maybeAutoAirplane(result)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "poll failed", t)
            publish(
                UiMonitorState(
                    running = true,
                    statusLine = t.message ?: "error",
                    lastResult = _state.value.lastResult,
                ),
            )
        }
    }

    private suspend fun maybeAutoAirplane(result: DetectionResult) {
        val enabled = prefs.autoAirplaneOnCritical.first()
        if (!enabled) return
        val critical = result.findings.any { it.severity == ThreatSeverity.CRITICAL }
        if (!critical) return
        when (val r = airplane.enableAirplaneModeOrGuide()) {
            is AirplaneModeController.Result.Enabled,
            is AirplaneModeController.Result.AlreadyOn,
            -> Unit
            is AirplaneModeController.Result.OpenedSettings -> {
                r.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(r.intent)
            }
            is AirplaneModeController.Result.Failed ->
                Log.w(TAG, "auto airplane failed: ${r.reason}")
        }
    }

    private fun buildStatus(result: DetectionResult): String {
        val serving = result.snapshot.serving
        val gen = serving?.key?.generation?.name ?: "?"
        val dbm = serving?.dbm?.let { "$it dBm" } ?: "n/a"
        val score = result.aggregateScore
        val threat = result.findings.firstOrNull()?.type?.name ?: "OK"
        return "$gen · $dbm · score $score · $threat"
    }

    private fun updateFg(line: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(AlertNotifier.NOTIF_MONITOR, notifier.buildMonitorNotification(line))
    }

    private fun stopMonitoring() {
        loopJob?.cancel()
        loopJob = null
        scope.launch { prefs.setMonitoringEnabled(false) }
        publish(UiMonitorState(running = false, statusLine = "Stopped", lastResult = _state.value.lastResult))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        loopJob?.cancel()
        scope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    data class UiMonitorState(
        val running: Boolean = false,
        val statusLine: String = "",
        val lastResult: DetectionResult? = null,
    )

    companion object {
        private const val TAG = "CellMonitorService"
        private const val POLL_INTERVAL_MS = 15_000L
        private const val ALERT_COOLDOWN_MS = 60_000L
        const val ACTION_STOP = "ltechnologies.onionphone.imsnitch.STOP"

        private val _state = MutableStateFlow(UiMonitorState())
        val state: StateFlow<UiMonitorState> = _state.asStateFlow()

        @Volatile
        private var instance: CellMonitorService? = null

        private fun publish(s: UiMonitorState) {
            _state.value = s
        }

        fun start(context: Context) {
            val i = Intent(context, CellMonitorService::class.java)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, CellMonitorService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }
}
