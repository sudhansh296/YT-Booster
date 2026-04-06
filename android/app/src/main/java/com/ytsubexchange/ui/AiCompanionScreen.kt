package com.ytsubexchange.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AiCompanionScreen(token: String) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF0D0D0D)).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🤖", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text("AI Companion", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF7B2FF7))
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text("Coming Soon 🚀", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Jaldi aa raha hai!\nYT Buddy aapka personal AI companion hoga.",
            color = Color(0xFF9E9E9E),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}
