package ltechnologies.onionphone.imsnitch.mitigation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * Airplane-mode mitigation.
 *
 * On modern Android, [Settings.Global.AIRPLANE_MODE_ON] is a secure setting.
 * Ordinary apps cannot toggle it. Options we support:
 *
 * 1. If `WRITE_SECURE_SETTINGS` was granted via
 *    `adb shell pm grant … WRITE_SECURE_SETTINGS` (or device-owner / system),
 *    write the global setting and broadcast the change.
 * 2. Otherwise open the system Airplane Mode / Internet connectivity panel
 *    so the user can flip it in one tap.
 */
class AirplaneModeController(private val context: Context) {

    sealed class Result {
        data object Enabled : Result()
        data object AlreadyOn : Result()
        data class OpenedSettings(val intent: Intent) : Result()
        data class Failed(val reason: String) : Result()
    }

    fun isAirplaneModeOn(): Boolean =
        Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0,
        ) != 0

    fun canWriteSecureSettings(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_SECURE_SETTINGS,
        ) == PackageManager.PERMISSION_GRANTED

    fun enableAirplaneModeOrGuide(): Result {
        if (isAirplaneModeOn()) return Result.AlreadyOn

        if (canWriteSecureSettings()) {
            return try {
                val ok = Settings.Global.putInt(
                    context.contentResolver,
                    Settings.Global.AIRPLANE_MODE_ON,
                    1,
                )
                if (!ok) {
                    return Result.Failed("Settings.Global.putInt returned false")
                }
                val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                    .putExtra("state", true)
                    .addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING)
                context.sendBroadcast(intent)
                Result.Enabled
            } catch (se: SecurityException) {
                Log.w(TAG, "WRITE_SECURE_SETTINGS present but denied at runtime", se)
                openSettingsFallback()
            } catch (t: Throwable) {
                Log.e(TAG, "Airplane toggle failed", t)
                Result.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
        return openSettingsFallback()
    }

    fun openAirplaneSettingsIntent(): Intent {
        // Prefer the one-tap Internet panel when available (API 29+).
        val panel = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (panel.resolveActivity(context.packageManager) != null) {
            return panel
        }
        return Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Deep-link to mobile network settings (2G toggle lives here on Android 12+). */
    fun openMobileNetworkSettingsIntent(): Intent {
        val intent = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) != null) return intent
        return Intent(Settings.ACTION_WIRELESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun adbGrantHint(): String =
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    private fun openSettingsFallback(): Result.OpenedSettings =
        Result.OpenedSettings(openAirplaneSettingsIntent())

    companion object {
        private const val TAG = "AirplaneMode"
    }
}

/** Optional helper for opening app notification settings. */
fun Context.openAppNotificationSettings(): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .also {
            if (it.resolveActivity(packageManager) == null) {
                it.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                it.data = "package:$packageName".toUri()
            }
        }
