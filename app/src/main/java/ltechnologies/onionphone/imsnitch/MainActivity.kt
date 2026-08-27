package ltechnologies.onionphone.imsnitch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ltechnologies.onionphone.imsnitch.ui.HomeScreen
import ltechnologies.onionphone.imsnitch.ui.MainViewModel
import ltechnologies.onionphone.imsnitch.ui.theme.IMSnitchTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        vm.onPermissionsResult(result)
        if (result.values.all { it }) {
            // User may want monitoring after grant — leave toggle to them.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IMSnitchTheme {
                val state by vm.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    onRequestPermissions = ::requestNeededPermissions,
                    onToggleMonitoring = vm::setMonitoring,
                    onToggleAutoAirplane = vm::setAutoAirplane,
                    onOpenAirplaneSettings = vm::openAirplaneSettings,
                    onOpenMobileSettings = vm::openMobileNetworkSettings,
                    onRefresh = vm::refreshOnce,
                    onMitigateNow = vm::mitigateNow,
                )
            }
        }
        vm.refreshPermissions()
        vm.refreshOnce()
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermissions()
    }

    private fun requestNeededPermissions() {
        val needed = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (needed.isEmpty()) {
            vm.refreshPermissions()
        } else {
            permissionLauncher.launch(needed)
        }
    }
}
