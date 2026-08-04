package com.sorimpower.app.feature.blocker.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sorimpower.app.data.BlockerRepository
import com.sorimpower.app.ui.SorimPowerTheme

private val WarningRed = Color(0xFFFF3B30)
private val WarningOrange = Color(0xFFFF9D3D)
private val WarningBackground = Color(0xFF120B0D)

class BlockedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "이 앱"
        val todayCount = intent.getIntExtra(EXTRA_TODAY_COUNT, 1).coerceAtLeast(1)
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: BlockerRepository.DEFAULT_BLOCK_MESSAGE
        setContent {
            SorimPowerTheme {
                BackHandler(onBack = ::returnHome)
                BlockedWarningScreen(appName, todayCount, message, ::returnHome)
            }
        }
        window.decorView.postDelayed(::returnHome, 5_000)
    }

    private fun returnHome() {
        if (isFinishing) return
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_TODAY_COUNT = "today_count"
        const val EXTRA_MESSAGE = "block_message"
    }
}

@Composable
private fun BlockedWarningScreen(
    appName: String,
    todayCount: Int,
    message: String,
    returnHome: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2A0B10), WarningBackground, Color(0xFF080708)),
                ),
            )
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            Modifier
                .border(1.dp, WarningRed.copy(alpha = .7f), CircleShape)
                .background(WarningRed.copy(alpha = .12f), CircleShape)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).background(WarningRed, CircleShape))
            Text("접근이 차단되었습니다", color = Color.White, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier
                .size(78.dp)
                .border(3.dp, WarningRed, CircleShape)
                .background(WarningRed.copy(alpha = .15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("!", color = WarningRed, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "$appName 중단",
            color = Color.White,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(message, color = Color.White.copy(alpha = .72f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(30.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF251C1F)),
            elevation = CardDefaults.cardElevation(10.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("오늘", color = WarningOrange, fontWeight = FontWeight.Bold)
                Text(
                    "${todayCount}번째",
                    color = Color.White,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    if (todayCount >= 5) "반복해서 앱을 열고 있어요. 지금 멈춰주세요." else "이 앱을 실행하려고 했어요.",
                    color = Color.White.copy(alpha = .7f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = returnHome,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WarningRed, contentColor = Color.White),
        ) {
            Text("홈으로 돌아가기", fontWeight = FontWeight.Black)
        }
        Text(
            "5초 후 자동으로 홈 화면으로 이동합니다.",
            Modifier.padding(top = 12.dp),
            color = Color.White.copy(alpha = .45f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
