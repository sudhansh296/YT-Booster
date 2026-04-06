package com.ytsubexchange.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytsubexchange.ui.theme.AppColors
import com.ytsubexchange.ui.theme.isDarkTheme
import com.ytsubexchange.viewmodel.MainViewModel

@Composable
fun ReferralScreen(viewModel: MainViewModel) {
    val referral by viewModel.referral.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var codeInput by remember { mutableStateOf("") }
    var applyMsg by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var codeCopied by remember { mutableStateOf(false) }
    var linkCopied by remember { mutableStateOf(false) }

    val dark by isDarkTheme
    val bg = AppColors.bg(dark)
    val card = AppColors.card(dark)
    val cardAlt = AppColors.cardAlt(dark)
    val textColor = AppColors.text(dark)
    val textSecondary = AppColors.textSecondary(dark)

    var isLoadError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoadError = false
        try {
            viewModel.loadReferral()
        } catch (e: Exception) { isLoadError = true }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(bg).statusBarsPadding().verticalScroll(rememberScrollState())
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎁 Referral", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = if (dark) Color(0xFF1A1A3A) else Color(0xFFE8EAF6)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Dost ko invite karo 🚀", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("• Tumhe milega: +20 Coins", color = textColor, fontSize = 14.sp)
                    Text("• Dost ko milega: +20 Coins", color = textColor, fontSize = 14.sp)
                    Text("• Jitne dost, utne coins!", color = Color(0xFF4CAF50), fontSize = 13.sp)
                }
            }

            referral?.let { ref ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = card),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Mera Referral Code", color = textSecondary, fontSize = 13.sp)

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(ref.referralCode, color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 4.sp)
                            Button(
                                onClick = { clipboard.setText(AnnotatedString(ref.referralCode)); codeCopied = true },
                                colors = ButtonDefaults.buttonColors(containerColor = cardAlt),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(if (codeCopied) "✓ Copied" else "Copy Code", color = textColor, fontSize = 12.sp)
                            }
                        }

                        Divider(color = cardAlt)

                        if (ref.referralLink.isNotEmpty()) {
                            Text("Referral Link", color = textSecondary, fontSize = 12.sp)
                            Text(ref.referralLink, color = Color(0xFF29B6F6), fontSize = 12.sp, maxLines = 1)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { clipboard.setText(AnnotatedString(ref.referralLink)); linkCopied = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF29B6F6)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF29B6F6)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) { Text(if (linkCopied) "✓ Copied" else "Copy Link", fontSize = 12.sp) }
                                Button(
                                    onClick = {
                                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, "YT-Booster se free subscribers pao! 🚀\nMere referral link se join karo aur +20 coins pao:\n${ref.referralLink}")
                                        }
                                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share via"))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) { Text("Share 📤", color = Color.White, fontSize = 12.sp) }
                            }
                        }

                        Divider(color = cardAlt)

                        Text("Referral Stats", color = textSecondary, fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                Triple("Aaj", ref.stats.today, Color(0xFF4CAF50)),
                                Triple("Week", ref.stats.thisWeek, Color(0xFF29B6F6)),
                                Triple("Month", ref.stats.thisMonth, Color(0xFFFF9800)),
                                Triple("Total", ref.stats.total, Color(0xFFFFD700))
                            ).forEach { (label, value, color) ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = cardAlt),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("$value", color = color, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                        Text(label, color = textSecondary, fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        Divider(color = cardAlt)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${ref.referralEarned}", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                Text("Total Coins Earned", color = textSecondary, fontSize = 12.sp)
                            }
                        }

                        // Milestones section
                        if (ref.milestones.isNotEmpty()) {
                            Divider(color = cardAlt)
                            Text("🏆 Milestone Bonuses", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            ref.milestones.forEach { m ->
                                val progress = if (m.reached) 1f else (ref.referralCount.toFloat() / m.count.toFloat()).coerceIn(0f, 1f)
                                val statusColor = when {
                                    m.claimed -> Color(0xFF4CAF50)
                                    m.reached -> Color(0xFFFFD700)
                                    else -> Color(0xFF555555)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                when { m.claimed -> "✅"; m.reached -> "🎁"; else -> "🔒" },
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                "${m.count} Referrals",
                                                color = if (m.claimed || m.reached) textColor else Color(0xFF555555),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                        }
                                        Text(
                                            if (m.claimed) "Claimed ✓" else "+${m.bonus} Coins",
                                            color = statusColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = progress,
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = statusColor,
                                        trackColor = cardAlt
                                    )
                                    if (!m.reached) {
                                        Text(
                                            "${ref.referralCount}/${m.count} (${m.count - ref.referralCount} aur chahiye)",
                                            color = Color(0xFF444444),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } ?: Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                if (isLoadError) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Data load nahi hua", color = Color(0xFFFF6B6B), fontSize = 14.sp)
                        Button(
                            onClick = { viewModel.loadReferral() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Retry", color = Color.White) }
                    }
                } else {
                    CircularProgressIndicator(color = Color(0xFFFF0000))
                }
            }

            if (referral?.alreadyReferred == true || referral?.adminCodeUsed != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (dark) Color(0xFF0D2A0D) else Color(0xFFE8F5E9)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("✅", fontSize = 24.sp)
                        Column {
                            Text("Referral Code Use Ho Gaya!", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (referral?.adminCodeUsed != null) {
                                Text("Tum sub-admin (${referral!!.adminCodeUsed}) ke through aaye ho", color = Color(0xFF4CAF50).copy(alpha = 0.7f), fontSize = 12.sp)
                            } else {
                                Text("Tumne pehle se ek code use kar liya hai", color = Color(0xFF4CAF50).copy(alpha = 0.7f), fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = card),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Kisi ka code use karo", color = textSecondary, fontSize = 13.sp)
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { codeInput = it.uppercase() },
                            placeholder = { Text("Referral code daalo", color = AppColors.textSecondary(dark)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFF0000),
                                unfocusedBorderColor = cardAlt,
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                cursorColor = textColor
                            ),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        if (applyMsg.isNotEmpty()) {
                            Text(applyMsg, color = if (applyMsg.contains("+")) Color(0xFF4CAF50) else Color(0xFFFF6B6B), fontSize = 13.sp)
                        }
                        Button(
                            onClick = {
                                if (codeInput.length >= 4) {
                                    isLoading = true
                                    viewModel.applyReferralCode(codeInput) { success, msg ->
                                        isLoading = false; applyMsg = msg
                                        if (success) { codeInput = ""; viewModel.loadReferral() }
                                    }
                                }
                            },
                            enabled = codeInput.length >= 4 && !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text("Apply Code (+20 Coins)", color = Color.White)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
