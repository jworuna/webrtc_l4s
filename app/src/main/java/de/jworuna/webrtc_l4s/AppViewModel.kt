package de.jworuna.webrtc_l4s

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import com.microsoft.signalr.HubException
import de.jworuna.webrtc_l4s.cellinfo.WifiChangeCallback
import de.jworuna.webrtc_l4s.cellinfo.WifiChangeListener
import de.jworuna.webrtc_l4s.data.SettingsState
import de.jworuna.webrtc_l4s.data.readSettings
import de.jworuna.webrtc_l4s.data.saveSettings
import de.jworuna.webrtc_l4s.slice.SliceNetwork
import de.jworuna.webrtc_l4s.slice.SliceNetworkRequest
import de.jworuna.webrtc_l4s.webrtc.CallStats
import de.jworuna.webrtc_l4s.webrtc.CameraOptions
import de.jworuna.webrtc_l4s.webrtc.WebRtcClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

sealed class Tab {
    data object User : Tab()
    data object Settings : Tab()
}

data class UiState(
    val currentTab: Tab = Tab.User,
    val settings: SettingsState = SettingsState(),
    val contacts: List<Contact> = emptyList(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.Idle,
    val errorMessage: String? = null,
    val callActive: Boolean = false,
    val callTargetId: String? = null,
    val isMuted: Boolean = false,
    val showStats: Boolean = false,
    val stats: CallStats? = null,
    val cameraOptions: CameraOptions = CameraOptions(emptyList(), emptyList())
)

data class Contact(
    val clientId: String,
    val name: String,
    val isOnline: Boolean
)

enum class ConnectionStatus { Idle, Connecting, Connected, Error }

private data class ClientDto(
    val clientId: String,
    val name: String,
    val isOnline: Boolean
)

private data class MessageDto(
    val from: String,
    val type: String,
    val payload: String
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val TAG = "AppViewModel"
    private val context = app.applicationContext
    private lateinit var sliceNetworkRequest: SliceNetworkRequest
    private val telephonyManager: TelephonyManager? =
        ContextCompat.getSystemService(context, TelephonyManager::class.java)
    private var hubConnection: HubConnection? = null
    private var hasLoadedSettings = false
    private val webRtc = WebRtcClient(context)
    private var statsJob: Job? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState
    private var wifiChangeListener: WifiChangeListener? = null
    private var hasWifi = false
    private val loadClientsDeferred = CompletableDeferred<Boolean>()
    private val wifiChangeCallback = object : WifiChangeCallback
    {
        override fun onChange(isWifi: Boolean) {
            hasWifi = isWifi
            if (hasLoadedSettings)
            {
                viewModelScope.launch {
                    context.readSettings().collect { settingsFromStore ->
                        if (!hasWifi)
                        {
                            if (settingsFromStore.useSlice && SliceNetwork.network == null)
                            {
                                sliceNetworkRequest = SliceNetworkRequest(context)
                                sliceNetworkRequest.requestSlice(10000)
                            }
                        } else {
                            if (settingsFromStore.useSlice && SliceNetwork.network != null)
                            {
                                sliceNetworkRequest.releaseSlice()
                            }
                        }

                        stopConnection()
                        delay(3000)
                        connectIfReady()
                        webRtc.rebuildWebRtcFactory(hasWifi)
                    }
                }
            }
        }
    }

    init {
        wifiChangeListener = WifiChangeListener(context, wifiChangeCallback)
        wifiChangeListener?.registerWifiChangeListener()
        webRtc.setIceFailureCallback { error ->
            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.Error,
                    errorMessage = error
                )
            }
        }

        viewModelScope.launch {
            val options = webRtc.getCameraOptions()
            context.readSettings().collect { settingsFromStore ->
                val isWifiOnLoad = wifiChangeListener?.isOnWifi()
                val freshClientId = fetchLine1Number().ifBlank { settingsFromStore.clientId }
                val newSettings = settingsFromStore.copy(clientId = freshClientId)
                if (newSettings.useSlice && SliceNetwork.network == null)
                {
                    if (!isWifiOnLoad!!)
                    {
                        sliceNetworkRequest = SliceNetworkRequest(context)
                        sliceNetworkRequest.requestSlice(10000)
                        delay(3000)
                    }
                }
                if (!newSettings.useSlice && SliceNetwork.network != null)
                {
                    sliceNetworkRequest.releaseSlice()
                }
                webRtc.setStunTurn(
                    newSettings.stunTurnServerUrl,
                    newSettings.stunTurnUsername,
                    newSettings.stunTurnPassword
                )
                webRtc.setUseScreamAndOrSlice(newSettings.useScream, newSettings.useSlice)
                webRtc.setUseTrickleIce(newSettings.useTrickleIce)
                webRtc.updateLoggingConfig(
                    newSettings.enableApiLogging,
                    newSettings.logApiUrl,
                    newSettings.logUsername,
                    newSettings.logPassword,
                    newSettings.enableLogcatLogging,
                    newSettings.enableLogLocation,
                    newSettings.logIntervalMs,
                    newSettings.logBatchSize,
                    newSettings.videoCodec
                )
                webRtc.updateBitrateConfig(
                    newSettings.minBitrateKbps,
                    newSettings.maxBitrateKbps
                )
                applyCameraFormats(newSettings, options)
                webRtc.rebuildWebRtcFactory(isWifiOnLoad!!)
                _uiState.update { it.copy(settings = newSettings, cameraOptions = options) }
                persistIfChanged(newSettings)
                hasLoadedSettings = true
                Log.d(TAG, "Settings Loaded")
                if (_uiState.value.currentTab is Tab.User) connectIfReady()
            }
        }
    }

    fun onTabSelected(tab: Tab) {
        _uiState.update { it.copy(currentTab = tab) }
        if (tab is Tab.User) connectIfReady()
    }

    fun updateName(name: String) {
        persist(_uiState.value.settings.copy(name = name))
    }

    fun updateUrl(url: String) {
        persist(_uiState.value.settings.copy(signalingUrl = url))
    }

    fun updateLogApiUrl(url: String) {
        persist(_uiState.value.settings.copy(logApiUrl = url))
    }

    fun updateLogUsername(username: String) {
        persist(_uiState.value.settings.copy(logUsername = username))
    }

    fun updateLogPassword(password: String) {
        persist(_uiState.value.settings.copy(logPassword = password))
    }

    fun updateEnableApiLogging(enabled: Boolean) {
        val current = _uiState.value.settings
        persist(current.copy(enableApiLogging = enabled, enableLogcatLogging = if (enabled) false else current.enableLogcatLogging))
    }

    fun updateStunTurnUrl(url: String) {
        persist(_uiState.value.settings.copy(stunTurnServerUrl = url))
        viewModelScope.launch {
            context.readSettings().collect { settingsFromStore ->
                webRtc.setStunTurn(url,
                    settingsFromStore.stunTurnUsername,
                    settingsFromStore.stunTurnPassword)
            }
        }
    }

    fun updateStunTurnUsername(username: String) {
        persist(_uiState.value.settings.copy(stunTurnUsername = username))
        viewModelScope.launch {
            context.readSettings().collect { settingsFromStore ->
                webRtc.setStunTurn(settingsFromStore.stunTurnServerUrl,
                    username,
                    settingsFromStore.stunTurnPassword)
            }
        }
    }

    fun updateStunTurnPassword(password: String) {
        persist(_uiState.value.settings.copy(stunTurnPassword = password))
        viewModelScope.launch {
            context.readSettings().collect { settingsFromStore ->
                webRtc.setStunTurn(settingsFromStore.stunTurnServerUrl,
                    settingsFromStore.stunTurnUsername,
                    password)
            }
        }
    }

    fun updateEnableLogLocation(enabled: Boolean) {
        persist(_uiState.value.settings.copy(enableLogLocation = enabled))
    }

    fun updateLogIntervalMs(value: String) {
        val interval = value.toIntOrNull() ?: 0
        persist(_uiState.value.settings.copy(logIntervalMs = interval))
    }

    fun updateLogBatchSize(value: String) {
        val batch = value.toIntOrNull() ?: 0
        persist(_uiState.value.settings.copy(logBatchSize = batch))
    }

    fun updateEnableLogcatLogging(enabled: Boolean) {
        val current = _uiState.value.settings
        persist(current.copy(enableLogcatLogging = enabled, enableApiLogging = if (enabled) false else current.enableApiLogging))
    }

    fun updateVideoCodec(value: String) {
        persist(_uiState.value.settings.copy(videoCodec = value))
    }

    fun updateMuteOnStart(enabled: Boolean) {
        persist(_uiState.value.settings.copy(muteOnStart = enabled))
    }

    fun updateMinBitrate(value: String) {
        val kbps = value.toIntOrNull() ?: 0
        persist(_uiState.value.settings.copy(minBitrateKbps = kbps))
    }

    fun updateMaxBitrate(value: String) {
        val kbps = value.toIntOrNull() ?: 0
        persist(_uiState.value.settings.copy(maxBitrateKbps = kbps))
    }

    fun updateFrontFormat(value: String) {
        val options = webRtc.getCameraOptions()
        val updated = _uiState.value.settings.copy(frontFormat = value)
        persist(updated)
        applyCameraFormats(updated, options)
        _uiState.update { it.copy(settings = updated, cameraOptions = options) }
    }

    fun updateBackFormat(value: String) {
        val options = webRtc.getCameraOptions()
        val updated = _uiState.value.settings.copy(backFormat = value)
        persist(updated)
        applyCameraFormats(updated, options)
        _uiState.update { it.copy(settings = updated, cameraOptions = options) }
    }

    private fun applyCameraFormats(settings: SettingsState, options: CameraOptions) {
        fun parse(value: String, list: List<org.webrtc.CameraEnumerationAndroid.CaptureFormat>): org.webrtc.CameraEnumerationAndroid.CaptureFormat? {
            return list.firstOrNull { fmt ->
                val label = "${fmt.width}x${fmt.height}@${fmt.framerate.max / 1000}"
                label == value
            }
        }
        val front = parse(settings.frontFormat, options.frontFormats)
        val back = parse(settings.backFormat, options.backFormats)
        webRtc.setPreferredFormats(front, back)
    }

    fun updateUseScream(enabled: Boolean) {
        persist(_uiState.value.settings.copy(useScream = enabled))
        webRtc.setUseScreamAndOrSlice(enabled, _uiState.value.settings.useSlice)
        webRtc.rebuildWebRtcFactory(hasWifi)
    }

    fun updateUseSlice(enabled: Boolean) {
        persist(_uiState.value.settings.copy(useSlice = enabled))
        webRtc.setUseScreamAndOrSlice(_uiState.value.settings.useScream, enabled)
        webRtc.rebuildWebRtcFactory(hasWifi)
    }

    fun updateUseTrickleIce(enabled: Boolean) {
        persist(_uiState.value.settings.copy(useTrickleIce = enabled))
        webRtc.setUseTrickleIce(enabled)
    }

    fun connectIfReady() {
        if (!hasLoadedSettings) return
        val settings = _uiState.value.settings
        if (settings.clientId.isBlank() || settings.name.isBlank() || !webRtc.isValidIceServerUrl(settings.stunTurnServerUrl)) {
            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.Error,
                    errorMessage = "Please add Name and a Stun Server URL, and maybe restart the app to generate ClientID in Settings."
                )
            }
            return
        }
        if (hubConnection?.connectionState != HubConnectionState.CONNECTED)
            connect(settings)
        else
            fetchClients()
    }

    fun startCall(target: Contact) {
        viewModelScope.launch {
            try {
                webRtc.createOffer { sdp ->
                    sendSignal(target.clientId, "offer", sdp)
                    _uiState.update { it.copy(callActive = true, callTargetId = target.clientId) }
                    applyMuteOnStartIfNeeded()
                    webRtc.setCallActive(true)
                    startStats()
                }
            } catch (e: Exception)
            {
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.Error,
                        errorMessage = e.message.toString()
                    )
                }
                hangup()
            }
        }
    }

    fun hangup() {
        val target = _uiState.value.callTargetId
        if (!target.isNullOrBlank()) sendSignal(target, "hangup", "")
        webRtc.hangup()
        webRtc.setCallActive(false)
        stopStats()
        _uiState.update { it.copy(callActive = false, callTargetId = null, isMuted = false, stats = null) }
        fetchClients()
        webRtc.rebuildWebRtcFactory(hasWifi)
    }

    fun toggleMute() {
        val muted = !_uiState.value.isMuted
        webRtc.setAudioMuted(muted)
        _uiState.update { it.copy(isMuted = muted) }
    }

    fun toggleStats() {
        val show = !_uiState.value.showStats
        _uiState.update { it.copy(showStats = show) }
        if (show && _uiState.value.callActive) startStats() else if (!show) _uiState.update { it.copy(stats = null) }
    }

    fun switchCamera() {
        webRtc.switchCamera()
    }

    fun setRenderers(local: org.webrtc.SurfaceViewRenderer, remote: org.webrtc.SurfaceViewRenderer) {
        webRtc.attachRenderers(local, remote)
        webRtc.startLocalPreview()
    }

    private fun persist(settings: SettingsState) {
        _uiState.update { it.copy(settings = settings) }
        viewModelScope.launch {
            context.saveSettings(settings.copy(clientId = settings.clientId.ifBlank { fetchLine1Number() }))
        }
        webRtc.updateLoggingConfig(
            settings.enableApiLogging,
            settings.logApiUrl,
            settings.logUsername,
            settings.logPassword,
            settings.enableLogcatLogging,
            settings.enableLogLocation,
            settings.logIntervalMs,
            settings.logBatchSize,
            settings.videoCodec
        )
        webRtc.updateBitrateConfig(
            settings.minBitrateKbps,
            settings.maxBitrateKbps,
        )
    }

    private fun applyMuteOnStartIfNeeded() {
        val shouldMute = _uiState.value.settings.muteOnStart
        if (shouldMute) {
            webRtc.setAudioMuted(true)
            _uiState.update { it.copy(isMuted = true) }
        }
    }

    private fun persistIfChanged(settings: SettingsState) {
        val current = _uiState.value.settings
        if (current != settings) {
            _uiState.update { it.copy(settings = settings) }
            viewModelScope.launch { context.saveSettings(settings) }
        }
    }

    fun refreshClientId() {
        if (!hasLoadedSettings) return
        val updatedClientId = fetchLine1Number()
        if (updatedClientId.isNotBlank()) {
            val newSettings = _uiState.value.settings.copy(clientId = updatedClientId)
            persist(newSettings)
            if (_uiState.value.connectionStatus == ConnectionStatus.Connected) connectIfReady()
        }
    }

    private fun fetchLine1Number(): String {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_NUMBERS
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
        return if (hasPermission) telephonyManager?.line1Number.orEmpty() else ""
    }

    private fun fetchClients()
    {
        Log.d(TAG, "Refetch client list")
        hubConnection?.send("GetClients")
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.Connecting,
                    errorMessage = null
                )
            }
            withContext(Dispatchers.IO) {
                loadClientsDeferred.await()
            }
        }
    }

    private fun connect(settings: SettingsState) {
        viewModelScope.launch {
            _uiState.update { it.copy(connectionStatus = ConnectionStatus.Connecting, errorMessage = null) }
            stopConnection()

            val url = buildUrl(settings.signalingUrl, settings.clientId, settings.name)
            val connection = HubConnectionBuilder.create(url).build()
            hubConnection = connection

            connection.on(
                "ClientList",
                { array: Array<ClientDto> -> handleClientList(array.toList()) },
                Array<ClientDto>::class.java
            )
            connection.on(
                "Message",
                { message: MessageDto -> handleMessage(message) },
                MessageDto::class.java
            )

            webRtc.onIceCandidate { candidate ->
                val iceJson = JSONObject()
                    .put("sdpMid", candidate.sdpMid)
                    .put("sdpMLineIndex", candidate.sdpMLineIndex)
                    .put("candidate", candidate.sdp)
                sendSignal(_uiState.value.callTargetId ?: "", "ice", iceJson.toString())
            }

            try {
                withContext(Dispatchers.IO) {
                    connection.start().blockingAwait()
                    loadClientsDeferred.await()
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.Error,
                        errorMessage = "Connection failed: ${t.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    private fun handleClientList(list: List<ClientDto>) {
        val selfId = _uiState.value.settings.clientId

        val newContactSet = list
            .filter { it.clientId != selfId }
            .mapTo(mutableSetOf()) { Contact(it.clientId, it.name, it.isOnline) }

        _uiState.update { currentState ->
            if (currentState.contacts.toSet() == newContactSet) {
                currentState
            } else {
                currentState.copy(contacts = newContactSet.toList())
            }
        }

        loadClientsDeferred.complete(true)
        _uiState.update { it.copy(connectionStatus = ConnectionStatus.Connected, errorMessage = null) }
    }

    private fun handleMessage(message: MessageDto) {
        when (message.type.lowercase()) {
            "answer" -> {
                webRtc.handleRemoteAnswer(message.payload)
                _uiState.update { it.copy(callActive = true, callTargetId = message.from) }
                applyMuteOnStartIfNeeded()
                webRtc.setCallActive(true)
                startStats()
            }
            "offer" -> {
                try {
                    webRtc.handleRemoteOffer(message.payload) { answerSdp ->
                        sendSignal(message.from, "answer", answerSdp)
                        _uiState.update { it.copy(callActive = true, callTargetId = message.from) }
                        applyMuteOnStartIfNeeded()
                        webRtc.setCallActive(true)
                        startStats()
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            connectionStatus = ConnectionStatus.Error,
                            errorMessage = e.message.toString()
                        )
                    }
                    hangup()
                }
            }
            "ice" -> {
                runCatching {
                    val json = JSONObject(message.payload)
                    val ice = org.webrtc.IceCandidate(
                        json.getString("sdpMid"),
                        json.getInt("sdpMLineIndex"),
                        json.getString("candidate")
                    )
                    webRtc.addIceCandidate(ice)
                }
            }
            "hangup" -> {
                webRtc.hangup()
                stopStats()
                webRtc.setCallActive(false)
                _uiState.update { it.copy(callActive = false, callTargetId = null, isMuted = false, stats = null) }
            }
        }
    }

    private fun sendSignal(targetId: String, type: String, payload: String) {
        try {
            hubConnection?.send("Message", targetId, type, payload)
        } catch (_: HubException) {
            _uiState.update { it.copy(errorMessage = "Can't send SignalR message.") }
        }
    }

    private fun buildUrl(baseUrl: String, clientId: String, name: String): String {
        val sanitized = baseUrl.trimEnd('/')
        val encodedClient = URLEncoder.encode(clientId, Charsets.UTF_8)
        val encodedName = URLEncoder.encode(name, Charsets.UTF_8)
        val uri = URI(sanitized)
        val path = uri.toString()
        val queryPrefix = if (uri.query.isNullOrBlank()) "?" else "&"
        return "$path${queryPrefix}clientId=$encodedClient&name=$encodedName"
    }

    private fun startStats() {
        if (!_uiState.value.showStats) return
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            while (_uiState.value.callActive) {
                webRtc.collectStats()?.let { stats -> _uiState.update { it.copy(stats = stats) } }
                delay(1500)
            }
        }
    }

    private fun stopStats() {
        statsJob?.cancel()
        statsJob = null
    }

    private fun stopConnection() {
        hubConnection?.let { conn ->
            if (conn.connectionState == HubConnectionState.CONNECTED ||
                conn.connectionState == HubConnectionState.CONNECTING
            ) {
                conn.stop()
            }
        }
        hubConnection = null
    }

    override fun onCleared() {
        wifiChangeListener?.unRegisterWifiChangeListener()
        stopConnection()
        webRtc.hangup()
        webRtc.setCallActive(false)
        stopStats()
        sliceNetworkRequest.releaseSlice()
        webRtc.dispose()
        super.onCleared()
    }
}
