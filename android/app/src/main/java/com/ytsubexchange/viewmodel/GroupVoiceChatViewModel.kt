package com.ytsubexchange.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ytsubexchange.data.VoiceChatParticipant
import com.ytsubexchange.network.RetrofitClient
import com.ytsubexchange.network.SocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.*

class GroupVoiceChatViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _participants = MutableStateFlow<List<VoiceChatParticipant>>(emptyList())
    val participants: StateFlow<List<VoiceChatParticipant>> = _participants
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted
    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn
    private val _raisedHands = MutableStateFlow<Set<String>>(emptySet())
    val raisedHands: StateFlow<Set<String>> = _raisedHands
    private val _isScreenSharing = MutableStateFlow(false)
    val isScreenSharing: StateFlow<Boolean> = _isScreenSharing
    private val _screenShareUserId = MutableStateFlow<String?>(null)
    val screenShareUserId: StateFlow<String?> = _screenShareUserId
    var remoteScreenSurface: org.webrtc.SurfaceViewRenderer? = null
    private var screenVideoTrack: org.webrtc.VideoTrack? = null
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive
    private val _toastMsg = MutableStateFlow<String?>(null)
    val toastMsg: StateFlow<String?> = _toastMsg
    private var currentRoomId = ""
    private var myUserId = ""
    private var myName = ""
    private var myPic = ""
    private var isAdmin = false
    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var localAudioTrack: AudioTrack? = null
    private var webrtcInitialized = false
    private val peerConnections = mutableMapOf<String, PeerConnection>()
    private val pendingIce = mutableMapOf<String, MutableList<IceCandidate>>()
    private val token: String get() = "Bearer ${prefs.getString("token", "")}"
    private val rawToken: String get() = prefs.getString("token", "") ?: ""

    fun clearToast() { _toastMsg.value = null }

    fun join(roomId: String, userId: String, name: String, pic: String, admin: Boolean = false) {
        if (_isActive.value) return
        currentRoomId = roomId
        myUserId = userId
        isAdmin = admin
        if (name.isNotEmpty()) { myName = name; myPic = pic; startJoin() }
        else { viewModelScope.launch { try { val p = RetrofitClient.api.getProfile(token); myName = p.channelName; myPic = p.profilePic; if (myUserId.isEmpty()) myUserId = p._id } catch (e: Exception) { myName = "Me"; myPic = "" }; startJoin() } }
    }

    private fun startJoin() {
        initWebRTC {
            _isActive.value = true
            _participants.value = listOf(VoiceChatParticipant(userId = myUserId, socketId = SocketManager.getSocketId() ?: "", name = myName, pic = myPic, muted = false))
            listenEvents()
            SocketManager.joinVoiceChat(currentRoomId, rawToken)
        }
    }

    fun leave() { if (!_isActive.value) return; SocketManager.leaveVoiceChat(currentRoomId); cleanup() }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        localAudioTrack?.setEnabled(!_isMuted.value)
        SocketManager.sendVoiceChatMute(currentRoomId, _isMuted.value)
        _participants.value = _participants.value.map { if (it.userId == myUserId) it.copy(muted = _isMuted.value) else it }
    }

    fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
        try {
            val am = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.isSpeakerphoneOn = _isSpeakerOn.value
        } catch (e: Exception) {}
    }

    fun raiseHand(raised: Boolean) {
        SocketManager.emit("voice_chat_raise_hand", org.json.JSONObject().apply {
            put("roomId", currentRoomId)
            put("userId", myUserId)
            put("raised", raised)
        })
    }

    fun startScreenShare(resultCode: Int, data: android.content.Intent) {
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val mgr = ctx.getSystemService(android.media.projection.MediaProjectionManager::class.java)
                val projection = mgr.getMediaProjection(resultCode, data)
                val egl = eglBase ?: return@launch
                val factory = peerConnectionFactory ?: return@launch

                val surfaceTextureHelper = org.webrtc.SurfaceTextureHelper.create("ScreenCapture", egl.eglBaseContext)
                val screenSource = factory.createVideoSource(true)
                val surface = android.view.Surface(surfaceTextureHelper.surfaceTexture)
                projection.createVirtualDisplay(
                    "ScreenShare", 720, 1280, 160,
                    android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface, null, null
                )
                surfaceTextureHelper.startListening { frame ->
                    screenSource.capturerObserver.onFrameCaptured(frame)
                }
                screenVideoTrack = factory.createVideoTrack("screen_vc", screenSource)
                screenVideoTrack?.setEnabled(true)

                // Add to all peer connections
                peerConnections.values.forEach { pc ->
                    try { pc.addTrack(screenVideoTrack, listOf("screen_stream")) } catch (e: Exception) {}
                }

                _isScreenSharing.value = true
                _screenShareUserId.value = myUserId
                SocketManager.emit("voice_chat_screen_share", JSONObject().apply {
                    put("roomId", currentRoomId); put("userId", myUserId); put("sharing", true)
                })
                mainHandler.post { _toastMsg.value = "📺 Screen share shuru ho gaya" }
            } catch (e: Exception) {
                mainHandler.post { _toastMsg.value = "Screen share nahi ho saka: ${e.message?.take(40)}" }
            }
        }
    }

    fun stopScreenShare() {
        screenVideoTrack?.setEnabled(false)
        screenVideoTrack?.dispose()
        screenVideoTrack = null
        _isScreenSharing.value = false
        _screenShareUserId.value = null
        SocketManager.emit("voice_chat_screen_share", JSONObject().apply {
            put("roomId", currentRoomId); put("userId", myUserId); put("sharing", false)
        })
        _toastMsg.value = "Screen share band ho gaya"
    }

    fun isGroupAdmin() = isAdmin

    fun adminMuteUser(targetUserId: String, muted: Boolean) {
        if (!isAdmin) return
        SocketManager.adminMuteVoiceChat(currentRoomId, targetUserId, muted)
    }

    fun adminKickUser(targetUserId: String) {
        if (!isAdmin) return
        SocketManager.adminKickVoiceChat(currentRoomId, targetUserId)
    }

    fun adminEndVoiceChat() {
        if (!isAdmin) return
        SocketManager.adminEndVoiceChat(currentRoomId)
    }

    private fun iceServers() = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("turn:80.211.139.118:3478").setUsername("ytbooster").setPassword("ytbooster2024").createIceServer(),
        PeerConnection.IceServer.builder("turn:80.211.139.118:3478?transport=tcp").setUsername("ytbooster").setPassword("ytbooster2024").createIceServer()
    )

    private fun initWebRTC(onDone: () -> Unit) {
        if (webrtcInitialized) { mainHandler.post { onDone() }; return }
        Thread {
            try {
                val ctx = getApplication<Application>()
                eglBase = EglBase.create()
                PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(ctx).setEnableInternalTracer(false).createInitializationOptions())
                peerConnectionFactory = PeerConnectionFactory.builder().setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)).setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true)).createPeerConnectionFactory()
                val ac = MediaConstraints().apply { mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation","true")); mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression","true")); mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl","true")) }
                localAudioTrack = peerConnectionFactory!!.createAudioTrack("audio_vc", peerConnectionFactory!!.createAudioSource(ac))
                localAudioTrack?.setEnabled(true)
                webrtcInitialized = true
                mainHandler.post { try { val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager; am.mode = AudioManager.MODE_IN_COMMUNICATION; am.isSpeakerphoneOn = true } catch (e: Exception) {}; onDone() }
            } catch (e: Exception) { android.util.Log.e("VoiceChat","initWebRTC: ${e.message}"); mainHandler.post { onDone() } }
        }.start()
    }

    private fun createPeerConnectionFor(socketId: String, userId: String): PeerConnection? {
        val factory = peerConnectionFactory ?: return null
        val cfg = PeerConnection.RTCConfiguration(iceServers()).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN; continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY }
        val pc = factory.createPeerConnection(cfg, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) { SocketManager.sendVoiceChatIce(socketId, JSONObject().apply { put("sdpMid",c.sdpMid); put("sdpMLineIndex",c.sdpMLineIndex); put("candidate",c.sdp) }.toString(), currentRoomId) }
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) { if (s == PeerConnection.IceConnectionState.DISCONNECTED || s == PeerConnection.IceConnectionState.FAILED) mainHandler.post { removePeer(socketId) } }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(r: RtpReceiver?, s: Array<out MediaStream>?) { val t = r?.track(); if (t is AudioTrack) t.setEnabled(true) }
        }) ?: return null
        pc.addTrack(localAudioTrack, listOf("voice_stream"))
        peerConnections[socketId] = pc
        return pc
    }

    private fun sendOfferTo(socketId: String, userId: String) {
        val pc = createPeerConnectionFor(socketId, userId) ?: return
        Thread { try { val c = MediaConstraints().apply { mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio","true")) }; pc.createOffer(makeSdpObserver(pc, socketId, true), c) } catch (e: Exception) {} }.start()
    }

    private fun makeSdpObserver(pc: PeerConnection, socketId: String, isOffer: Boolean): SdpObserver = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {
            pc.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onSetSuccess() { val s = JSONObject().apply { put("type",sdp.type.canonicalForm()); put("sdp",sdp.description) }.toString(); if (isOffer) SocketManager.sendVoiceChatOffer(socketId, s, currentRoomId) else SocketManager.sendVoiceChatAnswer(socketId, s, currentRoomId) }
                override fun onCreateFailure(p0: String?) {}
                override fun onSetFailure(e: String?) {}
            }, sdp)
        }
        override fun onSetSuccess() {}
        override fun onCreateFailure(e: String?) {}
        override fun onSetFailure(e: String?) {}
    }

    private fun removePeer(socketId: String) { peerConnections[socketId]?.close(); peerConnections.remove(socketId); pendingIce.remove(socketId) }

    private fun listenEvents() {
        SocketManager.on("voice_chat_participants") { args ->
            try {
                val data = args[0] as JSONObject; val arr = data.optJSONArray("participants") ?: return@on
                val list = (0 until arr.length()).map { i -> val p = arr.getJSONObject(i); VoiceChatParticipant(userId=p.optString("userId"), socketId=p.optString("socketId"), name=p.optString("name"), pic=p.optString("pic"), muted=p.optBoolean("muted")) }
                mainHandler.post { val me = _participants.value.firstOrNull { it.userId == myUserId }; _participants.value = (if (me != null) listOf(me) else emptyList()) + list; list.forEach { p -> sendOfferTo(p.socketId, p.userId) } }
            } catch (e: Exception) {}
        }
        SocketManager.on("voice_chat_user_joined") { args ->
            try {
                val data = args[0] as JSONObject; val p = VoiceChatParticipant(userId=data.optString("userId"), socketId=data.optString("socketId"), name=data.optString("name"), pic=data.optString("pic"), muted=data.optBoolean("muted"))
                mainHandler.post { if (_participants.value.none { it.userId == p.userId }) _participants.value = _participants.value + p; _toastMsg.value = "${p.name} voice chat mein aaya" }
                createPeerConnectionFor(p.socketId, p.userId)
            } catch (e: Exception) {}
        }
        SocketManager.on("voice_chat_user_left") { args ->
            try { val data = args[0] as JSONObject; val uid = data.optString("userId"); val sid = data.optString("socketId"); mainHandler.post { val l = _participants.value.firstOrNull { it.userId == uid }; _participants.value = _participants.value.filter { it.userId != uid }; l?.let { _toastMsg.value = "${it.name} voice chat se gaya" }; removePeer(sid) } } catch (e: Exception) {}
        }
        SocketManager.on("voice_chat_mute_changed") { args ->
            try { val data = args[0] as JSONObject; val uid = data.optString("userId"); val m = data.optBoolean("muted"); mainHandler.post { _participants.value = _participants.value.map { if (it.userId == uid) it.copy(muted = m) else it } } } catch (e: Exception) {}
        }
        SocketManager.on("voice_chat_offer") { args ->
            try {
                val data = args[0] as JSONObject; val fSid = data.optString("fromSocketId"); val fUid = data.optString("fromUserId"); val oj = JSONObject(data.optString("offer"))
                val sdp = SessionDescription(SessionDescription.Type.fromCanonicalForm(oj.optString("type")), oj.optString("sdp"))
                val pc = peerConnections[fSid] ?: createPeerConnectionFor(fSid, fUid) ?: return@on
                Thread { pc.setRemoteDescription(object : SdpObserver { override fun onCreateSuccess(p0: SessionDescription?) {}; override fun onSetSuccess() { pendingIce[fSid]?.forEach { pc.addIceCandidate(it) }; pendingIce.remove(fSid); pc.createAnswer(makeSdpObserver(pc, fSid, false), MediaConstraints()) }; override fun onCreateFailure(e: String?) {}; override fun onSetFailure(e: String?) {} }, sdp) }.start()
            } catch (e: Exception) {}
        }
        SocketManager.on("voice_chat_answer") { args ->
            try {
                val data = args[0] as JSONObject; val fSid = data.optString("fromSocketId"); val aj = JSONObject(data.optString("answer"))
                val sdp = SessionDescription(SessionDescription.Type.fromCanonicalForm(aj.optString("type")), aj.optString("sdp"))
                val pc = peerConnections[fSid] ?: return@on
                Thread { pc.setRemoteDescription(object : SdpObserver { override fun onCreateSuccess(p0: SessionDescription?) {}; override fun onSetSuccess() { pendingIce[fSid]?.forEach { pc.addIceCandidate(it) }; pendingIce.remove(fSid) }; override fun onCreateFailure(e: String?) {}; override fun onSetFailure(e: String?) {} }, sdp) }.start()
            } catch (e: Exception) {}
        }
        SocketManager.on("voice_chat_ice") { args ->
            try {
                val data = args[0] as JSONObject; val fSid = data.optString("fromSocketId"); val cj = JSONObject(data.optString("candidate"))
                val c = IceCandidate(cj.optString("sdpMid"), cj.optInt("sdpMLineIndex"), cj.optString("candidate"))
                val pc = peerConnections[fSid]; if (pc != null && pc.remoteDescription != null) pc.addIceCandidate(c) else pendingIce.getOrPut(fSid) { mutableListOf() }.add(c)
            } catch (e: Exception) {}
        }

        // Admin muted you
        SocketManager.on("voice_chat_admin_muted_you") { args ->
            try {
                val data = args[0] as JSONObject
                val muted = data.optBoolean("muted")
                mainHandler.post {
                    _isMuted.value = muted
                    localAudioTrack?.setEnabled(!muted)
                    _participants.value = _participants.value.map { if (it.userId == myUserId) it.copy(muted = muted) else it }
                    _toastMsg.value = if (muted) "🔇 Admin ne aapko mute kar diya" else "🔊 Admin ne aapko unmute kar diya"
                }
            } catch (e: Exception) {}
        }

        // Admin kicked you
        SocketManager.on("voice_chat_kicked") { _ ->
            mainHandler.post {
                _toastMsg.value = "❌ Admin ne aapko voice chat se remove kar diya"
                leave()
            }
        }

        // Admin ended voice chat
        SocketManager.on("voice_chat_ended_by_admin") { _ ->
            mainHandler.post {
                _toastMsg.value = "Voice chat admin ne band kar diya"
                cleanup()
            }
        }

        // Raise hand
        SocketManager.on("voice_chat_hand_raised") { args ->
            try {
                val data = args[0] as JSONObject
                val uid = data.optString("userId")
                val raised = data.optBoolean("raised")
                mainHandler.post {
                    _raisedHands.value = if (raised) _raisedHands.value + uid else _raisedHands.value - uid
                    if (raised && uid != myUserId) {
                        val name = _participants.value.firstOrNull { it.userId == uid }?.name ?: uid
                        _toastMsg.value = "✋ $name ne haath uthaya"
                    }
                }
            } catch (e: Exception) {}
        }

        // Screen share
        SocketManager.on("voice_chat_screen_share") { args ->
            try {
                val data = args[0] as JSONObject
                val uid = data.optString("userId")
                val sharing = data.optBoolean("sharing")
                mainHandler.post {
                    if (sharing) {
                        _screenShareUserId.value = uid
                        val name = _participants.value.firstOrNull { it.userId == uid }?.name ?: uid
                        if (uid != myUserId) _toastMsg.value = "📺 $name screen share kar raha hai"
                    } else {
                        if (_screenShareUserId.value == uid) _screenShareUserId.value = null
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun cleanup() {
        _isActive.value = false; _participants.value = emptyList(); _isMuted.value = false
        Thread { try { peerConnections.values.forEach { it.close() }; peerConnections.clear(); pendingIce.clear(); localAudioTrack?.dispose(); localAudioTrack = null; peerConnectionFactory?.dispose(); peerConnectionFactory = null; eglBase?.release(); eglBase = null; webrtcInitialized = false } catch (e: Exception) {}; mainHandler.post { try { val am = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager; am.mode = AudioManager.MODE_NORMAL; am.isSpeakerphoneOn = false } catch (e: Exception) {} } }.start()
        listOf("voice_chat_participants","voice_chat_user_joined","voice_chat_user_left","voice_chat_mute_changed","voice_chat_offer","voice_chat_answer","voice_chat_ice",
            "voice_chat_admin_muted_you","voice_chat_kicked","voice_chat_ended_by_admin","voice_chat_hand_raised","voice_chat_screen_share"
        ).forEach { SocketManager.off(it) }
    }

    override fun onCleared() { super.onCleared(); if (_isActive.value) { SocketManager.leaveVoiceChat(currentRoomId); cleanup() } }
}
