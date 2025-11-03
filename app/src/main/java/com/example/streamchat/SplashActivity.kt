package com.example.streamchat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.example.streamchat.R
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SplashScreen {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        label = "alpha",
        animationSpec = tween(durationMillis = 2000)
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(3000)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 1. Apply the new 3-color gradient (270 angle is top-to-bottom)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F52BA), // startColor
                        Color(0xFF487BCA), // centerColor
                        Color(0xFFFFFFFF)  // endColor
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // 2. Add the semi-transparent black overlay on top of the gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f)) // This is #40000000
        )

        // 3. Place the Text on the very top
        Text(
            text = "temp.",
            modifier = Modifier.alpha(alphaAnim.value),
            style = TextStyle(
                fontFamily = FontFamily(Font(R.font.angkor_regular)),
                fontSize = 60.sp,
                color = Color.White,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.5f),
                    offset = androidx.compose.ui.geometry.Offset(4f, 4f),
                    blurRadius = 8f
                )
            )
        )
    }
}