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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sorimpower.app.feature.blocker.data.BlockerRepository
import com.sorimpower.app.feature.blocker.service.BlockedLaunchSession
import com.sorimpower.app.core.ui.SorimPowerTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

private val WarningRed = Color(0xFFFF3B30)
private val WarningOrange = Color(0xFFFF9D3D)
private val WarningBackground = Color(0xFF120B0D)

class BlockedActivity : ComponentActivity() {
    private var blockDetails by mutableStateOf(BlockDetails())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blockDetails = intent.toBlockDetails()
        setContent {
            SorimPowerTheme {
                val details = blockDetails
                BackHandler(onBack = ::returnHome)
                BlockedWarningScreen(details.appName, details.todayCount, details.message, ::returnHome) {
                    allowOneTimeUse(details.packageName)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        blockDetails = intent.toBlockDetails()
    }

    override fun onDestroy() {
        BlockedLaunchSession.finish(blockDetails.packageName)
        super.onDestroy()
    }

    private fun Intent.toBlockDetails() = BlockDetails(
        appName = getStringExtra(EXTRA_APP_NAME) ?: "이 앱",
        packageName = getStringExtra(EXTRA_PACKAGE_NAME).orEmpty(),
        todayCount = getIntExtra(EXTRA_TODAY_COUNT, 1).coerceAtLeast(1),
        message = getStringExtra(EXTRA_MESSAGE) ?: BlockerRepository.DEFAULT_BLOCK_MESSAGE,
    )

    private fun allowOneTimeUse(packageName: String) {
        if (packageName.isBlank()) {
            returnHome()
            return
        }
        BlockedLaunchSession.finish(packageName)
        lifecycleScope.launch {
            BlockerRepository(applicationContext).allowNextLaunch(packageName)
            packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
                startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            finishAndRemoveTask()
        }
    }

    private fun returnHome() {
        if (isFinishing) return
        BlockedLaunchSession.finish(blockDetails.packageName)
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
        finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_TODAY_COUNT = "today_count"
        const val EXTRA_MESSAGE = "block_message"
    }
}

private data class BlockDetails(
    val appName: String = "이 앱",
    val packageName: String = "",
    val todayCount: Int = 1,
    val message: String = BlockerRepository.DEFAULT_BLOCK_MESSAGE,
)

@Composable
private fun BlockedWarningScreen(
    appName: String,
    todayCount: Int,
    message: String,
    returnHome: () -> Unit,
    allowTemporaryUse: () -> Unit,
) {
    var showEmergencyUnlock by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2A0B10), WarningBackground, Color(0xFF080708)),
                ),
            )
            .verticalScroll(rememberScrollState())
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
        Spacer(Modifier.height(18.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = .16f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, WarningOrange.copy(alpha = .65f)),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text("나에게 남긴 메시지", color = WarningOrange, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Text(
                    message,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Start,
                )
            }
        }
        Spacer(Modifier.height(22.dp))
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
        OutlinedButton(
            onClick = { showEmergencyUnlock = true },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp),
        ) { Text("정말 사용해야 해요", color = Color.White) }
    }
    if (showEmergencyUnlock) {
        EmergencyUnlockDialog(
            onDismiss = { showEmergencyUnlock = false },
            onUnlock = allowTemporaryUse,
        )
    }
}

@Composable
private fun EmergencyUnlockDialog(onDismiss: () -> Unit, onUnlock: () -> Unit) {
    val challenge = remember {
        val sentence = "나는 이 앱이 지금 꼭 필요한 이유를 생각했고, 필요한 일만 마친 뒤 바로 앱을 종료하겠습니다. "
        buildString { while (length < 100) append(sentence) }.take(100)
    }
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이번 한 번만 사용", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text("아래 100자를 정확히 따라 입력하면 이번 실행만 통과할 수 있어요.")
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Text(challenge, Modifier.padding(14.dp), fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(100) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    minLines = 4,
                    label = { Text("따라 입력하기") },
                    supportingText = { Text("${typed.length}/100") },
                    isError = typed.isNotEmpty() && !challenge.startsWith(typed),
                )
            }
        },
        confirmButton = {
            Button(onClick = onUnlock, enabled = typed == challenge) { Text("이번 한 번 사용") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("취소") } },
    )
}
