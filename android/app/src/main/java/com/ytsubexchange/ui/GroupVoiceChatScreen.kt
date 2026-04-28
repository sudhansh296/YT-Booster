package com.ytsubexchange.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ytsubexchange.data.VoiceChatParticipant
import com.ytsubexchange.viewmodel.GroupVoiceChatViewModel

private val VcBg      = Color(0xFF0D0D0D)
private val VcCard    = Color(0xFF1A1A2E)
private val VcCardAlt = Color(0xFF16213E)
private val VcAccent  = Color(0xFF7B2FF7)
private val VcGreen   = Color(0xFF4CAF50)
private val VcRed     = Color(0xFFE53935)
private val VcBlue    = Color(0xFF29B6F6)
private val VcGold    = Color(0xFFFFD700)

@Composable
fun GroupVoiceChatScreen(
    roomName: String,
    viewModel: GroupVoiceChatViewModel,
    onLeave: () -> Unit,
    onRequestScreenCapture: (() -> Unit)? = null
) {
    val participants by viewModel.participants.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    val raisedHands by viewModel.raisedHands.collectAsState()
    val isScreenSharing by viewModel.isScreenSharing.collectAsState()
    val screenShareUserId by viewModel.screenShareUserId.collectAsState()
    val toastMsg by viewModel.toastMsg.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isAdmin = viewModel.isGroupAdmin()
    var adminTargetParticipant by remember { mutableStateOf<VoiceChatParticipant?>(null) }
    var myHandRaised by remember { mutableStateOf(false) }
    val isActive by viewModel.isActive.collectAsState()
    val myId = viewModel.getMyUserId()

    LaunchedEffect(toastMsg) {
        val msg = toastMsg
        if (msg != null) { snackbarHostState.showSnackbar(message = msg); viewModel.clearToast() }
    }

    Box(Modifier.fillMaxSize().background(VcBg).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth()
                    .background(Color(0xFF111122))
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* minimize — stay in chat */ onLeave() }) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text("Voice Chat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(roomName, color = Color(0xFF9E9E9E), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Live indicator
                val inf = rememberInfiniteTransition(label = "live")
                val alpha by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(VcGreen.copy(alpha)))
                    Text("${participants.size}", color = VcGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Divider(color = Color(0xFF1E1E2E), thickness = 0.5.dp)

            // ── Participants List (Telegram style) ────────────
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(participants) { p ->
                    val isMe = p.userId == myId
                    val hasRaisedHand = raisedHands.contains(p.userId)
                    val isSpeaking = !p.muted

                    Row(
                        Modifier.fillMaxWidth()
                            .then(
                                if (isAdmin && !isMe)
                                    Modifier.clickable { adminTargetParticipant = p }
                                else Modifier
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Avatar with speaking ring
                        Box(contentAlignment = Alignment.Center) {
                            if (isSpeaking) {
                                val inf2 = rememberInfiniteTransition(label = "ring_${ p.userId}")
                                val ringAlpha by inf2.animateFloat(0.2f, 0.6f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "ra")
                                Box(Modifier.size(52.dp).clip(CircleShape).background(VcAccent.copy(ringAlpha)))
                            }
                            Box(
                                Modifier.size(46.dp).clip(CircleShape)
                                    .background(
                                        if (isMe) Brush.radialGradient(listOf(Color(0xFF1565C0), Color(0xFF0D47A1)))
                                        else Brush.radialGradient(listOf(Color(0xFF4A148C), Color(0xFF1A0A2E)))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (p.pic.isNotEmpty()) {
                                    AsyncImage(model = p.pic, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
                                } else {
                                    Text(p.name.take(1).uppercase(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            // Screen share badge
                            if (screenShareUserId == p.userId) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(Color(0xFF9C27B0)),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.ScreenShare, null, tint = Color.White, modifier = Modifier.size(10.dp)) }
                            }
                        }

                        // Name + status
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    p.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (hasRaisedHand) Text("✋", fontSize = 13.sp)
                            }
                            Text(
                                when {
                                    isMe -> "this is you"
                                    isSpeaking -> "speaking"
                                    else -> "listening"
                                },
                                color = when {
                                    isMe -> VcBlue
                                    isSpeaking -> VcAccent
                                    else -> Color(0xFF666666)
                                },
                                fontSize = 12.sp
                            )
                        }

                        // Mic icon (right side)
                        Icon(
                            if (p.muted) Icons.Default.MicOff else Icons.Default.Mic,
                            null,
                            tint = if (p.muted) Color(0xFF555555) else VcGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Invite Members row (Telegram style)
                item {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                // Share voice chat invite link
                                val inviteLink = "https://api.picrypto.in/vc/${viewModel.getCurrentRoomId()}"
                                val shareText = "🎙️ Join my Voice Chat on YT-Booster!\n\n" +
                                    "Group: $roomName\n" +
                                    "Join here: $inviteLink\n\n" +
                                    "App nahi hai? Download karo:\nhttps://api.picrypto.in/download/YT-Booster.apk"
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "Share Voice Chat Link"))
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF1A1A2E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PersonAdd, null, tint = Color(0xFF666666), modifier = Modifier.size(22.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Invite Members", color = Color(0xFF9E9E9E), fontSize = 15.sp)
                            Text("Link share karo", color = Color(0xFF555555), fontSize = 11.sp)
                        }
                        Icon(Icons.Default.Share, null, tint = Color(0xFF555555), modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ── Bottom Controls (Telegram style) ─────────────
            Column(
                Modifier.fillMaxWidth()
                    .background(Color(0xFF111122))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speaker / Bluetooth
                    VcControlBtn(
                        icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                        label = if (isSpeakerOn) "Speaker" else "Earpiece",
                        bg = Color(0xFF1A2A3A),
                        tint = VcBlue,
                        size = 56
                    ) { viewModel.toggleSpeaker() }

                    // Camera (placeholder — voice only)
                    VcControlBtn(
                        icon = Icons.Default.VideocamOff,
                        label = "Camera",
                        bg = Color(0xFF1A1A2E),
                        tint = Color(0xFF555555),
                        size = 56
                    ) { /* video not supported in group voice */ }

                    // Mute (center, bigger)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(64.dp).clip(CircleShape)
                                .background(if (isMuted) Color(0xFF3A1A1A) else Color(0xFF1A3A1A))
                                .clickable { viewModel.toggleMute() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                null,
                                tint = if (isMuted) VcRed else VcGreen,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(if (isMuted) "Unmute" else "Mute", color = Color(0xFF9E9E9E), fontSize = 11.sp)
                    }

                    // Message (go back to chat)
                    VcControlBtn(
                        icon = Icons.Default.Message,
                        label = "Message",
                        bg = Color(0xFF1A1A2E),
                        tint = Color(0xFF9E9E9E),
                        size = 56
                    ) { onLeave() }

                    // Leave
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape)
                                .background(Color(0xFF8B0000))
                                .clickable { viewModel.leave(); onLeave() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Leave", color = VcRed, fontSize = 11.sp)
                    }
                }

                // Admin: End chat for everyone
                if (isAdmin) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { viewModel.adminEndVoiceChat(); onLeave() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("End Voice Chat for Everyone", color = VcRed, fontSize = 13.sp)
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 160.dp)
        )
    }

    // Admin action dialog
    adminTargetParticipant?.let { target ->
        val isHandRaised = raisedHands.contains(target.userId)
        androidx.compose.ui.window.Dialog(onDismissRequest = { adminTargetParticipant = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VcCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(VcAccent, Color(0xFF4A1A8A)))), contentAlignment = Alignment.Center) {
                            Text(target.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Column {
                            Text(target.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            if (isHandRaised) Text("✋ Hand raised", color = VcGold, fontSize = 11.sp)
                        }
                    }
                    Divider(color = Color(0xFF2A2A3E))
                    AdminActionRow(
                        icon = if (target.muted) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        label = if (target.muted) "Unmute" else "Mute",
                        color = if (target.muted) VcGreen else VcRed
                    ) { viewModel.adminMuteUser(target.userId, !target.muted); adminTargetParticipant = null }
                    AdminActionRow(Icons.Default.PersonRemove, "Voice Chat se Hatao", VcRed) {
                        viewModel.adminKickUser(target.userId); adminTargetParticipant = null
                    }
                    TextButton(onClick = { adminTargetParticipant = null }, modifier = Modifier.align(Alignment.End)) {
                        Text("Cancel", color = Color(0xFF9E9E9E))
                    }
                }
            }
        }
    }
}

