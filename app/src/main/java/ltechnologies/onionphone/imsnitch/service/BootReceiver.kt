package ltechnologies.onionphone.imsnitch.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ltechnologies.onionphone.imsnitch.data.UserPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val enabled = runBlocking {
            UserPreferences(context).monitoringEnabled.first()
        }
        if (enabled) {
            CellMonitorService.start(context)
        }
    }
}
