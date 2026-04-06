package com.ytsubexchange.test.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ytsubexchange.test.ui.theme.AppColors
import com.ytsubexchange.test.viewmodel.MainViewModel
import com.ytsubexchange.test.viewmodel.QueueState

@Composable
fun MatchCard(state: QueueState.Matched, viewModel: MainViewModel, dark: Boolean = true) {
    val context = LocalContext.current
    val match = state.match
    var subscribeClicked by remember { mutableStateOf(false) }
    val card = AppColors.card(dark)
    val textColor = AppColors.text(dark)
    val textSecondary = AppColors.textSecondary(dark)

    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Match Mila!", color = Color(0xFFFF0000), fontWeight = FontWeight.Bold, fontSize = 20.sp)

            AsyncImage(model = match.profilePic, contentDescription = null, modifier = Modifier.size(72.dp).clip(CircleShape))
            Text(match.channelName, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("+${match.coinsReward} Coin${if (match.coinsReward > 1) "s" else ""} milenge", color = Color(0xFFFFD700), fontSize = 13.sp, fontWeight = FontWeight.Bold)

            if (state.partnerConfirmed) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (dark) Color(0xFF1A2A1A) else Color(0xFFE8F5E9)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${match.channelName} ne subscribe kar liya!", color = Color(0xFF4CAF50), fontSize = 13.sp, modifier = Modifier.padding(10.dp))
                }
            }

            Button(
                onClick = {
                    subscribeClicked = true
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(match.channelUrl)))
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (subscribeClicked) AppColors.cardAlt(dark) else Color(0xFFFF0000)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (subscribeClicked) "Subscribe kiya - Confirm karo" else "Subscribe Karo", color = if (subscribeClicked) textColor else Color.White)
            }

            if (subscribeClicked) {
                Button(
                    onClick = { viewModel.confirmSubscribe(match.matchId) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Confirm & Coin Lo", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            TextButton(onClick = { viewModel.leaveQueue() }) {
                Text("Skip / Cancel", color = textSecondary, fontSize = 12.sp)
            }
        }
    }
}
