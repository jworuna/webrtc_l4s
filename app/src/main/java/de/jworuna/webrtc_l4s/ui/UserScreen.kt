package de.jworuna.webrtc_l4s.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.jworuna.webrtc_l4s.Contact
import de.jworuna.webrtc_l4s.ConnectionStatus
import org.webrtc.SurfaceViewRenderer
import androidx.compose.ui.Alignment

@Composable
fun UserScreen(
    contacts: List<Contact>,
    status: ConnectionStatus,
    errorMessage: String?,
    onCallClick: (Contact) -> Unit,
    callActive: Boolean,
    onHangup: () -> Unit,
    onToggleMute: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleStats: () -> Unit,
    isMuted: Boolean,
    showStats: Boolean,
    stats: de.jworuna.webrtc_l4s.webrtc.CallStats?,
    onRenderersReady: (SurfaceViewRenderer, SurfaceViewRenderer) -> Unit
) {
    when {
        callActive -> CallOverlay(
            onHangup = onHangup,
            onToggleMute = onToggleMute,
            onSwitchCamera = onSwitchCamera,
            onToggleStats = onToggleStats,
            isMuted = isMuted,
            showStats = showStats,
            stats = stats,
            onRenderersReady = onRenderersReady
        )
        errorMessage != null -> CenterMessage(errorMessage)
        status == ConnectionStatus.Connecting -> CenterLoading()
        else -> ContactList(contacts = contacts, onCallClick = onCallClick)
    }
}

@Composable
private fun CenterMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun CenterLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ContactList(contacts: List<Contact>, onCallClick: (Contact) -> Unit) {
    val (online, offline) = contacts.partition { it.isOnline }
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionHeader("Online") }
            items(online) { ContactRow(it, Color(0xFF00C853), onCallClick) }
            if (online.isEmpty()) item { EmptyHint("Niemand online") }
            item { SectionHeader("Offline") }
            items(offline) { ContactRow(it, Color.Gray, onCallClick) }
            if (offline.isEmpty()) item { EmptyHint("Niemand offline") }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = Color.Gray
    )
}

@Composable
private fun ContactRow(contact: Contact, iconColor: Color, onCallClick: (Contact) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        RowWithIcon(contact, iconColor, onCallClick)
        Divider(modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun RowWithIcon(contact: Contact, iconColor: Color, onCallClick: (Contact) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
        ) {
            Text(contact.name, style = MaterialTheme.typography.titleLarge)
            Text(contact.clientId, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
        }
        IconButton(onClick = { onCallClick(contact) }) {
            Icon(Icons.Filled.Call, contentDescription = "Call", tint = iconColor)
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
}

