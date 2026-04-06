package com.ytsubexchange.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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

private val VcBg     = Color(0xFF0D0D0D)
private val VcCard   = Color(0xFF1A1A2E)
private val VcAccent = Color(0xFF7B2FF7)
private val VcGreen  = Color(0xFF4CAF50)
private val VcRed    = Color(0xFFE53935)
private val VcMuted  = Color(0xFF616161)

@Composable
fun GroupVoiceChatScreen(
    roomName: String,
    viewModel: GroupVoiceChatViewModel,
    onLeave: () -> Unit
) {
    val participants by viewModel.participants.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val toastMsg by viewModel.toastMsg.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isAdmin = viewModel.isGroupAdmin()
    var adminTargetParticipant by remember { mutableStateOf<VoiceChatParticipant?>(null) }

    LaunchedEffect(toastMsg) {
        val msg = toastMsg
        if (msg != null) { snackbarHostState.showSnackbar(message = msg); viewModel.clearToast() }
    }

    Box(Modifier.fillMaxSize().background(VcBg).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            // ── Header ────────────────────────────────────────
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(VcCard, VcBg)))
                    .padding(16.dp, 12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Live indicator
                        val inf = rememberInfiniteTransition(label = "live")
                        val alpha by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
                        Box(Modifier.size(8.dp).clip(CircleShape).background(VcGreen.copy(alpha)))
                        Text("Voice Chat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(roomName, color = Color(0xFF9E9E9E), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text("${participants.size} participant${if (participants.size != 1) "s" else ""}",
                        color = VcAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Divider(color = Color(0xFF1E1E2E), thickness = 0.5.dp)

            // ── Participants Grid ─────────────────────────────
            if (participants.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎙️", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Koi nahi hai abhi", color = Color(0xFF555555), fontSize = 14.sp)
                        Text("Invite karo doston ko!", color = Color(0xFF444444), fontSize = 12.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(count = participants.size) { i ->
                        VoiceParticipantCard(
                            participant = participants[i],
                            isAdmin = isAdmin,
                            onAdminAction = { adminTargetParticipant = participants[i] }
                        )
                    }
                }
            }

            // ── Bottom Controls ───────────────────────────────
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f))))
                    .padding(bottom = 32.dp, top = 16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute/Unmute
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(60.dp).clip(CircleShape)
                                .background(if (isMuted) Color(0xFF3A1A1A) else Color(0xFF1A2A1A))
                                .clickable { viewModel.toggleMute() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                null,
                                tint = if (isMuted) VcRed else VcGreen,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(if (isMuted) "Unmute" else "Mute", color = Color(0xFF9E9E9E), fontSize = 11.sp)
                    }

                    // Leave button
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

                    // Speaker toggle (placeholder — audio always on speaker in group voice)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(60.dp).clip(CircleShape).background(Color(0xFF1A2A3A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VolumeUp, null, tint = Color(0xFF29B6F6), modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Speaker", color = Color(0xFF9E9E9E), fontSize = 11.sp)
                    }

                    // Admin: End Voice Chat
                    if (isAdmin) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(60.dp).clip(CircleShape).background(Color(0xFF2A1A1A))
                                    .clickable { viewModel.adminEndVoiceChat(); onLeave() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Close, null, tint = VcRed, modifier = Modifier.size(26.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("End Chat", color = VcRed, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }

    // Admin action dialog
    adminTargetParticipant?.let { target ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { adminTargetParticipant = null }) {
            androidx.compose.material3.Card(
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = VcCard),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("🛡️ Admin: ${target.name}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(12.dp))
                    // Mute/Unmute
                    Box(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(if (target.muted) Color(0xFF1A3A1A) else Color(0xFF3A1A1A))
                        .clickable {
                            viewModel.adminMuteUser(target.userId, !target.muted)
                            adminTargetParticipant = null
                        }.padding(12.dp)) {
                        Text(if (target.muted) "🔊 Unmute" else "🔇 Mute", color = if (target.muted) VcGreen else VcRed, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    // Kick
                    Box(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A1A1A))
                        .clickable {
                            viewModel.adminKickUser(target.userId)
                            adminTargetParticipant = null
                        }.padding(12.dp)) {
                        Text("❌ Voice Chat se Hatao", color = VcRed, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.TextButton(onClick = { adminTargetParticipant = null }, modifier = Modifier.align(Alignment.End)) {
                        Text("Cancel", color = Color(0xFF9E9E9E))
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun VoiceParticipantCard(participant: VoiceChatParticipant, isAdmin: Boolean = false, onAdminAction: (() -> Unit)? = null) {
    val inf = rememberInfiniteTransition(label = "speak")
    val ringScale by inf.animateFloat(
        1f, 1.12f,
        infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Speaking ring (always subtle for now — can add audio level detection later)
            if (!participant.muted) {
                Box(
                    Modifier.size((56 * ringScale).dp).clip(CircleShape)
                        .background(VcAccent.copy(alpha = 0.15f))
                )
            }
            // Avatar
            Box(
                Modifier.size(52.dp).clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            if (participant.muted)
                                listOf(Color(0xFF333333), Color(0xFF222222))
                            else
                                listOf(VcAccent, Color(0xFF4A1A8A))
                        )
                    )
                    .then(if (isAdmin && onAdminAction != null)
                        Modifier.combinedClickable(onClick = {}, onLongClick = onAdminAction)
                    else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (participant.pic.isNotEmpty()) {
                    AsyncImage(
                        model = participant.pic,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Text(
                        participant.name.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            // Mute badge
            if (participant.muted) {
                Box(
                    Modifier.align(Alignment.BottomEnd)
                        .size(18.dp).clip(CircleShape)
                        .background(Color(0xFF1A1A1A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MicOff, null, tint = VcRed, modifier = Modifier.size(11.dp))
                }
            } else {
                Box(
                    Modifier.align(Alignment.BottomEnd)
                        .size(18.dp).clip(CircleShape)
                        .background(VcGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(11.dp))
                }
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
    }
}
