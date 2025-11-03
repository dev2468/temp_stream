package com.example.streamchat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.example.streamchat.data.repository.ChatRepository
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.compose.ui.theme.ChatTheme
import io.getstream.chat.android.models.InitializationState
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val repository = ChatRepository.getInstance(applicationContext)
        val client = ChatClient.instance()

        findViewById<ComposeView>(R.id.composeView).setContent {
            ChatTheme {
                SplashScreen(
                    client = client,
                    isLoggedIn = repository.isLoggedIn(),
                    onNavigateToLogin = {
                        startActivity(Intent(this, FirebaseAuthActivity::class.java))
                        finish()
                    },
                    onNavigateToChannelList = {
                        startActivity(Intent(this, ChannelListActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun SplashScreen(
    client: ChatClient,
    isLoggedIn: Boolean,
    onNavigateToLogin: () -> Unit,
    onNavigateToChannelList: () -> Unit
) {
    val clientState by client.clientState.initializationState.collectAsState()

    LaunchedEffect(clientState, isLoggedIn) {
        delay(500) // Small delay for splash effect
        when {
            !isLoggedIn -> onNavigateToLogin()
            clientState == InitializationState.COMPLETE -> onNavigateToChannelList()
            clientState == InitializationState.NOT_INITIALIZED -> onNavigateToLogin()
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                when (clientState) {
                    InitializationState.COMPLETE -> "Connected!"
                    InitializationState.INITIALIZING -> "Connecting..."
                    else -> "Initializing..."
                }
            )
        }
    }
}

