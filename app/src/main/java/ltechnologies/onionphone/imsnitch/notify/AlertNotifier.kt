package ltechnologies.onionphone.imsnitch.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ltechnologies.onionphone.imsnitch.MainActivity
import ltechnologies.onionphone.imsnitch.R
import ltechnologies.onionphone.imsnitch.detection.DetectionResult
import ltechnologies.onionphone.imsnitch.detection.ThreatSeverity

class AlertNotifier(private val context: Context) {

    init {
        ensureChannels()
    }

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MONITOR,
                context.getString(R.string.channel_monitor),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_monitor_desc)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                context.getString(R.string.channel_alert),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_alert_desc)
                enableVibration(true)
            },
        )
    }

    fun buildMonitorNotification(statusLine: String): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_tower)
            .setContentTitle(context.getString(R.string.monitor_notif_title))
            .setContentText(statusLine)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun notifyThreat(result: DetectionResult) {
        val top = result.findings.maxByOrNull { it.score } ?: return
        if (top.severity.ordinal < ThreatSeverity.HIGH.ordinal) return

        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_ALERT, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_tower)
            .setContentTitle(top.title)
            .setContentText(top.detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(top.detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ALERT, notif)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied
        }
    }

    companion object {
        const val CHANNEL_MONITOR = "monitor"
        const val CHANNEL_ALERT = "alert"
        const val NOTIF_MONITOR = 1001
        const val NOTIF_ALERT = 1002
        const val EXTRA_ALERT = "extra_alert"
    }
}
