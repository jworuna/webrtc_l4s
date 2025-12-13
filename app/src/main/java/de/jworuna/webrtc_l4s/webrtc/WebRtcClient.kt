package de.jworuna.webrtc_l4s.webrtc

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.net.ConnectivityManager
import android.util.Log
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Observer
import de.jworuna.webrtc_l4s.cellinfo.CellInfoProvider
import de.jworuna.webrtc_l4s.cellinfo.CurrentCellInfo
import de.jworuna.webrtc_l4s.measurement.GrafanaMeasurement
import de.jworuna.webrtc_l4s.measurement.IMeasurementCallback
import de.jworuna.webrtc_l4s.measurement.LogcatMeasurement
import de.jworuna.webrtc_l4s.measurement.MeasurementItem
import de.jworuna.webrtc_l4s.slice.SliceNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.PeerConnectionFactory.InitializationOptions
import org.webrtc.RTCStatsCollectorCallback
import org.webrtc.RTCStatsReport
import org.webrtc.CameraEnumerationAndroid
import org.webrtc.CandidatePairChangeEvent
import org.webrtc.IceCandidate
import org.webrtc.Logging
import org.webrtc.NetworkMonitor
import org.webrtc.RtpParameters
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class WebRtcClient(private val context: Context) {

    private val TAG = "de.jworuna.webrtc_l4s.webrtc.WebRtcClient"
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val eglBase: EglBase = EglBase.create()
    private var peerConnectionFactory: PeerConnectionFactory? = null

    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var iceCallback: ((IceCandidate) -> Unit)? = null
    private var currentCameraName: String? = null
    private var lastVideoBytes: Double? = null
    private var lastAudioBytes: Double? = null
    private var lastTimestampUs: Double? = null
    private var useScream: Boolean = false
    private var useSlice: Boolean = false
    private var lastScreamEct1: Double? = null
    private var lastScreamEctCe: Double? = null
    private var desiredFrontFormat: CameraEnumerationAndroid.CaptureFormat? = null
    private var desiredBackFormat: CameraEnumerationAndroid.CaptureFormat? = null
    private val unwantedCandidates = mutableListOf<IceCandidate>()
    private var sliceIps = mutableListOf<String>()
    private var nonSliceIps = mutableListOf<String>()
    private var useTrickleIce: Boolean = true
    private var pendingLocalSdpCallback: (() -> Unit)? = null
    private var cellInfoProvider: CellInfoProvider? = null
    private val cellInfoObserver = Observer<CurrentCellInfo> {}
    private var measurementCallback: IMeasurementCallback = LogcatMeasurement()
    private var loggingEnabled: Boolean = false
    private var callActive: Boolean = false
    private val measurementBuffer = mutableListOf<MeasurementItem>()
    private var measurementJob: Job? = null
    private val measurementScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var measurementLastVideoBytes: Double? = null
    private var measurementLastTimestampUs: Double? = null
    private var measurementLastVideoLost: Double? = null
    private var measurementLastEct1: Double? = null
    private var measurementLastEctCe: Double? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var logLocationEnabled: Boolean = true
    private var logIntervalMs: Int = 50
    private var logBatchSize: Int = 200
    private var preferredVideoCodec: String = "All Codecs"
    private var previousVoiceVolume: Int? = null
    private var previousCommunicationDevice: AudioDeviceInfo? = null
    private var minBitrateKbps: Long = 300
    private var maxBitrateKbps: Long = 20_000
    private var stunTurnUrl: String = ""
    private var stunTurnUsername: String = ""
    private var stunTurnPassword: String = ""
    private var isWifi: Boolean = false
    private var iceFailureCallback: ((String) -> Unit)? = null

    fun setIceFailureCallback(callback: ((String) -> Unit))
    {
        iceFailureCallback = callback
    }

    fun setUseScreamAndOrSlice(shouldUseScream: Boolean, shouldUseSlice: Boolean) {
        if (useScream == shouldUseScream && useSlice == shouldUseSlice) return
        useScream = shouldUseScream
        useSlice = shouldUseSlice
    }

    fun setUseTrickleIce(enabled: Boolean) {
        useTrickleIce = enabled
    }

    fun setStunTurn(inStunTurnUrl: String, inStunTurnUsername: String, inStunTurnPassword: String) {
        stunTurnUrl = inStunTurnUrl
        stunTurnUsername = inStunTurnUsername
        stunTurnPassword = inStunTurnPassword
    }

    fun updateLoggingConfig(
        apiLoggingEnabled: Boolean,
        url: String,
        username: String,
        password: String,
        logcatLoggingEnabled: Boolean,
        logLocationEnabled: Boolean,
        logIntervalMs: Int,
        logBatchSize: Int,
        videoCodec: String
    ) {
        val useApi = apiLoggingEnabled && url.isNotBlank()
        val useLogcat = logcatLoggingEnabled
        loggingEnabled = useApi || useLogcat
        this.logLocationEnabled = logLocationEnabled
        this.logIntervalMs = logIntervalMs.coerceAtLeast(10)
        this.logBatchSize = logBatchSize.coerceAtLeast(1)
        setPreferredVideoCodec(videoCodec)
        swapMeasurementCallback(useLogcat)
        if (loggingEnabled) {
            if (useApi) measurementCallback.setLogUrlAndCredentials(url, username, password)
            startMeasurementLoopIfNeeded()
        } else {
            stopMeasurementLoop()
        }
    }

    fun updateBitrateConfig(minKbps: Int, maxKbps: Int) {
        minBitrateKbps = (minKbps.coerceAtLeast(0).toLong())
        maxBitrateKbps = (maxKbps.coerceAtLeast(minKbps).toLong())
    }

    fun setPreferredVideoCodec(codec: String) {
        preferredVideoCodec = codec
    }

    private fun swapMeasurementCallback(useLogcat: Boolean) {
        val currentlyLogcat = measurementCallback is LogcatMeasurement
        if (useLogcat == currentlyLogcat) return
        measurementCallback.close()
        measurementCallback = if (useLogcat) LogcatMeasurement() else GrafanaMeasurement()
    }

    fun setCallActive(active: Boolean) {
        callActive = active
        if (callActive) {
            startMeasurementLoopIfNeeded()
        } else {
            stopMeasurementLoop()
        }
    }

    private fun ensureCellInfoObservation() {
        mainHandler.post {
            if (cellInfoProvider == null) cellInfoProvider = CellInfoProvider(context)
            cellInfoProvider?.currentCellInfo?.let { liveData ->
                if (!liveData.hasObservers()) {
                    liveData.observeForever(cellInfoObserver)
                }
            }
            cellInfoProvider?.setLocationEnabled(logLocationEnabled)
        }
    }

    private fun stopCellInfoObservation() {
        mainHandler.post {
            cellInfoProvider?.currentCellInfo?.removeObserver(cellInfoObserver)
            cellInfoProvider?.setLocationEnabled(false)
            cellInfoProvider?.clear()
            cellInfoProvider = null
        }
    }

    fun rebuildWebRtcFactory(hasWifi: Boolean)
    {
        isWifi = hasWifi
        if (isWifi)
            Log.d(TAG, "Wifi is active, ignore cellular and slicing.")
        if (peerConnection == null)
            rebuildFactory()
    }

    fun setupSlice()
    {
        if (!isWifi)
        {
            Log.d(TAG, "useSlice = $useSlice, SliceNetwork: ${SliceNetwork.network != null}")
            if (useSlice && SliceNetwork.network != null)
            {
                connectivityManager.bindProcessToNetwork(SliceNetwork.network)
                sliceIps.add(SliceNetwork.ipv6!!)
                if (SliceNetwork.ipv4 != null)
                    sliceIps.add(SliceNetwork.ipv4!!)

                Log.d(TAG, "Slice is used:")
                Log.d(TAG,
                    "SliceState: ${SliceNetwork.sliceState}, " +
                            "SliceInterface: ${SliceNetwork.sliceInterface}, " +
                            "SliceIPv6: ${SliceNetwork.ipv6}, SliceIPv4: ${SliceNetwork.ipv4}")

                NetworkMonitor.getInstance().setNetworkChangeDetectorFactory(
                    CustomNetworkChangeDetectorFactory(SliceNetwork.network!!)
                )
            }
        }
    }

    private fun rebuildFactory() {
        Log.d(TAG, "rebuildFactory")
        Log.d(TAG, "UseSlice: $useSlice overriteByWifi: $isWifi")
        sliceIps.clear()
        nonSliceIps.clear()
        unwantedCandidates.clear()
        setupSlice()
        collectNonSliceIps()

        peerConnection?.close()
        peerConnection = null
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null

        PeerConnectionFactory.initialize(
            InitializationOptions.builder(context)
                .setFieldTrials(currentFieldTrials())
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO)
        Logging.enableLogTimeStamps()

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    private fun currentFieldTrials(): String {
        return if (useScream) {
            "WebRTC-RFC8888CongestionControlFeedback/Enabled,offer:true/WebRTC-Bwe-ScreamV2/Enabled/"
        } else {
            ""
        }
    }

    fun attachRenderers(local: SurfaceViewRenderer, remote: SurfaceViewRenderer) {
        localRenderer = local
        remoteRenderer = remote
        local.init(eglBase.eglBaseContext, null)
        remote.init(eglBase.eglBaseContext, null)
        local.setMirror(true)
        localVideoTrack?.let { track -> localRenderer?.let { track.addSink(it) } }
        remoteVideoTrack?.let { track -> remoteRenderer?.let { track.addSink(it) } }
    }

    fun startLocalPreview() {
        if (peerConnectionFactory == null) rebuildFactory()
        if (videoCapturer == null) {
            videoCapturer = createCameraCapturer() ?: return
            currentCameraName = (videoCapturer as? org.webrtc.CameraVideoCapturer)?.let { it.javaClass.simpleName }
            videoSource = peerConnectionFactory!!.createVideoSource(videoCapturer!!.isScreencast)
            videoCapturer!!.initialize(
                org.webrtc.SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext),
                context,
                videoSource!!.capturerObserver
            )
            val format = selectFormatForCurrentCamera() ?: CameraEnumerationAndroid.CaptureFormat(640, 480, 30_000, 30_000)
            videoCapturer!!.startCapture(format.width, format.height, format.framerate.max / 1000)

            localVideoTrack = peerConnectionFactory!!.createVideoTrack(LOCAL_VIDEO_TRACK_ID, videoSource)
            audioSource = peerConnectionFactory!!.createAudioSource(MediaConstraints())
            localAudioTrack = peerConnectionFactory!!.createAudioTrack(LOCAL_AUDIO_TRACK_ID, audioSource)
        }
        localRenderer?.let { renderer -> localVideoTrack?.addSink(renderer) }

        applyBitrateToSenders()
    }

    private fun applyBitrateToSenders() {
        val pc = peerConnection ?: return
        val minBps = (minBitrateKbps * 1000).coerceAtLeast(0)
        val maxBps = (maxBitrateKbps * 1000).coerceAtLeast(minBps)
        pc.senders.forEach { sender ->
            if (sender.track()?.kind() == "video") {
                val params = sender.parameters
                if (params.encodings.isEmpty()) {
                    params.encodings = emptyList<RtpParameters.Encoding>()
                }
                params.encodings[0].minBitrateBps = minBps.toInt()
                params.encodings[0].maxBitrateBps = maxBps.toInt()
                params.encodings[0].bitratePriority = 1.0
                params.encodings[0].numTemporalLayers = params.encodings[0].numTemporalLayers
                val success = sender.setParameters(params)
                Log.d(TAG, "Applied bitrate params success=$success min=${params.encodings[0].minBitrateBps} max=${params.encodings[0].maxBitrateBps}")
            }
        }
    }

    fun switchCamera() {
        val capturer = videoCapturer as? org.webrtc.CameraVideoCapturer ?: return
        capturer.switchCamera(null)
    }

    fun createOffer(onSdp: (String) -> Unit) {
        ensurePeerConnection()
        startLocalPreview()
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                val filtered = desc?.let { transformSdp(it) }
                peerConnection?.setLocalDescription(SimpleSdpObserver(), filtered)
                if (useTrickleIce) {
                    onSdp(filtered!!.description)
                } else {
                    waitForIceGatheringThen { onSdp(peerConnection?.localDescription?.description ?: filtered!!.description) }
                }
            }
        }, constraints)
    }

    fun handleRemoteOffer(sdp: String, onAnswer: (String) -> Unit) {
        Log.d(TAG, "Answer:\n$sdp")
        ensurePeerConnection()
        startLocalPreview()
        val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
        peerConnection?.setRemoteDescription(SimpleSdpObserver(), offer)
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                if (p0 == null) return
                val filtered = transformSdp(p0)
                peerConnection?.setLocalDescription(SimpleSdpObserver(), filtered)
                if (useTrickleIce) {
                    onAnswer(filtered.description)
                } else {
                    waitForIceGatheringThen { onAnswer(peerConnection?.localDescription?.description ?: filtered.description) }
                }
            }
        }, constraints)
    }

    fun handleRemoteAnswer(sdp: String) {
        val answer = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection?.setRemoteDescription(SimpleSdpObserver(), answer)
    }

    suspend fun collectStats(): CallStats? = suspendCoroutine { cont ->
        val pc = peerConnection ?: return@suspendCoroutine cont.resume(null)
        pc.getStats { report ->
            cont.resume(extractStats(report))
        }
    }

    private fun startMeasurementLoopIfNeeded() {
        if (!loggingEnabled || !callActive) return
        if (measurementJob?.isActive == true) return
        measurementLastVideoBytes = null
        measurementLastTimestampUs = null
        measurementLastVideoLost = null
        measurementLastEct1 = null
        measurementLastEctCe = null
        ensureCellInfoObservation()
        measurementJob = measurementScope.launch {
            while (loggingEnabled && callActive) {
                val item = collectMeasurementItem()
                if (item != null) {
                    measurementBuffer.add(item)
                    if (measurementBuffer.size >= logBatchSize) flushMeasurements()
                }
                delay(logIntervalMs.toLong())
            }
            flushMeasurements()
        }
    }

    private fun stopMeasurementLoop() {
        measurementJob?.cancel()
        measurementJob = null
        flushMeasurements()
        stopCellInfoObservation()
    }

    private fun flushMeasurements() {
        if (measurementBuffer.isEmpty() || !loggingEnabled) return
        measurementCallback.onMeasurementItemCallback(measurementBuffer.toTypedArray())
        measurementBuffer.clear()
    }

    private suspend fun collectMeasurementItem(): MeasurementItem? = suspendCoroutine { cont ->
        val pc = peerConnection ?: return@suspendCoroutine cont.resume(null)
        pc.getStats(RTCStatsCollectorCallback { report ->
            var rttMs: Double? = null
            var videoBytes = 0.0
            var videoPacketsLost = 0.0
            var screamEct1 = 0.0
            var screamCe = 0.0

            report.statsMap.values.forEach { stat ->
                when (stat.type) {
                    "remote-inbound-rtp" -> {
                        val rtt = (stat.members["roundTripTime"] as? Number)?.toDouble()
                        if (rtt != null) rttMs = rtt * 1000.0
                    }
                    "inbound-rtp" -> {
                        val kind = (stat.members["mediaType"] as? String ?: stat.members["kind"] as? String ?: "").lowercase()
                        if (kind == "video") {
                            videoPacketsLost += (stat.members["packetsLost"] as? Number)?.toDouble() ?: 0.0
                            videoBytes += (stat.members["bytesReceived"] as? Number)?.toDouble() ?: 0.0
                            screamEct1 += (stat.members["packetsReceivedWithEct1"] as? Number)?.toDouble() ?: 0.0
                            screamCe += (stat.members["packetsReceivedWithCe"] as? Number)?.toDouble() ?: 0.0
                        }
                    }
                }
            }

            val currentTsUs = report.timestampUs
            if (measurementLastTimestampUs != null && currentTsUs <= measurementLastTimestampUs!!) {
                cont.resume(null)
                return@RTCStatsCollectorCallback
            }
            val bitrateKbps = if (measurementLastVideoBytes != null && measurementLastTimestampUs != null) {
                val deltaBytes = videoBytes - (measurementLastVideoBytes ?: 0.0)
                val deltaTimeSec = (currentTsUs - (measurementLastTimestampUs ?: currentTsUs)) / 1_000_000.0
                if (deltaTimeSec > 0) (deltaBytes * 8.0) / 1000.0 / deltaTimeSec else null
            } else null

            measurementLastVideoBytes = videoBytes
            measurementLastTimestampUs = currentTsUs
            val packetLossDelta = if (measurementLastVideoLost != null) {
                val delta = videoPacketsLost - (measurementLastVideoLost ?: 0.0)
                if (delta < 0) 0.0 else delta
            } else videoPacketsLost
            measurementLastVideoLost = videoPacketsLost

            val deltaEct1 = if (measurementLastEct1 != null) {
                val delta = screamEct1 - (measurementLastEct1 ?: 0.0)
                if (delta < 0) 0.0 else delta
            } else screamEct1
            val deltaEctCe = if (measurementLastEctCe != null) {
                val delta = screamCe - (measurementLastEctCe ?: 0.0)
                if (delta < 0) 0.0 else delta
            } else screamCe
            measurementLastEct1 = screamEct1
            measurementLastEctCe = screamCe

            val ecnPercent = if ((deltaEct1 + deltaEctCe) > 0) (deltaEctCe / (deltaEct1 + deltaEctCe)) * 100.0 else 0.0
            val item = MeasurementItem(
                timeStampMs = (currentTsUs / 1000).toLong(),
                rttMs = rttMs ?: 0.0,
                loadkbits = bitrateKbps?.toLong() ?: 0L,
                ecnCePercent = ecnPercent,
                packetLossCount = packetLossDelta.toInt(),
                cellId = CurrentCellInfo.cellId,
                pci = CurrentCellInfo.pci,
                band = CurrentCellInfo.band,
                streamId = remoteVideoTrack?.id() ?: "inbound-video",
                sessionName = "webrtc-l4s",
                isNrSa = CurrentCellInfo.isNrSa,
                dbm = CurrentCellInfo.dbm,
                lat = if (logLocationEnabled) CurrentCellInfo.lat else null,
                lon = if (logLocationEnabled) CurrentCellInfo.lon else null
            )
            cont.resume(item)
        })
    }

    fun dispose() {
        stopMeasurementLoop()
        stopCellInfoObservation()
        measurementCallback.close()
        measurementScope.cancel()
    }

    private fun extractStats(report: RTCStatsReport): CallStats {
        var rttMs: Double? = null
        var videoPacketsLost = 0.0
        var videoPacketsTotal = 0.0
        var videoBytes = 0.0
        var audioPacketsLost = 0.0
        var audioPacketsTotal = 0.0
        var audioBytes = 0.0
        var screamEct1 = 0.0
        var screamEctCe = 0.0

        report.statsMap.values.forEach { stat ->
            when (stat.type) {
                "remote-inbound-rtp" -> {
                    val rtt = (stat.members["roundTripTime"] as? Number)?.toDouble()
                    if (rtt != null) rttMs = (rtt * 1000.0)
                }
                "inbound-rtp" -> {
                    val kind = (stat.members["mediaType"] as? String ?: stat.members["kind"] as? String ?: "").lowercase()
                    val lost = (stat.members["packetsLost"] as? Number)?.toDouble() ?: 0.0
                    val received = (stat.members["packetsReceived"] as? Number)?.toDouble() ?: 0.0
                    val bytes = (stat.members["bytesReceived"] as? Number)?.toDouble() ?: 0.0
                    val ect1 = (stat.members["packetsReceivedWithEct1"] as? Number)?.toDouble() ?: 0.0
                    val ce = (stat.members["packetsReceivedWithCe"] as? Number)?.toDouble() ?: 0.0
                    when (kind) {
                        "video" -> {
                            videoPacketsLost += lost
                            videoPacketsTotal += lost + received
                            videoBytes += bytes
                            screamEct1 += ect1
                            screamEctCe += ce
                        }
                        "audio" -> {
                            audioPacketsLost += lost
                            audioPacketsTotal += lost + received
                            audioBytes += bytes
                            screamEct1 += ect1
                            screamEctCe += ce
                        }
                    }
                }
            }
        }

        val currentTs = report.timestampUs
        val videoBitrate = if (lastVideoBytes != null && lastTimestampUs != null) {
            val deltaBytes = videoBytes - (lastVideoBytes ?: 0.0)
            val deltaTimeSec = (currentTs - (lastTimestampUs ?: currentTs)) / 1_000_000.0
            if (deltaTimeSec > 0) (deltaBytes * 8.0) / 1000.0 / deltaTimeSec else null
        } else null

        val audioBitrate = if (lastAudioBytes != null && lastTimestampUs != null) {
            val deltaBytes = audioBytes - (lastAudioBytes ?: 0.0)
            val deltaTimeSec = (currentTs - (lastTimestampUs ?: currentTs)) / 1_000_000.0
            if (deltaTimeSec > 0) (deltaBytes * 8.0) / 1000.0 / deltaTimeSec else null
        } else null

        lastVideoBytes = videoBytes
        lastAudioBytes = audioBytes
        lastTimestampUs = currentTs

        lastScreamEct1 = if (useScream) screamEct1 else null
        lastScreamEctCe = if (useScream) screamEctCe else null

        val deltaEct1 = if (measurementLastEct1 != null) {
            val delta = screamEct1 - (measurementLastEct1 ?: 0.0)
            if (delta < 0) 0.0 else delta
        } else screamEct1
        val deltaEctCe = if (measurementLastEctCe != null) {
            val delta = screamEctCe - (measurementLastEctCe ?: 0.0)
            if (delta < 0) 0.0 else delta
        } else screamEctCe
        val ecnPercent = if ((deltaEct1 + deltaEctCe) > 0) (deltaEctCe / (deltaEct1 + deltaEctCe)) * 100.0 else 0.0
        val connectivity = if (isWifi) {
            "Wifi"
        } else {
            if (useSlice)
                "Slicing"
            else
                "Cellular"
        }

        return CallStats(
            rttMs = rttMs,
            videoPacketLossPercent = if (videoPacketsTotal > 0) (videoPacketsLost / videoPacketsTotal) * 100.0 else null,
            videoBitrateKbps = videoBitrate,
            audioPacketLossPercent = if (audioPacketsTotal > 0) (audioPacketsLost / audioPacketsTotal) * 100.0 else null,
            audioBitrateKbps = audioBitrate,
            screamEct1 = if (useScream) lastScreamEct1 else null,
            screamEctCe = if (useScream) lastScreamEctCe else null,
            screamCeInPercent = ecnPercent,
            connectivity = connectivity
        )
    }

    fun addIceCandidate(candidate: IceCandidate) {
        val addresses = extractCandidateAddresses(candidate)
        if (!isCandidateSliceExclusive(addresses)) {
            Log.d(TAG, "Ignore remote ICE candidate (not on slice): ${candidate.sdp}")
            return
        }
        peerConnection?.addIceCandidate(candidate)
    }

    fun onIceCandidate(callback: (IceCandidate) -> Unit) {
        iceCallback = callback
    }

    fun hangup() {
        callActive = false
        stopMeasurementLoop()
        localVideoTrack?.removeSink(localRenderer)
        remoteVideoTrack?.removeSink(remoteRenderer)
        peerConnection?.close()
        peerConnection = null
        pendingLocalSdpCallback = null
        try {
            videoCapturer?.stopCapture()
        } catch (_: Exception) {
        }
        videoCapturer?.dispose()
        videoCapturer = null
        videoSource?.dispose()
        audioSource?.dispose()
        localVideoTrack = null
        localAudioTrack = null
        remoteVideoTrack = null
        restoreVoiceVolume()
    }

    fun isValidIceServerUrl(url: String): Boolean {
        val pattern = Regex(
            "^(stun|turn|turns):[^\\s]+(:\\d+)?(\\?transport=(tcp|udp))?$",
            RegexOption.IGNORE_CASE
        )
        return pattern.matches(url)
    }

    fun validateIceServers(servers: List<PeerConnection.IceServer>) {
        for (server in servers) {
            for (url in server.urls) {
                if (!isValidIceServerUrl(url)) {
                    throw Exception("Invalid ICE server URL: $url")
                }
                if (url.startsWith("turn:", ignoreCase = true) ||
                    url.startsWith("turns:", ignoreCase = true)) {
                    if (server.username.isNullOrBlank() || server.password.isNullOrBlank()) {
                        throw Exception( "TURN server requires username and password: $url")
                    }
                }
            }
        }
    }

    fun buildStunTurnOrIceServer(): List<PeerConnection.IceServer>
    {
        val iceServer = PeerConnection.IceServer.builder(stunTurnUrl)
        if (stunTurnUsername.isNotBlank())
            iceServer.setUsername(stunTurnUsername)
        if (stunTurnPassword.isNotBlank())
            iceServer.setPassword(stunTurnPassword)

        val result = listOf(iceServer.createIceServer())
        validateIceServers(result)

        return result
    }

    private fun ensurePeerConnection() {
        if (peerConnection != null) return
        if (peerConnectionFactory == null) rebuildFactory()
        val iceServers = buildStunTurnOrIceServer()
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.continualGatheringPolicy = if (useTrickleIce) {
            PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        } else {
            PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
        }

        collectNonSliceIps()

        peerConnection = peerConnectionFactory!!.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "ICE connection state changed to: $p0")
                    if (p0 == PeerConnection.IceConnectionState.FAILED ) {
                        handleIceFailure()
                    }
                }
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState) {
                    Log.d(TAG, "PeerConnection -> onIceGatheringChange")
                    Log.d(TAG, "ICE Gathering State: $p0")
                    if (p0 == PeerConnection.IceGatheringState.COMPLETE)
                    {
                        if (unwantedCandidates.isNotEmpty()) {
                            peerConnection?.removeIceCandidates(unwantedCandidates.toTypedArray())
                            Log.d(TAG, "Removed ${unwantedCandidates.size} unwanted candidates locally")
                            unwantedCandidates.clear()
                        }
                        pendingLocalSdpCallback?.invoke()
                        pendingLocalSdpCallback = null
                    }
                }
                override fun onIceCandidate(p0: IceCandidate) {
                    val addresses = extractCandidateAddresses(p0)
                    val isSliceExclusive = isCandidateSliceExclusive(addresses)
                    if (!isSliceExclusive) {
                        Log.d(TAG, "Discard local ICE candidate (non-slice or mixed): ${p0.sdp}")
                        filterUnwantedCandidate(p0)
                        return
                    }
                    if (!useTrickleIce) {
                        filterUnwantedCandidate(p0)
                        return
                    }
                    p0.let {
                        Log.d(TAG, "Send Candidate: ${it.sdp}")
                        iceCallback?.invoke(it)
                    }
                }
                override fun onIceCandidatesRemoved(p0: Array<out org.webrtc.IceCandidate>?) {}
                override fun onAddStream(p0: org.webrtc.MediaStream?) {}
                override fun onRemoveStream(p0: org.webrtc.MediaStream?) {}
                override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(
                    receiver: org.webrtc.RtpReceiver?,
                    streams: Array<out org.webrtc.MediaStream>?
                ) {
                    val track = receiver?.track() as? VideoTrack ?: return
                    remoteVideoTrack = track
                    remoteRenderer?.let { track.addSink(it) }
                }
                override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent?)
                {
                    super.onSelectedCandidatePairChanged(event)
                    Log.d(TAG, "PeerConnection -> onSelectedCandidatePairChanged")
                    Log.d(TAG, "Reason: ${event?.reason}")
                    val local = event?.local
                    val remote = event?.remote

                    if (local != null && remote != null) {
                        val localAddresses = extractCandidateAddresses(local)
                        if (!isCandidateSliceExclusive(localAddresses)) {
                            Log.d(TAG, "Drop selected pair (local not slice): ${parseCandidate(local)}")
                            peerConnection?.removeIceCandidates(arrayOf(local))
                            return
                        }
                        Log.d(TAG, "Selected Candidate Pair:")
                        Log.d(TAG, "Local Candidate: ${parseCandidate(local)}")
                        Log.d(TAG, "Remote Candidate: ${parseCandidate(remote)}")
                    }
                    else
                        Log.d(TAG, "No candidate pair information available")
                }
            }
        )

        startLocalPreview()
        localVideoTrack?.let { peerConnection?.addTrack(it, listOf("localStream")) }
        localAudioTrack?.let { peerConnection?.addTrack(it, listOf("localStream")) }
        routeAudioToSpeaker()
    }

    private fun handleIceFailure() {
        iceFailureCallback?.invoke("Ice Failure")
    }

    private fun routeAudioToSpeaker() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        setCommunicationDeviceToSpeaker(audioManager)
        maximizeVoiceVolume(audioManager)
    }

    private fun maximizeVoiceVolume(audioManager: AudioManager) {
        if (previousVoiceVolume == null) {
            previousVoiceVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        }
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, max, 0)
    }

    private fun restoreVoiceVolume() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        previousVoiceVolume?.let {
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, it, 0)
        }
        previousVoiceVolume = null
        restoreCommunicationDevice(audioManager)
    }

    private fun setCommunicationDeviceToSpeaker(audioManager: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speaker = audioManager.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (speaker != null) {
                if (previousCommunicationDevice == null) {
                    previousCommunicationDevice = audioManager.communicationDevice
                }
                audioManager.setCommunicationDevice(speaker)
                return
            }
        }
        // Fallback for older devices
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = true
    }

    private fun restoreCommunicationDevice(audioManager: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            previousCommunicationDevice?.let { audioManager.setCommunicationDevice(it) }
            if (previousCommunicationDevice == null) {
                audioManager.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        }
        previousCommunicationDevice = null
    }

    fun setAudioMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        deviceNames.firstOrNull { enumerator.isFrontFacing(it) }?.let {
            return enumerator.createCapturer(it, null)
        }
        deviceNames.firstOrNull()?.let { return enumerator.createCapturer(it, null) }
        return null
    }

    fun getCameraOptions(): CameraOptions {
        val enumerator = Camera2Enumerator(context)
        var front: List<CameraEnumerationAndroid.CaptureFormat> = emptyList()
        var back: List<CameraEnumerationAndroid.CaptureFormat> = emptyList()
        enumerator.deviceNames.forEach { name ->
            val formats = enumerator.getSupportedFormats(name)
            if (enumerator.isFrontFacing(name)) front = formats!! else back = formats!!
        }
        return CameraOptions(frontFormats = front, backFormats = back)
    }

    fun setPreferredFormats(front: CameraEnumerationAndroid.CaptureFormat?, back: CameraEnumerationAndroid.CaptureFormat?) {
        desiredFrontFormat = front
        desiredBackFormat = back
    }

    private fun selectFormatForCurrentCamera(): CameraEnumerationAndroid.CaptureFormat? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        val frontName = deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        val backName = deviceNames.firstOrNull { !enumerator.isFrontFacing(it) }
        return when (currentCameraName) {
            frontName -> desiredFrontFormat
            backName -> desiredBackFormat
            else -> desiredFrontFormat ?: desiredBackFormat
        }
    }

    companion object {
        private const val LOCAL_VIDEO_TRACK_ID = "LOCAL_VIDEO_TRACK"
        private const val LOCAL_AUDIO_TRACK_ID = "LOCAL_AUDIO_TRACK"
    }

    private fun parseCandidate(candidate: IceCandidate): String {
        val parts = candidate.sdp.split(" ")
        return if (parts.size >= 8) {
            val transport = parts[2]
            val address = parts[4]
            val port = parts[5]
            val type = parts[7]
            "type=$type, address=$address, port=$port, transport=$transport"
        } else {
            candidate.sdp
        }
    }

    private fun waitForIceGatheringThen(block: () -> Unit) {
        val pc = peerConnection ?: return
        if (pc.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) {
            block()
            return
        }
        pendingLocalSdpCallback = block
    }

    private fun collectNonSliceIps() {
        nonSliceIps.clear()
        connectivityManager.allNetworks
            .filter { net -> !useSlice || net != SliceNetwork.network }
            .forEach { net ->
                connectivityManager.getLinkProperties(net)
                    ?.linkAddresses
                    ?.mapNotNull { it.address?.hostAddress }
                    ?.filterNot { addr ->
                        addr.isBlank() || addr.startsWith("127.") || addr.startsWith("::1") || addr.startsWith("fe80")
                    }
                    ?.let { nonSliceIps.addAll(it) }
            }
    }

    private fun isCandidateSliceExclusive(addresses: List<String>): Boolean {
        if (sliceIps.isEmpty()) return true
        if (addresses.isEmpty()) return false
        val hasNonSlice = addresses.any { addr ->
            sliceIps.none { it == addr } || nonSliceIps.any { it == addr }
        }
        return !hasNonSlice
    }

    private fun extractCandidateAddresses(candidate: IceCandidate): List<String> {
        val parts = candidate.sdp.split(" ")
        val addresses = mutableListOf<String>()
        if (parts.size >= 6) addresses.add(parts[4])
        val raddrIndex = parts.indexOf("raddr")
        if (raddrIndex in 0 until parts.lastIndex) {
            addresses.add(parts[raddrIndex + 1])
        }
        return addresses
    }

    private fun filterUnwantedCandidate(candidate: IceCandidate) {
        if (!isCandidateSliceExclusive(extractCandidateAddresses(candidate))) {
            Log.d(TAG, "Remove ICE candidate (not on slice): ${candidate.sdp}")
            unwantedCandidates.add(candidate)
            peerConnection?.removeIceCandidates(arrayOf(candidate))
        }
    }

    private fun transformSdp(desc: SessionDescription): SessionDescription {
        if (preferredVideoCodec.equals("All Codecs", ignoreCase = true)) return desc
        val filteredSdp = filterSdpByCodec(desc.description, preferredVideoCodec)
        return SessionDescription(desc.type, filteredSdp)
    }

    private fun filterSdpByCodec(sdp: String, codec: String): String {
        val target = codec.lowercase()
        val lines = sdp.lines().toMutableList()
        val allowedPayloads = mutableSetOf<String>()
        val rtxMap = mutableMapOf<String, String>()

        lines.forEach { line ->
            if (line.startsWith("a=rtpmap:", ignoreCase = true)) {
                val parts = line.substringAfter("a=rtpmap:").split(" ", limit = 2)
                if (parts.size == 2) {
                    val pt = parts[0]
                    val codecName = parts[1].substringBefore("/").lowercase()
                    if (codecName.contains(target)) allowedPayloads.add(pt)
                }
            } else if (line.startsWith("a=fmtp:")) {
                val payload = line.substringAfter("a=fmtp:").substringBefore(" ")
                val apt = line.substringAfter("apt=", "")
                if (apt.isNotBlank()) rtxMap[payload] = apt.substringBefore(";").trim()
            }
        }

        // Keep RTX payloads that point to an allowed codec
        rtxMap.forEach { (rtx, apt) ->
            if (allowedPayloads.contains(apt)) allowedPayloads.add(rtx)
        }
        if (allowedPayloads.isEmpty()) return sdp

        val result = mutableListOf<String>()
        for (line in lines) {
            when {
                line.startsWith("m=video") -> {
                    val parts = line.split(" ")
                    if (parts.size > 3) {
                        val header = parts.take(3)
                        val newPayloads = parts.drop(3).filter { allowedPayloads.contains(it) }
                        if (newPayloads.isEmpty()) return sdp
                        result.add((header + newPayloads).joinToString(" "))
                    } else {
                        result.add(line)
                    }
                }
                line.startsWith("a=rtpmap:") -> {
                    val pt = line.substringAfter("a=rtpmap:").substringBefore(" ")
                    if (allowedPayloads.contains(pt)) result.add(line)
                }
                line.startsWith("a=fmtp:") -> {
                    val pt = line.substringAfter("a=fmtp:").substringBefore(" ")
                    if (allowedPayloads.contains(pt)) result.add(line)
                }
                line.startsWith("a=rtcp-fb:") -> {
                    val pt = line.substringAfter("a=rtcp-fb:").substringBefore(" ")
                    if (pt == "*" || allowedPayloads.contains(pt)) result.add(line)
                }
                else -> result.add(line)
            }
        }
        return result.joinToString("\r\n")
    }
}

open class SimpleSdpObserver : SdpObserver {
    override fun onSetFailure(p0: String?) {}
    override fun onSetSuccess() {}
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onCreateFailure(p0: String?) {}
}

data class CameraOptions(
    val frontFormats: List<CameraEnumerationAndroid.CaptureFormat>,
    val backFormats: List<CameraEnumerationAndroid.CaptureFormat>
)
