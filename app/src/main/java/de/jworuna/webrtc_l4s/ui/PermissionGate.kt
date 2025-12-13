package de.jworuna.webrtc_l4s.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionGate(
    requiredPermissions: List<String>,
    modifier: Modifier = Modifier,
    onPermissionsGranted: () -> Unit = {},
    onAllGranted: @Composable () -> Unit
) {
    val permissionsState = rememberMultiplePermissionsState(requiredPermissions)

    LaunchedEffect(Unit) {
        permissionsState.launchMultiplePermissionRequest()
    }

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) onPermissionsGranted()
    }

    if (permissionsState.allPermissionsGranted) {
        Box(modifier = modifier.fillMaxSize()) { onAllGranted() }
    } else {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Bitte allen Berechtigungen zustimmen, um die App zu nutzen.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                    Text("Erneut fragen")
                }
            }
        }
    }
}
