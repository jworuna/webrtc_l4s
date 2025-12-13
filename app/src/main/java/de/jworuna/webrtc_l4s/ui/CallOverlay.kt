package de.jworuna.webrtc_l4s.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import de.jworuna.webrtc_l4s.webrtc.CallStats
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallOverlay(
    onHangup: () -> Unit,
    onToggleMute: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleStats: () -> Unit,
    isMuted: Boolean,
    showStats: Boolean,
    stats: CallStats?,
    onRenderersReady: (SurfaceViewRenderer, SurfaceViewRenderer) -> Unit
) {
    val context = LocalContext.current
    val remoteRenderer = remember { SurfaceViewRenderer(context) }
    val localRenderer = remember { SurfaceViewRenderer(context) }

    DisposableEffect(Unit) {
        onRenderersReady(localRenderer, remoteRenderer)
        onDispose {
            localRenderer.release()
            remoteRenderer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Remote video full screen
        AndroidView(
            factory = { remoteRenderer.apply { setZOrderOnTop(false) } },
            modifier = Modifier.fillMaxSize()
        )

        if (showStats && stats != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color(0x66000000), shape = MaterialTheme.shapes.small)
                    .padding(8.dp)
            ) {
                Text("Connection: ${stats.connectivity}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                Text("RTT: ${stats.rttMs?.let { String.format("%.0f ms", it) } ?: "-"}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                Text("Video Loss: ${stats.videoPacketLossPercent?.let { String.format("%.1f%%", it) } ?: "-"}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                Text("Video Bitrate: ${stats.videoBitrateKbps?.let { String.format("%.2f mbps", it / 1000f) } ?: "-"}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                Text("Audio Loss: ${stats.audioPacketLossPercent?.let { String.format("%.1f%%", it) } ?: "-"}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                Text("Audio Bitrate: ${stats.audioBitrateKbps?.let { String.format("%.0f kbps", it) } ?: "-"}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                stats.screamEct1?.let { Text("ECT(1): ${String.format("%.0f", it)}", color = Color.White, style = MaterialTheme.typography.labelMedium) }
                stats.screamEctCe?.let { Text("ECN-CE: ${String.format("%.0f", it)}", color = Color.White, style = MaterialTheme.typography.labelMedium) }
                Text("ECN-CE: ${stats.screamCeInPercent?.let { String.format("%.2f%%", it) } ?: "-"}", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }

        // Local preview picture-in-picture
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .fillMaxWidth(0.35f)
                .aspectRatio(1f)
        ) {
            AndroidView(
                factory = { localRenderer.apply { setZOrderMediaOverlay(true); setMirror(true) } },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Controls overlaid on video
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleStats,
                modifier = Modifier.background(Color(0xFF4A5568), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Toggle stats",
                    tint = if (showStats) Color(0xFF80CBC4) else Color.White,
                    modifier = Modifier.padding(8.dp)
                )
            }
            IconButton(
                onClick = onSwitchCamera,
                modifier = Modifier.background(Color(0xFF4A5568), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Cameraswitch,
                    contentDescription = "Switch camera",
                    tint = Color.White,
                    modifier = Modifier.padding(8.dp)
                )
            }
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier.background(Color(0xFF4A5568), shape = CircleShape)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = "Mute",
                    tint = Color.White,
                    modifier = Modifier.padding(8.dp)
                )
            }
            IconButton(
                onClick = onHangup,
                modifier = Modifier.background(Color(0xFFE85C4A), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Hangup",
                    tint = Color.White,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
