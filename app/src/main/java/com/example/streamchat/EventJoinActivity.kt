package com.example.streamchat

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.streamchat.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.example.streamchat.ui.startActivityWithSlide
import com.example.streamchat.ui.finishWithSlide
import io.getstream.chat.android.compose.ui.theme.ChatTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class EventJoinActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val repository = ChatRepository.getInstance(applicationContext)
        
        // Parse event ID from deep link: temp://event/{eventId}
        val eventId = intent.data?.lastPathSegment
        val fullLink = intent.data?.toString()
        
        if (eventId == null) {
            Toast.makeText(this, "Invalid event link", Toast.LENGTH_SHORT).show()
            finishWithSlide()
            return
        }
        
        // Check if user is logged in
        if (!repository.isLoggedIn()) {
            // Save pending event link and redirect to login
            if (fullLink != null) {
                repository.savePendingEventLink(fullLink)
            }
            startActivityWithSlide(Intent(this, FirebaseAuthActivity::class.java))
            finishWithSlide()
            return
        }
        
        setContent {
            ChatTheme {
                EventJoinScreen(
                    eventId = eventId,
                    repository = repository,
                    onJoinSuccess = { channelCid ->
                        // Navigate directly into the channel message list when join succeeds
                        val intent = MessageListActivity.createIntent(this, channelCid ?: eventId, "")
                        startActivityWithSlide(intent)
                        finishWithSlide()
                    },
                    onError = { message ->
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        finishWithSlide()
                    }
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun EventJoinScreen(
    eventId: String,
    repository: ChatRepository,
    onJoinSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isJoining by remember { mutableStateOf(false) }
    var eventDetails by remember { mutableStateOf<com.example.streamchat.data.model.Event?>(null) }
    var isLoadingDetails by remember { mutableStateOf(true) }
    
    LaunchedEffect(eventId) {
        // Load event details first and attempt to auto-join if possible
        try {
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            if (firebaseUser == null) {
                onError("Not logged in")
                return@LaunchedEffect
            }

            val idToken = firebaseUser.getIdToken(false).await().token
            if (idToken == null) {
                onError("Failed to get authentication token")
                return@LaunchedEffect
            }

            val response = repository.getEventDetails(eventId, idToken)
            if (response.success && response.event != null) {
                eventDetails = response.event

                // Attempt to auto-join immediately
                isJoining = true
                try {
                    val joinResp = repository.joinEvent(eventId, idToken)
                    if (joinResp.success) {
                        // If server returned a channelCid, navigate to the channel directly
                        onJoinSuccess(joinResp.channelCid ?: joinResp.channelId ?: eventId)
                        return@LaunchedEffect
                    } else {
                        // Fall through to show manual join UI
                    }
                } catch (e: Exception) {
                    // Auto-join failed; show UI so user can retry
                } finally {
                    isJoining = false
                }
            } else {
                onError("Event not found")
            }
        } catch (e: Exception) {
            onError(e.message ?: "Failed to load event")
        } finally {
            isLoadingDetails = false
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        if (isLoadingDetails) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF0F52BA))
            }
        } else if (eventDetails != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Join Event",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F52BA)
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = eventDetails!!.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                
                if (eventDetails!!.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = eventDetails!!.description,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    text = "${eventDetails!!.memberCount} participants",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                
                Spacer(Modifier.height(32.dp))
                
                Button(
                    onClick = {
                        isJoining = true
                        scope.launch {
                            try {
                                val firebaseUser = FirebaseAuth.getInstance().currentUser
                                if (firebaseUser == null) {
                                    onError("Not logged in")
                                    return@launch
                                }
                                
                                val idToken = firebaseUser.getIdToken(false).await().token
                                if (idToken == null) {
                                    onError("Failed to get authentication token")
                                    return@launch
                                }
                                
                                val response = repository.joinEvent(eventId, idToken)
                                if (response.success && response.channelCid != null) {
                                    onJoinSuccess(response.channelCid)
                                } else {
                                    onError(response.error ?: "Failed to join event")
                                }
                            } catch (e: Exception) {
                                onError(e.message ?: "An error occurred")
                            } finally {
                                isJoining = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isJoining,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0F52BA)
                    )
                ) {
                    if (isJoining) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isJoining) "Joining..." else "Join Event", fontSize = 16.sp)
                }
            }
        }
    }
}
