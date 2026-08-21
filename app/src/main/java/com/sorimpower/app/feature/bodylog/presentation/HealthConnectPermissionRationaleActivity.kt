package com.sorimpower.app.feature.bodylog.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class HealthConnectPermissionRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PermissionRationale { finish() } }
    }
}

@Composable
private fun PermissionRationale(onClose: () -> Unit) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Health Connect 데이터 사용 안내", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("나잘알은 데일리 기록에 걸음 수, 거리, 활동 칼로리, 운동 시간을 표시하기 위해서만 Health Connect 활동 데이터를 읽습니다. 이 데이터는 기기 안에 저장되며 외부로 전송하지 않습니다.", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onClose, modifier = Modifier.padding(top = 24.dp)) { Text("확인") }
            }
        }
    }
}
