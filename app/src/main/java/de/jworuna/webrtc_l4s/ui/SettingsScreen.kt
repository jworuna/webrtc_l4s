package de.jworuna.webrtc_l4s.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.jworuna.webrtc_l4s.data.SettingsState
import de.jworuna.webrtc_l4s.webrtc.CameraOptions

@Composable
fun SettingsScreen(
    state: SettingsState,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onLogApiUrlChange: (String) -> Unit,
    onLogUsernameChange: (String) -> Unit,
    onLogPasswordChange: (String) -> Unit,
    onEnableApiLoggingChange: (Boolean) -> Unit,
    onEnableLogcatLoggingChange: (Boolean) -> Unit,
    onEnableLogLocationChange: (Boolean) -> Unit,
    onLogIntervalChange: (String) -> Unit,
    onLogBatchChange: (String) -> Unit,
    onVideoCodecChange: (String) -> Unit,
    onMuteOnStartChange: (Boolean) -> Unit,
    onMinBitrateChange: (String) -> Unit,
    onMaxBitrateChange: (String) -> Unit,
    onUseScreamChange: (Boolean) -> Unit,
    onUseSliceChange: (Boolean) -> Unit,
    onUseTrickleIceChange: (Boolean) -> Unit,
    cameraOptions: CameraOptions,
    onFrontFormatChange: (String) -> Unit,
    onBackFormatChange: (String) -> Unit,
    onStunTurnUrlChange: (String) -> Unit,
    onStunTurnUsernameChange: (String) -> Unit,
    onStunTurnPasswordChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.clientId,
            onValueChange = {},
            label = { Text("ClientID") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            enabled = false
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.signalingUrl,
            onValueChange = onUrlChange,
            label = { Text("Signaling URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        Spacer(Modifier.height(24.dp))
        Text("Logging", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.logApiUrl,
            onValueChange = onLogApiUrlChange,
            label = { Text("Log API URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.logUsername,
            onValueChange = onLogUsernameChange,
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.logPassword,
            onValueChange = onLogPasswordChange,
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Checkbox(checked = state.enableApiLogging, onCheckedChange = onEnableApiLoggingChange)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Enable API Logging")
                Text(
                    "Send logs to the configured API endpoint",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Checkbox(checked = state.enableLogcatLogging, onCheckedChange = onEnableLogcatLoggingChange)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Enable LogCat logging")
                Text(
                    "Log to Android logcat instead of API",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Checkbox(checked = state.enableLogLocation, onCheckedChange = onEnableLogLocationChange)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Include location in logs")
                Text(
                    "Disable to skip GPS updates in logging",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.logIntervalMs.toString(),
            onValueChange = onLogIntervalChange,
            label = { Text("Log interval (ms)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.logBatchSize.toString(),
            onValueChange = onLogBatchChange,
            label = { Text("Log batch size") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(24.dp))
        Text("WebRTC", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.stunTurnServerUrl,
            onValueChange = onStunTurnUrlChange,
            label = { Text("Stun/Turn URL stun: or turn:") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.stunTurnUsername,
            onValueChange = onStunTurnUsernameChange,
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.stunTurnPassword,
            onValueChange = onStunTurnPasswordChange,
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Checkbox(checked = state.useScream, onCheckedChange = onUseScreamChange)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("SCReAM congestion control")
                Text(
                    "FieldTrial: RFC8888 and WebRTC-Bwe-ScreamV2",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Checkbox(checked = state.useTrickleIce, onCheckedChange = onUseTrickleIceChange)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Trickle ICE")
                Text(
                    "Send Ice continuously (on) or all after gathering (off)",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Checkbox(checked = state.useSlice, onCheckedChange = onUseSliceChange)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Use Network Slicing")
                Text(
                    "5G LowLatency Slice (if available)",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Checkbox(checked = state.muteOnStart, onCheckedChange = onMuteOnStartChange)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Mute on start")
                Text(
                    "Start calls muted by default",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = state.minBitrateKbps.toString(),
            onValueChange = onMinBitrateChange,
            label = { Text("Min bitrate (kbps)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.maxBitrateKbps.toString(),
            onValueChange = onMaxBitrateChange,
            label = { Text("Max bitrate (kbps)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(24.dp))
        Text("Video Codec", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        SimpleDropdown(
            label = "Codec",
            selected = state.videoCodec,
            options = listOf("All Codecs", "VP8", "VP9", "H264"),
            onSelected = onVideoCodecChange
        )
        Spacer(Modifier.height(24.dp))
        Text("Camera", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        FormatDropdown(
            label = "Front-Facing",
            selected = state.frontFormat,
            options = cameraOptions.frontFormats.map { fmt -> "${fmt.width}x${fmt.height}@${fmt.framerate.max / 1000}" },
            onSelected = onFrontFormatChange
        )
        Spacer(Modifier.height(12.dp))
        FormatDropdown(
            label = "Back-Facing",
            selected = state.backFormat,
            options = cameraOptions.backFormats.map { fmt -> "${fmt.width}x${fmt.height}@${fmt.framerate.max / 1000}" },
            onSelected = onBackFormatChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    val expanded = remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded.value,
        onExpandedChange = { expanded.value = !expanded.value }
    ) {
        TextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value) },
            colors = ExposedDropdownMenuDefaults.textFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded.value = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    val expanded = remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded.value,
        onExpandedChange = { expanded.value = !expanded.value }
    ) {
        TextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value) },
            colors = ExposedDropdownMenuDefaults.textFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded.value = false
                    }
                )
            }
        }
    }
}
