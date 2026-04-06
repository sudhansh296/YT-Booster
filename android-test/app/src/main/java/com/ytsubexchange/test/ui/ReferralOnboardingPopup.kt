package com.ytsubexchange.test.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ytsubexchange.test.viewmodel.MainViewModel

@Composable
fun ReferralOnboardingPopup(
    prefilledCode: String,          // "" = organic (ASK), non-empty = from referral link
    viewModel: MainViewModel,
    onDone: () -> Unit,
    alreadyEarned: Boolean = false, // referral link se aaya - coins already credited on server
    earnedCoins: String = "20"
) {
    // isFromLink = referral link se aaya, code locked hai
    val isFromLink = prefilledCode.isNotEmpty()

    var code by remember { mutableStateOf(prefilledCode) }
    var msg by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    // alreadyEarned = true means coins already credited, just show confirm then success
    var applied by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "scale"
    )

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF0D0D1A), Color(0xFF1A0A0A)))
                )
        ) {
            // Top gold-red glow bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFFF0000), Color(0xFFFFD700), Color(0xFFFF0000))
                        )
                    )
            )

            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Pulsing icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFFFFD700).copy(alpha = 0.3f), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (applied) "🎉" else "🎁", fontSize = 44.sp)
                }

                if (!applied) {
                    // ---- Input / Confirm screen ----
                    Text(
                        "Welcome to YT-Booster! 🚀",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )

                    // Coins badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFFFFD700).copy(alpha = 0.15f),
                                        Color(0xFFFF9800).copy(alpha = 0.15f)
                                    )
                                )
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🪙", fontSize = 18.sp)
                            Text(
                                "+20 Coins Free",
                                color = Color(0xFFFFD700),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    if (isFromLink) {
                        // Referral link se aaya - code locked, no skip
                        Text(
                            "Tumhare dost ne tumhe invite kiya!\nNeeche unka referral code hai — confirm karo aur +20 coins pao!",
                            color = Color(0xFF888888),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        // Locked code display (non-editable, styled box)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF111111))
                                .padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    code,
                                    color = Color(0xFFFFD700),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 6.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "🔒 Referral Code",
                                    color = Color(0xFF555555),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        // Organic user - editable input + skip allowed
                        Text(
                            "Kisi ne refer kiya? Unka code daalo\naur dono ko +20 coins milenge!",
                            color = Color(0xFF888888),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.uppercase().take(8) },
                            placeholder = { Text("Referral Code daalo", color = Color(0xFF444444)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFFD700),
                                unfocusedBorderColor = Color(0xFF2A2A2A),
                                focusedTextColor = Color(0xFFFFD700),
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFFFFD700),
                                focusedContainerColor = Color(0xFF111111),
                                unfocusedContainerColor = Color(0xFF0D0D0D)
                            ),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                letterSpacing = 4.sp
                            )
                        )
                    }

                    if (msg.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (msg.contains("+"))
                                        Color(0xFF4CAF50).copy(alpha = 0.15f)
                                    else
                                        Color(0xFFFF6B6B).copy(alpha = 0.15f)
                                )
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                msg,
                                color = if (msg.contains("+")) Color(0xFF4CAF50) else Color(0xFFFF6B6B),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Apply / Confirm button
                    Button(
                        onClick = {
                            if (code.length >= 4) {
                                if (alreadyEarned) {
                                    // Coins already credited on server - just show success
                                    applied = true
                                } else {
                                    isLoading = true
                                    viewModel.applyReferralCode(code) { success, message ->
                                        isLoading = false
                                        if (success) {
                                            applied = true
                                        } else {
                                            msg = message
                                        }
                                    }
                                }
                            }
                        },
                        enabled = code.length >= 4 && !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (code.length >= 4 && !isLoading)
                                        Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFF9800)))
                                    else
                                        Brush.horizontalGradient(listOf(Color(0xFF333333), Color(0xFF333333)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    if (isFromLink) "✅ Confirm & Get +20 Coins" else "🪙 Apply & Get +20 Coins",
                                    color = if (code.length >= 4) Color.Black else Color(0xFF666666),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }

                    // Skip - sirf organic users ke liye
                    if (!isFromLink) {
                        TextButton(
                            onClick = onDone,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Skip karo →", color = Color(0xFF444444), fontSize = 13.sp)
                        }
                    }

                } else {
                    // ---- Success screen ----
                    Text(
                        "Code Apply Ho Gaya! 🎉",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.verticalGradient(listOf(Color(0xFF1A2A0A), Color(0xFF0D1A0A)))
                            )
                            .padding(horizontal = 32.dp, vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "+20",
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold,
                                fontSize = 48.sp
                            )
                            Text("Coins Mile! 🪙", color = Color(0xFF4CAF50).copy(alpha = 0.8f), fontSize = 16.sp)
                        }
                    }

                    Text(
                        "Tumhara referral code bhi ready hai!\nApne doston ko share karo aur aur coins kamao.",
                        color = Color(0xFF666666),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Button(
                        onClick = onDone,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(listOf(Color(0xFFFF0000), Color(0xFFCC0000)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("App Shuru Karo! 🚀", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}
