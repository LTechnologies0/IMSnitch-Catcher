package ltechnologies.onionphone.imsnitch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ltechnologies.onionphone.imsnitch.R
import ltechnologies.onionphone.imsnitch.detection.ThreatSeverity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRequestPermissions: () -> Unit,
    onToggleMonitoring: (Boolean) -> Unit,
    onToggleAutoAirplane: (Boolean) -> Unit,
    onOpenAirplaneSettings: () -> Unit,
    onOpenMobileSettings: () -> Unit,
    onRefresh: () -> Unit,
    onMitigateNow: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroStatus(state)

            if (!state.hasPermissions) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.need_permissions),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onRequestPermissions) {
                        Text(text = stringResource(R.string.grant_permissions))
                    }
                }
            }

            ToggleRow(
                title = stringResource(R.string.monitor_toggle),
                subtitle = stringResource(R.string.monitor_toggle_desc),
                checked = state.monitoring,
                enabled = state.hasPermissions,
                onCheckedChange = onToggleMonitoring,
            )

            ToggleRow(
                title = stringResource(R.string.auto_airplane),
                subtitle = stringResource(R.string.auto_airplane_desc),
                checked = state.autoAirplane,
                enabled = true,
                onCheckedChange = onToggleAutoAirplane,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = if (state.canWriteSecureSettings) {
                                stringResource(R.string.secure_write_yes)
                            } else {
                                stringResource(R.string.secure_write_no)
                            },
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Security, contentDescription = null)
                    },
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = if (state.airplaneOn) {
                                stringResource(R.string.airplane_on)
                            } else {
                                stringResource(R.string.airplane_off)
                            },
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.AirplanemodeActive, contentDescription = null)
                    },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onMitigateNow) {
                    Icon(Icons.Default.AirplanemodeActive, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.mitigate_airplane))
                }
                FilledTonalButton(onClick = onOpenAirplaneSettings) {
                    Text(text = stringResource(R.string.open_airplane_settings))
                }
            }

            OutlinedButton(onClick = onOpenMobileSettings) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.open_mobile_settings))
            }

            if (!state.canWriteSecureSettings) {
                Text(
                    text = stringResource(R.string.adb_grant_hint, state.adbGrantHint),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }

            state.mitigationMessage?.let { msg ->
                Text(text = msg, color = MaterialTheme.colorScheme.secondary)
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.section_serving),
                style = MaterialTheme.typography.titleMedium,
            )
            ServingBlock(state)

            Text(
                text = stringResource(R.string.section_findings),
                style = MaterialTheme.typography.titleMedium,
            )
            FindingsBlock(state)

            Text(
                text = stringResource(R.string.disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun HeroStatus(state: HomeUiState) {
    val score = state.lastResult?.aggregateScore ?: 0
    val alert = state.lastResult?.isAlert == true
    val bg = when {
        alert -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
        score >= 40 -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(bg, MaterialTheme.shapes.large)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (alert) Icons.Default.Warning else Icons.Default.CellTower,
                contentDescription = null,
                tint = if (alert) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Column {
                Text(
                    text = if (alert) {
                        stringResource(R.string.status_alert)
                    } else {
                        stringResource(R.string.status_ok)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = state.statusLine.ifBlank { stringResource(R.string.status_idle) },
                )
            }
        }
        Text(
            text = stringResource(R.string.threat_score, score),
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ServingBlock(state: HomeUiState) {
    val serving = state.lastResult?.snapshot?.serving
    if (serving == null) {
        Text(
            text = stringResource(R.string.no_cell),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    val key = serving.key
    val lines = listOf(
        "RAT: ${key.generation}",
        "PLMN: ${key.mcc ?: "?"}-${key.mnc ?: "?"}",
        "TAC/LAC: ${key.lacOrTac ?: -1}",
        "Cell ID: ${key.cellId ?: -1}",
        "PCI: ${key.pci ?: -1}",
        "Signal: ${serving.dbm ?: "?"} dBm  level=${serving.level ?: "?"}",
        serving.bandHint.orEmpty(),
        "Neighbors: ${state.lastResult?.snapshot?.neighbors?.size ?: 0}",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                MaterialTheme.shapes.medium,
            )
            .padding(12.dp),
    ) {
        lines.filter { it.isNotBlank() }.forEach { line ->
            Text(
                text = line,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun FindingsBlock(state: HomeUiState) {
    val findings = state.lastResult?.findings.orEmpty()
    if (findings.isEmpty()) {
        Text(
            text = stringResource(R.string.no_findings),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        findings.forEach { finding ->
            val tint = when (finding.severity) {
                ThreatSeverity.CRITICAL, ThreatSeverity.HIGH -> MaterialTheme.colorScheme.error
                ThreatSeverity.MEDIUM -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurface
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(tint.copy(alpha = 0.1f), MaterialTheme.shapes.medium)
                    .padding(12.dp),
            ) {
                Text(text = finding.title, fontWeight = FontWeight.SemiBold, color = tint)
                Text(text = finding.detail, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "${finding.type} · ${finding.severity} · +${finding.score}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
