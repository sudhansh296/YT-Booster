package com.ytsubexchange.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ytsubexchange.network.SocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.*

enum class CallState { IDLE, CALLING, INCOMING, IN_CALL }

class CallViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    val callState    = MutableStateFlow(CallState.IDLE)
    val callType     = MutableStateFlow("voice")
    val roomId       = MutableStateFlow("")
    val roomName     = MutableStateFlow("")
    val callerId     = MutableStateFlow("")
    val isMicMuted   = MutableStateFlow(false)
    val isCamOff     = MutableStateFlow(false)
    val isSpeakerOn  = MutableStateFlow(true)
    val callDuration = MutableStateFlow(0L)
    val isScreenSharing = MutableStateFlow(false)
    val upgradeRequest  = MutableStateFlow<String?>(null)

    // Surface views — set from UI
    var localSurfaceView: SurfaceViewRenderer? = null
    var remoteSurfaceView: SurfaceViewRenderer? = null

    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var localScreenTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var screenSurfaceHelper: SurfaceTextureHelper? = null

    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private var remoteSocketId: String? = null
    private var durationJob: kotlinx.coroutines.Job? = null
    private var timeoutJob: kotlinx.coroutines.Job? = null
    private var webrtcInitialized = false

    // Track surface init state to avoid double-init crash
    private var localSurfaceInited = false
    private var remoteSurfaceInited = false

    // Screen capture result (from Activity)
    var screenCaptureResultCode: Int = 0
    var screenCaptureData: Intent? = null

    init { listenCallEvents() }

    // ── ICE Servers ───────────────────────────────────────────
    private fun iceServers() = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("turn:80.211.139.118:3478")
            .setUsername("ytbooster").setPassword("ytbooster2024").createIceServer(),
        PeerConnection.IceServer.builder("turn:80.211.139.118:3478?transport=tcp")
            .setUsername("ytbooster").setPassword("ytbooster2024").createIceServer()
    )

    // ── Init WebRTC (background thread) ──────────────────────
    private fun initWebRTC(onDone: () -> Unit = {}) {
        if (webrtcInitialized) { mainHandler.post { onDone() }; return }
        Thread {
            try {
                val ctx = getApplication<Application>()
                eglBase = EglBase.create()

                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(ctx)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                )

                peerConnectionFactory = PeerConnectionFactory.builder()
                    .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase!!.eglBaseContext))
                    .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true))
                    .createPeerConnectionFactory()

                val audioConstraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                }
                localAudioTrack = peerConnectionFactory!!
                    .createAudioTrack("audio0", peerConnectionFactory!!.createAudioSource(audioConstraints))
                localAudioTrack?.setEnabled(true)

                webrtcInitialized = true

                mainHandler.post {
                    try {
                        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                        am.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
                        am.isSpeakerphoneOn = isSpeakerOn.value
                    } catch (e: Exception) { android.util.Log.e("CallVM", "AudioManager: ${e.message}") }
                    onDone()
                }
            } catch (e: Exception) {
                android.util.Log.e("CallVM", "initWebRTC failed: ${e.message}")
                mainHandler.post { onDone() }
            }
        }.start()
    }

    // ── Init video capturer (front camera) ───────────────────
    private fun initVideoTrack(onDone: (() -> Unit)? = null) {
        if (peerConnectionFactory == null || eglBase == null) { onDone?.invoke(); return }
        if (localVideoTrack != null) { onDone?.invoke(); return }  // already initialized
        Thread {
            try {
                val ctx = getApplication<Application>()
                val enumerator = Camera2Enumerator(ctx)
                val frontCamera = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                    ?: enumerator.deviceNames.firstOrNull()
                    ?: run { mainHandler.post { onDone?.invoke() }; return@Thread }

                videoCapturer = enumerator.createCapturer(frontCamera, null) as? CameraVideoCapturer
                    ?: run { mainHandler.post { onDone?.invoke() }; return@Thread }
                surfaceTextureHelper = SurfaceTextureHelper.create("CameraThread", eglBase!!.eglBaseContext)

                val videoSource = peerConnectionFactory!!.createVideoSource(false)
                videoCapturer!!.initialize(surfaceTextureHelper, ctx, videoSource.capturerObserver)
                videoCapturer!!.startCapture(1280, 720, 30)

                localVideoTrack = peerConnectionFactory!!.createVideoTrack("video0", videoSource)
                localVideoTrack?.setEnabled(true)

                mainHandler.post {
                    // Show local preview — surface ready hone pe
                    attachLocalSurface()
                    onDone?.invoke()
                }
            } catch (e: Exception) {
                android.util.Log.e("CallVM", "initVideoTrack: ${e.message}")
                mainHandler.post { onDone?.invoke() }
            }
        }.start()
    }

    // ── Attach local video to surface (safe, no double-init) ─
    fun attachLocalSurface() {
        val sv = localSurfaceView ?: return
        val track = localVideoTrack ?: return
        val egl = eglBase ?: return
        mainHandler.post {
            try {
                if (!localSurfaceInited) {
                    sv.init(egl.eglBaseContext, null)
                    sv.setMirror(true)
                    sv.setEnableHardwareScaler(true)
                    localSurfaceInited = true
                }
                track.addSink(sv)
            } catch (e: Exception) { android.util.Log.e("CallVM", "attachLocalSurface: ${e.message}") }
        }
    }

    // ── Attach remote video to surface (safe, no double-init) ─
    private fun attachRemoteSurface(track: VideoTrack) {
        mainHandler.post {
            val sv = remoteSurfaceView ?: return@post
            val egl = eglBase ?: return@post
            try {
                if (!remoteSurfaceInited) {
                    sv.init(egl.eglBaseContext, null)
                    sv.setEnableHardwareScaler(true)
                    remoteSurfaceInited = true
                }
                track.addSink(sv)
            } catch (e: Exception) { android.util.Log.e("CallVM", "attachRemoteSurface: ${e.message}") }
        }
    }

    // ── Create PeerConnection ─────────────────────────────────
    private fun createPeerConnection(withVideo: Boolean) {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val rId = remoteSocketId ?: return
                SocketManager.sendIceCandidate(rId, JSONObject().apply {
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                    put("candidate", candidate.sdp)
                }.toString())
            }
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                android.util.Log.d("CallVM", "ICE: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        mainHandler.post { callState.value = CallState.IN_CALL; startTimer() }
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        android.util.Log.e("CallVM", "ICE FAILED — check TURN server")
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        mainHandler.post { endCall() }
                    }
                    else -> {}
                }
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track() ?: return
                if (track is VideoTrack) {
                    attachRemoteSurface(track)
                } else if (track is AudioTrack) {
                    track.setEnabled(true)
                }
            }
        }) ?: return

        // Add audio track
        peerConnection?.addTrack(localAudioTrack, listOf("stream0"))

        // Add video track if needed — init first (async), then add to peer connection
        if (withVideo) {
            initVideoTrack {
                // Called on main thread after video track is ready
                val pc = peerConnection ?: return@initVideoTrack
                localVideoTrack?.let { pc.addTrack(it, listOf("stream0")) }
            }
        }
    }

    // ── Outgoing call ─────────────────────────────────────────
    fun startCall(roomId: String, roomName: String, callType: String) {
        this.roomId.value = roomId
        this.roomName.value = roomName
        this.callType.value = callType
        callState.value = CallState.CALLING
        val token = prefs.getString("token", "") ?: ""
        // 30s timeout — agar callee join nahi karta toh auto-end
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            kotlinx.coroutines.delay(30_000)
            if (callState.value == CallState.CALLING) {
                android.util.Log.w("CallVM", "Call timeout — no answer in 30s")
                endCall()
            }
        }
        // Init WebRTC immediately so we're ready when callee joins
        initWebRTC {
            val withVideo = callType == "video"
            createPeerConnection(withVideo)
            SocketManager.startCall(roomId, callType, token)
        }
    }

    // ── Incoming call accepted ────────────────────────────────
    fun acceptCall() {
        callState.value = CallState.IN_CALL
        val withVideo = callType.value == "video"
        val token = prefs.getString("token", "") ?: ""
        initWebRTC {
            createPeerConnection(withVideo)
            // Notify caller that we joined — they will send offer
            SocketManager.joinCall(roomId.value, callerId.value, token)
        }
    }

    fun rejectCall() {
        val token = prefs.getString("token", "") ?: ""
        SocketManager.endCall(roomId.value, token)
        reset()
    }

    fun endCall() {
        val token = prefs.getString("token", "") ?: ""
        SocketManager.endCall(roomId.value, token)
        reset()
    }

    // ── Controls ──────────────────────────────────────────────
    fun toggleMic() {
        isMicMuted.value = !isMicMuted.value
        localAudioTrack?.setEnabled(!isMicMuted.value)
    }

    fun toggleCam() {
        isCamOff.value = !isCamOff.value
        localVideoTrack?.setEnabled(!isCamOff.value)
    }

    fun toggleSpeaker() {
        isSpeakerOn.value = !isSpeakerOn.value
        try {
            val am = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            am.isSpeakerphoneOn = isSpeakerOn.value
        } catch (e: Exception) { }
    }

    fun flipCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    // ── Voice → Video upgrade ─────────────────────────────────
    fun requestUpgradeToVideo() {
        val token = prefs.getString("token", "") ?: ""
        SocketManager.upgradeCall(roomId.value, "video", token)
    }

    fun acceptUpgrade() {
        upgradeRequest.value = null
        callType.value = "video"
        val ctx = getApplication<Application>()
        if (android.content.pm.PackageManager.PERMISSION_GRANTED !=
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA)) {
            android.util.Log.e("CallVM", "acceptUpgrade: Camera permission not granted")
            callType.value = "voice"
            return
        }
        // Ensure factory is initialized before video track
        if (peerConnectionFactory == null || eglBase == null) {
            android.util.Log.e("CallVM", "acceptUpgrade: factory not ready, reinitializing")
            initWebRTC()
        }
        initVideoTrack {
            localVideoTrack?.let { track ->
                try {
                    val pc = peerConnection ?: return@let
                    val existingVideoSender = pc.senders?.firstOrNull { it.track() is VideoTrack }
                    if (existingVideoSender != null) {
                        existingVideoSender.setTrack(track, false)
                    } else {
                        pc.addTrack(track, listOf("stream0"))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CallVM", "acceptUpgrade addTrack: ${e.message}")
                }
            }
            attachLocalSurface()
            Thread {
                try {
                    val constraints = MediaConstraints().apply {
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                    }
                    peerConnection?.createOffer(makeSdpObserver(isOffer = true), constraints)
                } catch (e: Exception) { android.util.Log.e("CallVM", "acceptUpgrade renegotiate: ${e.message}") }
            }.start()
        }
    }

    fun rejectUpgrade() { upgradeRequest.value = null }

    // ── Screen Share ──────────────────────────────────────────
    fun startScreenShare() {
        val resultCode = screenCaptureResultCode
        val data = screenCaptureData ?: run {
            android.util.Log.e("CallVM", "startScreenShare: screenCaptureData is null")
            return
        }
        if (resultCode == 0) {
            android.util.Log.e("CallVM", "startScreenShare: resultCode is 0")
            return
        }
        val factory = peerConnectionFactory ?: run {
            android.util.Log.e("CallVM", "startScreenShare: peerConnectionFactory null")
            return
        }
        val egl = eglBase ?: run {
            android.util.Log.e("CallVM", "startScreenShare: eglBase null")
            return
        }

        Thread {
            try {
                val ctx = getApplication<Application>()

                // Stop camera first
                try { videoCapturer?.stopCapture() } catch (e: Exception) { }

                screenSurfaceHelper = SurfaceTextureHelper.create("ScreenThread", egl.eglBaseContext)
                val screenSource = factory.createVideoSource(true)

                screenCapturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
                    override fun onStop() { mainHandler.post { stopScreenShare() } }
                })
                screenCapturer!!.initialize(screenSurfaceHelper, ctx, screenSource.capturerObserver)
                screenCapturer!!.startCapture(1280, 720, 15)

                localScreenTrack = factory.createVideoTrack("screen0", screenSource)
                localScreenTrack?.setEnabled(true)

                // Replace video track in sender
                val senders = peerConnection?.senders
                val videoSender = senders?.firstOrNull { it.track()?.kind() == "video" }
                if (videoSender != null) {
                    videoSender.setTrack(localScreenTrack, false)
                    android.util.Log.d("CallVM", "Screen share: replaced video sender track")
                } else {
                    peerConnection?.addTrack(localScreenTrack, listOf("stream0"))
                    android.util.Log.d("CallVM", "Screen share: added new track")
                }

                mainHandler.post { isScreenSharing.value = true }
            } catch (e: Exception) {
                android.util.Log.e("CallVM", "startScreenShare error: ${e.message}")
                // Restart camera on failure
                try { videoCapturer?.startCapture(1280, 720, 30) } catch (ex: Exception) { }
            }
        }.start()
    }

    fun stopScreenShare() {
        Thread {
            try {
                screenCapturer?.stopCapture()
                screenCapturer?.dispose()
                screenCapturer = null
                screenSurfaceHelper?.dispose()
                screenSurfaceHelper = null
                localScreenTrack?.dispose()
                localScreenTrack = null

                // Restart camera
                try { videoCapturer?.startCapture(1280, 720, 30) } catch (e: Exception) {
                    android.util.Log.e("CallVM", "stopScreenShare restart cam: ${e.message}")
                }

                // Restore camera track in sender
                val senders = peerConnection?.senders
                val videoSender = senders?.firstOrNull { it.track() == null || it.track()?.kind() == "video" }
                if (videoSender != null && localVideoTrack != null) {
                    videoSender.setTrack(localVideoTrack, false)
                    android.util.Log.d("CallVM", "stopScreenShare: restored camera track")
                }

                mainHandler.post {
                    isScreenSharing.value = false
                    try {
                        getApplication<Application>().stopService(
                            android.content.Intent(getApplication(), com.ytsubexchange.ScreenCaptureService::class.java)
                        )
                    } catch (e: Exception) { }
                }
            } catch (e: Exception) {
                android.util.Log.e("CallVM", "stopScreenShare error: ${e.message}")
                mainHandler.post {
                    isScreenSharing.value = false
                    try {
                        getApplication<Application>().stopService(
                            android.content.Intent(getApplication(), com.ytsubexchange.ScreenCaptureService::class.java)
                        )
                    } catch (ex: Exception) { }
                }
            }
        }.start()
    }

    // ── SDP helpers ───────────────────────────────────────────
    private fun makeSdpObserver(isOffer: Boolean): SdpObserver = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {
            peerConnection?.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onSetSuccess() {
                    val rId = remoteSocketId ?: return
                    if (isOffer) {
                        SocketManager.sendOffer(rId, JSONObject().apply {
                            put("type", sdp.type.canonicalForm())
                            put("sdp", sdp.description)
                        }.toString(), roomId.value)
                    } else {
                        SocketManager.sendAnswer(rId, JSONObject().apply {
                            put("type", sdp.type.canonicalForm())
                            put("sdp", sdp.description)
                        }.toString())
                    }
                }
                override fun onCreateFailure(p0: String?) {}
                override fun onSetFailure(e: String?) { android.util.Log.e("CallVM", "setLocal fail: $e") }
            }, sdp)
        }
        override fun onSetSuccess() {}
        override fun onCreateFailure(e: String?) { android.util.Log.e("CallVM", "createSdp fail: $e") }
        override fun onSetFailure(e: String?) { android.util.Log.e("CallVM", "setSdp fail: $e") }
    }

    private fun startTimer() {
        durationJob?.cancel()
        durationJob = viewModelScope.launch {
            while (true) { kotlinx.coroutines.delay(1000); callDuration.value++ }
        }
    }

    private fun reset() {
        durationJob?.cancel()
        timeoutJob?.cancel()
        Thread {
            try {
                screenCapturer?.stopCapture(); screenCapturer?.dispose(); screenCapturer = null
                videoCapturer?.stopCapture(); videoCapturer?.dispose(); videoCapturer = null
                localVideoTrack?.dispose(); localVideoTrack = null
                localScreenTrack?.dispose(); localScreenTrack = null
                localAudioTrack?.dispose(); localAudioTrack = null
                surfaceTextureHelper?.dispose(); surfaceTextureHelper = null
                screenSurfaceHelper?.dispose(); screenSurfaceHelper = null
                peerConnection?.close(); peerConnection = null
                peerConnectionFactory?.dispose(); peerConnectionFactory = null
                eglBase?.release(); eglBase = null
                webrtcInitialized = false
            } catch (e: Exception) { android.util.Log.e("CallVM", "reset: ${e.message}") }
            pendingIceCandidates.clear()
            remoteSocketId = null
            mainHandler.post {
                try {
                    val am = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    am.mode = android.media.AudioManager.MODE_NORMAL
                    am.isSpeakerphoneOn = false
                } catch (e: Exception) { }
                try { if (localSurfaceInited) { localSurfaceView?.release(); localSurfaceInited = false } } catch (e: Exception) { }
                try { if (remoteSurfaceInited) { remoteSurfaceView?.release(); remoteSurfaceInited = false } } catch (e: Exception) { }
                callState.value = CallState.IDLE
                callDuration.value = 0L
                isMicMuted.value = false
                isCamOff.value = false
                isScreenSharing.value = false
                upgradeRequest.value = null
            }
        }.start()
    }

    // ── Socket Events ─────────────────────────────────────────
    private fun listenCallEvents() {
        SocketManager.on("call_incoming") { args ->
            try {
                val data = args[0] as JSONObject
                roomId.value   = data.optString("roomId")
                callType.value = data.optString("callType", "voice")
                callerId.value = data.optString("callerId")
                remoteSocketId = data.optString("callerSocketId")
                val name = data.optString("callerName", "")
                if (name.isNotEmpty()) roomName.value = name
                callState.value = CallState.INCOMING
            } catch (e: Exception) { }
        }

        // Caller receives this when callee joins — now send offer
        SocketManager.on("call_user_joined") { args ->
            try {
                val data = args[0] as JSONObject
                remoteSocketId = data.optString("socketId")
                // Callee joined — cancel timeout
                timeoutJob?.cancel()
                // peerConnection already created in startCall → just send offer
                val withVideo = callType.value == "video"
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    if (withVideo) mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }
                Thread {
                    peerConnection?.createOffer(makeSdpObserver(isOffer = true), constraints)
                }.start()
            } catch (e: Exception) { }
        }

        SocketManager.on("call_ended") { _ -> mainHandler.post { reset() } }

        SocketManager.on("call_upgrade") { args ->
            try {
                val data = args[0] as JSONObject
                upgradeRequest.value = data.optString("newType", "video")
            } catch (e: Exception) { }
        }

        SocketManager.on("webrtc_offer") { args ->
            try {
                val data = args[0] as JSONObject
                val offerJson = JSONObject(data.optString("offer"))
                remoteSocketId = data.optString("fromSocketId")
                val sdp = SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(offerJson.optString("type")),
                    offerJson.optString("sdp")
                )
                Thread {
                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            pendingIceCandidates.forEach { peerConnection?.addIceCandidate(it) }
                            pendingIceCandidates.clear()
                            peerConnection?.createAnswer(makeSdpObserver(isOffer = false), MediaConstraints())
                        }
                        override fun onCreateFailure(e: String?) {}
                        override fun onSetFailure(e: String?) { android.util.Log.e("CallVM", "setRemote(offer) fail: $e") }
                    }, sdp)
                }.start()
            } catch (e: Exception) { }
        }

        SocketManager.on("webrtc_answer") { args ->
            try {
                val data = args[0] as JSONObject
                val answerJson = JSONObject(data.optString("answer"))
                val sdp = SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(answerJson.optString("type")),
                    answerJson.optString("sdp")
                )
                Thread {
                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            pendingIceCandidates.forEach { peerConnection?.addIceCandidate(it) }
                            pendingIceCandidates.clear()
                        }
                        override fun onCreateFailure(e: String?) {}
                        override fun onSetFailure(e: String?) { android.util.Log.e("CallVM", "setRemote(answer) fail: $e") }
                    }, sdp)
                }.start()
            } catch (e: Exception) { }
        }

        SocketManager.on("webrtc_ice") { args ->
            try {
                val data = args[0] as JSONObject
                val candJson = JSONObject(data.optString("candidate"))
                val candidate = IceCandidate(
                    candJson.optString("sdpMid"),
                    candJson.optInt("sdpMLineIndex"),
                    candJson.optString("candidate")
                )
                if (peerConnection?.remoteDescription != null) {
                    peerConnection?.addIceCandidate(candidate)
                } else {
                    pendingIceCandidates.add(candidate)
                }
            } catch (e: Exception) { }
        }
    }

    override fun onCleared() {
        super.onCleared()
        reset()
        listOf("call_incoming","call_user_joined","call_ended","call_upgrade",
            "webrtc_offer","webrtc_answer","webrtc_ice").forEach { SocketManager.off(it) }
    }
}
