package ltechnologies.onionphone.imsnitch.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "imsnitch_prefs")

class UserPreferences(private val context: Context) {

    val monitoringEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_MONITORING] ?: false
    }

    val autoAirplaneOnCritical: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_AIRPLANE] ?: false
    }

    val lastAlertAtMs: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_ALERT] ?: 0L
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MONITORING] = enabled }
    }

    suspend fun setAutoAirplaneOnCritical(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_AIRPLANE] = enabled }
    }

    suspend fun setLastAlertAtMs(ts: Long) {
        context.dataStore.edit { it[KEY_LAST_ALERT] = ts }
    }

    companion object {
        private val KEY_MONITORING = booleanPreferencesKey("monitoring_enabled")
        private val KEY_AUTO_AIRPLANE = booleanPreferencesKey("auto_airplane_critical")
        private val KEY_LAST_ALERT = longPreferencesKey("last_alert_at")
    }
}