@Composable
private fun VcControlBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    bg: Color,
    tint: Color,
    size: Int = 56,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.43f).dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color(0xFF9E9E9E), fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AdminActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(color.copy(0.1f)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Compact banner shown inside chat window when voice chat is active ──
@Composable
fun VoiceChatActiveBanner(
    roomName: String,
    participantCount: Int,
    isMuted: Boolean,
    onJoin: () -> Unit,
    onMuteToggle: () -> Unit,
    onLeave: () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "banner")
    val alpha by inf.animateFloat(0.5f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "ba")

    Row(
        Modifier.fillMaxWidth()
            .background(Color(0xFF1A237E))
            .clickable { onJoin() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Pulsing mic icon
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF3949AB).copy(alpha)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(roomName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$participantCount members • Tap to join", color = Color(0xFFBBBBFF), fontSize = 11.sp)
        }

        // Mute toggle
        Box(
            Modifier.size(32.dp).clip(CircleShape)
                .background(if (isMuted) Color(0xFF3A1A1A) else Color(0xFF1A3A1A))
                .clickable { onMuteToggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                null,
                tint = if (isMuted) Color(0xFFFF6B6B) else Color(0xFF4CAF50),
                modifier = Modifier.size(16.dp)
            )
        }

        // Leave
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF8B0000))
                .clickable { onLeave() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}
