package de.jworuna.webrtc_l4s.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import de.jworuna.webrtc_l4s.AppViewModel
import de.jworuna.webrtc_l4s.Tab
import de.jworuna.webrtc_l4s.ui.theme.WebRTC_L4STheme

@Composable
fun AppRoot(
    requiredPermissions: List<String>,
    viewModel: AppViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = (LocalContext.current as? Activity)

    WebRTC_L4STheme {
        DisposableEffect(uiState.callActive, activity) {
            if (uiState.callActive) {
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onDispose {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        Scaffold(
            topBar = { TopBar() },
            bottomBar = { BottomBar(current = uiState.currentTab, onSelected = viewModel::onTabSelected) }
        ) { paddingValues ->
            PermissionGate(
                requiredPermissions = requiredPermissions,
                modifier = Modifier.padding(paddingValues),
                onPermissionsGranted = {
                    viewModel.refreshClientId()
                    viewModel.connectIfReady()
                },
                onAllGranted = {
                    when (uiState.currentTab) {
                        Tab.User -> UserScreen(
                            contacts = uiState.contacts,
                            status = uiState.connectionStatus,
                            errorMessage = uiState.errorMessage,
                            onCallClick = viewModel::startCall,
                            callActive = uiState.callActive,
                            onHangup = viewModel::hangup,
                            onSwitchCamera = viewModel::switchCamera,
                            onToggleMute = viewModel::toggleMute,
                            onToggleStats = viewModel::toggleStats,
                            isMuted = uiState.isMuted,
                            showStats = uiState.showStats,
                            stats = uiState.stats,
                            onRenderersReady = viewModel::setRenderers
                        )
                        Tab.Settings -> SettingsScreen(
                            state = uiState.settings,
                            onNameChange = viewModel::updateName,
                            onUrlChange = viewModel::updateUrl,
                            onLogApiUrlChange = viewModel::updateLogApiUrl,
                            onLogUsernameChange = viewModel::updateLogUsername,
                            onLogPasswordChange = viewModel::updateLogPassword,
                            onEnableApiLoggingChange = viewModel::updateEnableApiLogging,
                            onEnableLogcatLoggingChange = viewModel::updateEnableLogcatLogging,
                            onEnableLogLocationChange = viewModel::updateEnableLogLocation,
                            onLogIntervalChange = viewModel::updateLogIntervalMs,
                            onLogBatchChange = viewModel::updateLogBatchSize,
                            onVideoCodecChange = viewModel::updateVideoCodec,
                            onMuteOnStartChange = viewModel::updateMuteOnStart,
                            onMinBitrateChange = viewModel::updateMinBitrate,
                            onMaxBitrateChange = viewModel::updateMaxBitrate,
                            onUseScreamChange = viewModel::updateUseScream,
                            onUseSliceChange = viewModel::updateUseSlice,
                            onUseTrickleIceChange = viewModel::updateUseTrickleIce,
                            cameraOptions = uiState.cameraOptions,
                            onFrontFormatChange = viewModel::updateFrontFormat,
                            onBackFormatChange = viewModel::updateBackFormat,
                            onStunTurnUrlChange = viewModel::updateStunTurnUrl,
                            onStunTurnUsernameChange = viewModel::updateStunTurnUsername,
                            onStunTurnPasswordChange = viewModel::updateStunTurnPassword
                        )
                    }
                }
            )
        }
    }
}
