package com.geivia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GeiviaApp() }
    }
}

@Composable
private fun GeiviaApp() {
    val colors = darkColorScheme(
        primary = Color(0xFF8B7CFF),
        background = Color(0xFF0B1020),
        surface = Color(0xFF151D32)
    )

    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B1020)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF18264A), Color(0xFF0B1020), Color(0xFF090D18))
                        )
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "GEIVIA",
                        color = Color.White,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 5.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "CREATE  ·  CONNECT  ·  DEPLOY",
                        color = Color(0xFF9DAED4),
                        fontSize = 11.sp,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(40.dp))
                    Text(
                        text = "Seus projetos começam aqui.",
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Crie, configure e publique seus projetos Firebase sem complicação.",
                        color = Color(0xFFB7C2D9),
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = { },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Começar")
                    }
                }
            }
        }
    }
}
