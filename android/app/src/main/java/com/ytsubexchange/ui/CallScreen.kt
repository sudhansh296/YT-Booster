package com.ytsubexchange.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ytsubexchange.viewmodel.CallState
import com.ytsubexchange.viewmodel.CallViewModel
import org.webrtc.SurfaceViewRenderer

private val CallBg    = Color(0xFF0A0A0F)
private val CallCard  = Color(0xFF1A1A2E)
private val AccentRed = Color(0xFFE53935)
private val GreenCall = Color(0xFF4CAF50)
private val PurpleAcc = Color(0xFF7B2FF7)

// ── Incoming Call Dialog ──────────────────────────────────────
@Composable
fun IncomingCallDialog(
    viewModel: CallViewModel,
    onDismiss: () -> Unit,
    onAcceptWithPermission: (((() -> Unit)) -> Unit)? = null
) {
    val callType by viewModel.callType.collectAsState()
    val roomName by viewModel.roomName.collectAsState()
    val inf = rememberInfiniteTransition(label = "pulse")
    val scale by inf.animateFloat(1f, 1.15f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "s")

    Dialog(
        onDismissRequest = { viewModel.rejectCall(); onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier.fillMaxWidth().padding(24.dp).clip(RoundedCornerShape(24.dp))
                .background(CallCard).padding(24.dp).statusBarsPadding().navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.scale(scale).size(80.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(AccentRed.copy(0.3f), Color.Transparent))),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(CallCard.copy(0.8f)),
                        contentAlignment = Alignment.Center) {
                        Text(if (callType == "video") "📹" else "📞", fontSize = 28.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Incoming ${if (callType == "video") "Video" else "Voice"} Call",
                    color = Color(0xFF9E9E9E), fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(roomName.ifEmpty { "Unknown" }, color = Color.White,
                    fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(32.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    // Decline
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(AccentRed)
                            .clickable { viewModel.rejectCall(); onDismiss() },
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Decline", color = AccentRed, fontSize = 12.sp)
                    }
                    // Accept
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(GreenCall)
                            .clickable {
                                val action = { viewModel.acceptCall(); onDismiss() }
                                if (onAcceptWithPermission != null) onAcceptWithPermission(action) else action()
                            },
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Accept", color = GreenCall, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ── Active Call Screen ────────────────────────────────────────
@Composable
fun ActiveCallScreen(
    viewModel: CallViewModel,
    onEnd: () -> Unit,
    onRequestScreenCapture: () -> Unit = {}
) {
    val callState     by viewModel.callState.collectAsState()
    val callType      by viewModel.callType.collectAsState()
    val roomName      by viewModel.roomName.collectAsState()
    val isMicMuted    by viewModel.isMicMuted.collectAsState()
    val isCamOff      by viewModel.isCamOff.collectAsState()
    val isSpeakerOn   by viewModel.isSpeakerOn.collectAsState()
    val duration      by viewModel.callDuration.collectAsState()
    val isScreenShare by viewModel.isScreenSharing.collectAsState()
    val upgradeReq    by viewModel.upgradeRequest.collectAsState()

    val durationStr = remember(duration) { "%02d:%02d".format(duration / 60, duration % 60) }

    if (upgradeReq != null) {
        UpgradeRequestDialog(onAccept = { viewModel.acceptUpgrade() }, onReject = { viewModel.rejectUpgrade() })
    }

    Box(Modifier.fillMaxSize().background(CallBg)) {

        // ── Video area ────────────────────────────────────────
        if (callType == "video") {
            // Remote video — full screen
            val remoteView = remember { mutableStateOf<SurfaceViewRenderer?>(null) }
            val localView = remember { mutableStateOf<SurfaceViewRenderer?>(null) }

            DisposableEffect(Unit) {
                onDispose {
                    remoteView.value = null
                    localView.value = null
                }
            }

            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).also { sv ->
                        remoteView.value = sv
                        viewModel.remoteSurfaceView = sv
                    }
                },
                update = { sv ->
                    if (remoteView.value != sv) {
                        remoteView.value = sv
                        viewModel.remoteSurfaceView = sv
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            // Local video — small pip bottom-right
            if (!isScreenShare) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 160.dp)
                        .size(width = 100.dp, height = 140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceViewRenderer(ctx).also { sv ->
                                localView.value = sv
                                viewModel.localSurfaceView = sv
                                viewModel.attachLocalSurface()
                            }
                        },
                        update = { sv ->
                            if (localView.value != sv) {
                                localView.value = sv
                                viewModel.localSurfaceView = sv
                                viewModel.attachLocalSurface()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    // Flip camera button
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp)
                            .clip(CircleShape).background(Color.Black.copy(0.5f))
                            .clickable { viewModel.flipCamera() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FlipCameraAndroid, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // ── Voice call avatar ─────────────────────────────────
        if (callType == "voice") {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(100.dp))
                Box(
                    Modifier.size(120.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(PurpleAcc, Color(0xFF1A1A2E)))),
                    contentAlignment = Alignment.Center
                ) { Text("👤", fontSize = 52.sp) }
                Spacer(Modifier.height(20.dp))
                Text(roomName.ifEmpty { "Call" }, color = Color.White,
                    fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Spacer(Modifier.height(8.dp))
                when (callState) {
                    CallState.CALLING -> {
                        val inf = rememberInfiniteTransition(label = "d")
                        val alpha by inf.animateFloat(0.3f, 1f,
                            infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")
                        Text("Calling...", color = Color.White.copy(alpha), fontSize = 15.sp)
                    }
                    CallState.IN_CALL -> Text(durationStr, color = GreenCall, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    else -> Text("Connecting...", color = Color(0xFF9E9E9E), fontSize = 15.sp)
                }
            }
        }

        // ── Video call top bar ────────────────────────────────
        if (callType == "video") {
            Column(Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Text(roomName.ifEmpty { "Video Call" }, color = Color.White,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
                when (callState) {
                    CallState.IN_CALL -> Text(durationStr, color = GreenCall, fontSize = 13.sp)
                    CallState.CALLING -> Text("Calling...", color = Color(0xFF9E9E9E), fontSize = 13.sp)
                    else -> Text("Connecting...", color = Color(0xFF9E9E9E), fontSize = 13.sp)
                }
                if (isScreenShare) {
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(PurpleAcc.copy(0.8f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("● Screen Sharing", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── Controls overlay ──────────────────────────────────
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f))))
                .padding(bottom = 40.dp, top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Row 1: Mic, Speaker, Cam (video), Flip (video), End
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallBtn(
                    icon = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (isMicMuted) "Unmute" else "Mute",
                    bg = if (isMicMuted) Color(0xFF3A1A1A) else Color(0xFF2A2A3E),
                    tint = if (isMicMuted) AccentRed else Color.White
                ) { viewModel.toggleMic() }

                CallBtn(
                    icon = Icons.Default.VolumeUp,
                    label = if (isSpeakerOn) "Speaker" else "Earpiece",
                    bg = if (isSpeakerOn) Color(0xFF1A3A1A) else Color(0xFF2A2A3E),
                    tint = if (isSpeakerOn) GreenCall else Color.White
                ) { viewModel.toggleSpeaker() }

                if (callType == "video") {
                    CallBtn(
                        icon = if (isCamOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                        label = if (isCamOff) "Cam Off" else "Cam On",
                        bg = if (isCamOff) Color(0xFF3A1A1A) else Color(0xFF2A2A3E),
                        tint = if (isCamOff) AccentRed else Color.White
                    ) { viewModel.toggleCam() }
                }

                // End call
                Box(
                    Modifier.size(64.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(AccentRed, Color(0xFFB71C1C))))
                        .clickable { viewModel.endCall(); onEnd() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }

            // Row 2: Extra controls (IN_CALL only)
            if (callState == CallState.IN_CALL) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Voice → Video upgrade
                    if (callType == "voice") {
                        CallBtn(
                            icon = Icons.Default.Videocam,
                            label = "Video",
                            bg = Color(0xFF1A2A3A),
                            tint = Color(0xFF29B6F6)
                        ) { viewModel.requestUpgradeToVideo() }
                    }

                    // Screen share (video call only)
                    if (callType == "video") {
                        CallBtn(
                            icon = if (isScreenShare) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
                            label = if (isScreenShare) "Stop Share" else "Share Screen",
                            bg = if (isScreenShare) Color(0xFF2A1A3A) else Color(0xFF2A2A3E),
                            tint = if (isScreenShare) PurpleAcc else Color.White
                        ) {
                            if (isScreenShare) viewModel.stopScreenShare()
                            else onRequestScreenCapture()
                        }

                        Spacer(Modifier.width(8.dp))

                        // Flip camera
                        CallBtn(
                            icon = Icons.Default.FlipCameraAndroid,
                            label = "Flip",
                            bg = Color(0xFF2A2A3E),
                            tint = Color.White
                        ) { viewModel.flipCamera() }
                    }
                }
            }
        }
    }
}

// ── Upgrade Request Dialog ────────────────────────────────────
@Composable
fun UpgradeRequestDialog(onAccept: () -> Unit, onReject: () -> Unit) {
    Dialog(onDismissRequest = onReject) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CallCard).padding(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📹", fontSize = 40.sp)
                Spacer(Modifier.height(12.dp))
                Text("Video Call Request", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text("Partner voice call ko video mein upgrade karna chahta hai",
                    color = Color(0xFF9E9E9E), fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A1A1A))
                        .clickable(onClick = onReject).padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center) {
                        Text("Decline", color = AccentRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(GreenCall, Color(0xFF2E7D32))))
                        .clickable(onClick = onAccept).padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center) {
                        Text("Accept ✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ── Reusable call button ──────────────────────────────────────
@Composable
private fun CallBtn(icon: ImageVector, label: String, bg: Color, tint: Color = Color.White, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color(0xFF9E9E9E), fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}
