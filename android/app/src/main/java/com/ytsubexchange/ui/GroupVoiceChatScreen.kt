package com.ytsubexchange.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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

    LaunchedEffect(toastMsg) {
        val msg = toastMsg
        if (msg != null) { snackbarHostState.showSnackbar(message = msg); viewModel.clearToast() }
    }

    Box(Modifier.fillMaxSize().background(VcBg).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF1A1A3E), VcBg)))
                    .padding(16.dp, 16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    // Live pulse dot
                    val inf = rememberInfiniteTransition(label = "live")
                    val alpha by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(VcGreen.copy(alpha)))
                        Text("Voice Chat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(roomName, color = Color(0xFF9E9E9E), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(VcAccent.copy(0.2f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("${participants.size} participants", color = VcAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (raisedHands.isNotEmpty()) {
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(VcGold.copy(0.2f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("✋ ${raisedHands.size}", color = VcGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Divider(color = Color(0xFF1E1E2E), thickness = 0.5.dp)

            // ── Participants Grid ─────────────────────────────
            if (participants.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🎙️", fontSize = 52.sp)
                        Text("Koi nahi hai abhi", color = Color(0xFF555555), fontSize = 15.sp)
                        Text("Doston ko invite karo!", color = Color(0xFF444444), fontSize = 12.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(count = participants.size) { i ->
                        val p = participants[i]
                        VoiceParticipantCard(
                            participant = p,
                            isAdmin = isAdmin,
                            hasRaisedHand = raisedHands.contains(p.userId),
                            isScreenSharing = screenShareUserId == p.userId,
                            onAdminAction = { adminTargetParticipant = p }
                        )
                    }
                }
            }

            // ── Bottom Controls ───────────────────────────────
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.95f))))
                    .padding(bottom = 28.dp, top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Row 1: Main controls
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute
                    VcControlBtn(
                        icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (isMuted) "Unmute" else "Mute",
                        bg = if (isMuted) Color(0xFF3A1A1A) else Color(0xFF1A2A1A),
                        tint = if (isMuted) VcRed else VcGreen,
                        size = 60
                    ) { viewModel.toggleMute() }

                    // Leave (big red)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(68.dp).clip(CircleShape)
                                .background(Brush.radialGradient(listOf(VcRed, Color(0xFFB71C1C))))
                                .clickable { viewModel.leave(); onLeave() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Leave", color = VcRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Speaker
                    VcControlBtn(
                        icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                        label = if (isSpeakerOn) "Speaker" else "Earpiece",
                        bg = if (isSpeakerOn) Color(0xFF1A2A3A) else Color(0xFF2A2A2A),
                        tint = if (isSpeakerOn) VcBlue else Color(0xFF9E9E9E),
                        size = 60
                    ) { viewModel.toggleSpeaker() }
                }

                Spacer(Modifier.height(12.dp))

                // Row 2: Secondary controls
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Raise hand
                    VcControlBtn(
                        icon = Icons.Default.PanTool,
                        label = if (myHandRaised) "Lower Hand" else "Raise Hand",
                        bg = if (myHandRaised) Color(0xFF2A2A00) else Color(0xFF1A1A2E),
                        tint = if (myHandRaised) VcGold else Color(0xFF9E9E9E),
                        size = 50
                    ) {
                        myHandRaised = !myHandRaised
                        viewModel.raiseHand(myHandRaised)
                    }

                    // Admin: End chat
                    if (isAdmin) {
                        VcControlBtn(
                            icon = Icons.Default.Close,
                            label = "End Chat",
                            bg = Color(0xFF2A1A1A),
                            tint = VcRed,
                            size = 50
                        ) { viewModel.adminEndVoiceChat(); onLeave() }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp)
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
        Spacer(Modifier.height(5.dp))
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun VoiceParticipantCard(
    participant: VoiceChatParticipant,
    isAdmin: Boolean = false,
    hasRaisedHand: Boolean = false,
    isScreenSharing: Boolean = false,
    onAdminAction: (() -> Unit)? = null
) {
    val inf = rememberInfiniteTransition(label = "speak")
    val ringScale by inf.animateFloat(
        1f, 1.15f,
        infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Speaking ring
            if (!participant.muted) {
                Box(
                    Modifier.size((58 * ringScale).dp).clip(CircleShape)
                        .background(VcAccent.copy(alpha = 0.18f))
                )
                Box(
                    Modifier.size((54 * ringScale).dp).clip(CircleShape)
                        .background(VcAccent.copy(alpha = 0.10f))
                )
            }

            // Avatar
            Box(
                Modifier.size(54.dp).clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            if (participant.muted)
                                listOf(Color(0xFF2A2A2A), Color(0xFF1A1A1A))
                            else
                                listOf(VcAccent, Color(0xFF4A1A8A))
                        )
                    )
                    .then(
                        if (isAdmin && onAdminAction != null)
                            Modifier.combinedClickable(onClick = {}, onLongClick = onAdminAction)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (participant.pic.isNotEmpty()) {
                    AsyncImage(model = participant.pic, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
                } else {
                    Text(participant.name.take(1).uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Raised hand badge (top-left)
            if (hasRaisedHand) {
                Box(
                    Modifier.align(Alignment.TopStart).size(20.dp).clip(CircleShape).background(VcGold),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✋", fontSize = 10.sp)
                }
            }

            // Screen share badge (top-right of avatar)
            if (isScreenSharing) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(Color(0xFF9C27B0)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ScreenShare, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }

            // Mic badge (bottom-right)
            Box(
                Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape)
                    .background(if (participant.muted) Color(0xFF1A1A1A) else VcGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (participant.muted) Icons.Default.MicOff else Icons.Default.Mic,
                    null,
                    tint = if (participant.muted) VcRed else Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            participant.name,
            color = if (participant.muted) Color(0xFF666666) else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (!participant.muted) {
            Text("speaking", color = VcAccent.copy(0.7f), fontSize = 9.sp)
        }
    }
}
