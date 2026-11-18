package com.deckvault.app.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.deckvault.app.model.DiscoveredHost
import com.deckvault.app.model.SavedHost
import com.deckvault.app.peer.DeckVaultPeerService
import com.deckvault.app.peer.PeerServiceStatus
import com.deckvault.app.ui.theme.OledColors
import com.deckvault.app.ui.util.Formatters

private fun deviceIconFor(name: String, os: String): ImageVector {
    val o = os.lowercase()
    val n = name.lowercase()
    return when {
        o.contains("ios") || n.contains("iphone") || n.contains("ipad") -> Icons.Rounded.PhoneIphone
        o.contains("android") && (n.contains("tab") || n.contains("pad") || n.contains("ultra")) -> Icons.Rounded.TabletAndroid
        o.contains("android") -> Icons.Rounded.Smartphone
        n.contains("laptop") || n.contains("book") -> Icons.Rounded.Laptop
        else -> Icons.Rounded.Computer
    }
}

@Composable
fun HostsModalDialog(
    savedHosts: List<SavedHost>,
    activeHostId: String?,
    discoveredHosts: List<DiscoveredHost>,
    peerStatus: PeerServiceStatus,
    isScanningLan: Boolean,
    isPairing: Boolean,
    pairingError: String?,
    onScanLan: () -> Unit,
    onRotatePin: () -> Unit,
    onSelectHost: (SavedHost) -> Unit,
    onRemoveHost: (String) -> Unit,
    onPairHost: (ip: String, port: Int, pin: String, customLabel: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val hasAllFiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }

    var selectedDiscoveredId by remember(discoveredHosts) {
        mutableStateOf(
            discoveredHosts.firstOrNull { d -> savedHosts.none { it.hostId == d.hostId } }?.hostId
                ?: discoveredHosts.firstOrNull()?.hostId,
        )
    }
    var quickPinInput by remember { mutableStateOf("") }

    var showManualEntry by remember { mutableStateOf(false) }
    var ipInput by remember { mutableStateOf("") }
    var portInput by remember { mutableStateOf("48720") }
    var manualPinInput by remember { mutableStateOf("") }
    var labelInput by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = OledColors.PanelSurface,
            border = BorderStroke(1.dp, OledColors.HairlineBorder),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Devices & Peers", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }

                // Already Paired Devices
                if (savedHosts.isNotEmpty()) {
                    Text("Paired Devices (Tap to Switch)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    savedHosts.forEach { host ->
                        val active = host.hostId == activeHostId
                        Surface(
                            onClick = {
                                onSelectHost(host)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else OledColors.CardSurface,
                            border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else OledColors.HairlineBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp),
                            ) {
                                Icon(
                                    deviceIconFor(host.displayName, host.os),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(host.displayName, style = MaterialTheme.typography.titleMedium, color = OledColors.TextPrimary)
                                    Text("${host.os}  •  ${host.ip}:${host.port}", style = MaterialTheme.typography.bodySmall, color = OledColors.TextSecondary)
                                }
                                if (active) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Active", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { onRemoveHost(host.hostId) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove", tint = OledColors.TextSecondary)
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = OledColors.HairlineBorder)
                }

                // Auto-discovery on Wi-Fi with PIN-only Connect (No remembering IPs!)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Devices Found on Wi-Fi", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                        Text(
                            "Tap any PC, Laptop, Tablet, or Phone and enter only its PIN",
                            style = MaterialTheme.typography.labelSmall,
                            color = OledColors.TextSecondary,
                        )
                    }
                    OutlinedButton(onClick = onScanLan, enabled = !isScanningLan, shape = RoundedCornerShape(50)) {
                        if (isScanningLan) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(if (isScanningLan) "Scanning..." else "Scan Wi-Fi")
                    }
                }

                if (discoveredHosts.isEmpty() && !isScanningLan) {
                    Text(
                        "No nearby devices responded yet. Make sure DeckVault is open on your PC, Laptop, Fold, or Tablet on the same Wi-Fi, then tap Scan Wi-Fi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OledColors.TextSecondary,
                    )
                } else {
                    discoveredHosts.forEach { d ->
                        val primaryIp = d.ips.firstOrNull().orEmpty()
                        val alreadyPaired = savedHosts.any { it.hostId == d.hostId }
                        val expanded = selectedDiscoveredId == d.hostId

                        Surface(
                            onClick = {
                                selectedDiscoveredId = if (expanded) null else d.hostId
                                quickPinInput = ""
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = if (expanded) OledColors.ElevatedSurface else OledColors.CardSurface,
                            border = BorderStroke(
                                1.dp,
                                if (expanded) MaterialTheme.colorScheme.primary else OledColors.HairlineBorder,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(14.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Icon(
                                        deviceIconFor(d.deviceName, d.os),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(26.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(d.deviceName, style = MaterialTheme.typography.titleMedium, color = OledColors.TextPrimary)
                                        Text(
                                            buildString {
                                                append(d.os)
                                                append("  •  ")
                                                append(primaryIp)
                                                if (d.freeBytes > 0) {
                                                    append("  •  ${Formatters.formatBytes(d.freeBytes)} free")
                                                }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OledColors.TextSecondary,
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            selectedDiscoveredId = if (expanded) null else d.hostId
                                            quickPinInput = ""
                                        },
                                        shape = RoundedCornerShape(50),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Text(if (alreadyPaired) "Re-Pair" else "Connect")
                                    }
                                }

                                AnimatedVisibility(visible = expanded) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        OutlinedTextField(
                                            value = quickPinInput,
                                            onValueChange = { quickPinInput = it.filter(Char::isDigit).take(6) },
                                            label = { Text("Enter 6-Digit PIN") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Done,
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = {
                                                    if (!isPairing && primaryIp.isNotBlank() && quickPinInput.length >= 4) {
                                                        onPairHost(primaryIp, d.port, quickPinInput.trim(), d.deviceName)
                                                    }
                                                },
                                            ),
                                            modifier = Modifier.weight(1f),
                                        )
                                        Button(
                                            onClick = {
                                                onPairHost(primaryIp, d.port, quickPinInput.trim(), d.deviceName)
                                            },
                                            enabled = !isPairing && primaryIp.isNotBlank() && quickPinInput.length >= 4,
                                            shape = RoundedCornerShape(50),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = Color.Black,
                                            ),
                                            modifier = Modifier.height(50.dp),
                                        ) {
                                            if (isPairing) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                                            } else {
                                                Text("Pair")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (pairingError != null) {
                    Text(pairingError, style = MaterialTheme.typography.bodySmall, color = OledColors.DangerRed)
                }

                HorizontalDivider(color = OledColors.HairlineBorder)

                // This Device Peer Server Info
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = OledColors.CardSurface,
                    border = BorderStroke(1.dp, OledColors.HairlineBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
                        val ipStr = peerStatus.ips.joinToString(", ").ifBlank { "Starting..." }
                        val firstIp = peerStatus.ips.firstOrNull() ?: "<phone-ip>"
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (peerStatus.running) "This Device Sharing PIN: ${peerStatus.pin}" else "This Device Sharing Is Off",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (peerStatus.running) MaterialTheme.colorScheme.primary else OledColors.TextSecondary,
                                )
                                if (peerStatus.running) {
                                    Text(
                                        "Wi-Fi IP: $ipStr:${peerStatus.port}  •  Browser: http://$firstIp:${peerStatus.port}/",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OledColors.TextSecondary,
                                    )
                                }
                            }
                            if (peerStatus.running) {
                                OutlinedButton(
                                    onClick = onRotatePin,
                                    shape = RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text("New PIN")
                                }
                            }
                        }
                        if (!hasAllFiles && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            Uri.parse("package:${context.packageName}"),
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                },
                                shape = RoundedCornerShape(50),
                            ) {
                                Text("Grant All Files Access for Phone/Tablet Browsing")
                            }
                        }
                        if (!DeckVaultPeerService.isIgnoringBatteryOptimizations(context)) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(DeckVaultPeerService.batteryOptimizationIntent(context))
                                    }
                                },
                                shape = RoundedCornerShape(50),
                            ) {
                                Text("Keep Wi-Fi Fast (Unrestricted Battery)")
                            }
                        }
                    }
                }

                // Collapsible Manual IP Entry (Fallback)
                TextButton(
                    onClick = { showManualEntry = !showManualEntry },
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text(if (showManualEntry) "Hide Manual IP Entry" else "Enter IP Manually (Fallback)")
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (showManualEntry) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }

                AnimatedVisibility(visible = showManualEntry) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = ipInput,
                                onValueChange = { ipInput = it },
                                label = { Text("LAN IP") },
                                singleLine = true,
                                modifier = Modifier.weight(2f),
                            )
                            OutlinedTextField(
                                value = portInput,
                                onValueChange = { portInput = it.filter(Char::isDigit) },
                                label = { Text("Port") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = manualPinInput,
                                onValueChange = { manualPinInput = it.filter(Char::isDigit).take(6) },
                                label = { Text("6-Digit PIN") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = labelInput,
                                onValueChange = { labelInput = it },
                                label = { Text("Nickname") },
                                singleLine = true,
                                modifier = Modifier.weight(1.4f),
                            )
                        }
                        Button(
                            onClick = {
                                val p = portInput.toIntOrNull() ?: 48720
                                onPairHost(ipInput.trim(), p, manualPinInput.trim(), labelInput.trim())
                            },
                            enabled = !isPairing && ipInput.isNotBlank() && manualPinInput.length >= 4,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pair Manual IP")
                        }
                    }
                }
            }
        }
    }
}
