package de.jworuna.webrtc_l4s.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import de.jworuna.webrtc_l4s.Tab

@Composable
fun BottomBar(
    current: Tab,
    onSelected: (Tab) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = current is Tab.User,
            onClick = { onSelected(Tab.User) },
            icon = { androidx.compose.material3.Icon(Icons.Default.Person, contentDescription = "User") },
            label = { Text("User") },
            colors = NavigationBarItemDefaults.colors()
        )
        NavigationBarItem(
            selected = current is Tab.Settings,
            onClick = { onSelected(Tab.Settings) },
            icon = { androidx.compose.material3.Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            colors = NavigationBarItemDefaults.colors()
        )
    }
}
