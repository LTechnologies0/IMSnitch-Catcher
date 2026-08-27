package ltechnologies.onionphone.imsnitch

import android.app.Application
import ltechnologies.onionphone.imsnitch.notify.AlertNotifier

class ImSnitchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlertNotifier(this).ensureChannels()
    }
}
