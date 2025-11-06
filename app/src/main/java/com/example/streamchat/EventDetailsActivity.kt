package com.example.streamchat

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
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
import io.getstream.chat.android.compose.ui.theme.ChatTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class EventDetailsActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val eventId = intent.getStringExtra(KEY_EVENT_ID) ?: run {
            finish()
            return
        }
        
        val repository = ChatRepository.getInstance(applicationContext)
        
        setContent {
            ChatTheme {
                EventDetailsScreen(
                    eventId = eventId,
                    repository = repository,
                    onBack = { finish() },
                    onShare = { link, name ->
                        shareEventLink(link, name)
                    },
                    onOpenChannel = { channelCid ->
                        startActivity(MessageListActivity.createIntent(this, channelCid, ""))
                        finish()
                    }
                )
            }
        }
    }
    
    private fun shareEventLink(link: String, eventName: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Join my event: $eventName")
            putExtra(Intent.EXTRA_TEXT, """
                You're invited to $eventName!
                
                Join here: $link
                
                Download the temp. app to participate.
            """.trimIndent())
        }
        startActivity(Intent.createChooser(shareIntent, "Share event via"))
    }
    
    companion object {
        private const val KEY_EVENT_ID = "event_id"
        
        fun createIntent(context: android.content.Context, eventId: String): Intent {
            return Intent(context, EventDetailsActivity::class.java).apply {
                putExtra(KEY_EVENT_ID, eventId)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailsScreen(
    eventId: String,
    repository: ChatRepository,
    onBack: () -> Unit,
    onShare: (String, String) -> Unit,
    onOpenChannel: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var event by remember { mutableStateOf<com.example.streamchat.data.model.Event?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
    
    LaunchedEffect(eventId) {
        try {
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            if (firebaseUser == null) {
                errorMessage = "Not logged in"
                return@LaunchedEffect
            }
            
            val idToken = firebaseUser.getIdToken(false).await().token
            if (idToken == null) {
                errorMessage = "Failed to get authentication token"
                return@LaunchedEffect
            }
            
            val response = repository.getEventDetails(eventId, idToken)
            if (response.success && response.event != null) {
                event = response.event
            } else {
                errorMessage = "Event not found"
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to load event"
        } finally {
            isLoading = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (event != null) {
                        IconButton(onClick = {
                            onShare(event!!.joinLink, event!!.name)
                        }) {
                            Icon(Icons.Default.Share, "Share")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF0F52BA)
                    )
                }
                errorMessage != null -> {
                    Text(
                        text = errorMessage!!,
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                event != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = event!!.name,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F52BA)
                        )
                        
                        if (event!!.description.isNotBlank()) {
                            Text(
                                text = event!!.description,
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                        }
                        
                        if (event!!.eventDate != null) {
                            Text(
                                text = "📅 ${dateFormat.format(Date(event!!.eventDate!!))}",
                                fontSize = 14.sp,
                                color = Color.DarkGray
                            )
                        }
                        
                        Text(
                            text = "👥 ${event!!.memberCount} participants",
                            fontSize = 14.sp,
                            color = Color.DarkGray
                        )
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Text(
                            text = "Share Link:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Surface(
                            color = Color(0xFFF0F0F0),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = event!!.joinLink,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 12.sp,
                                color = Color(0xFF0F52BA)
                            )
                        }
                        
                        Spacer(Modifier.weight(1f))
                        
                        Button(
                            onClick = {
                                if (event!!.channelCid.isNotBlank()) {
                                    onOpenChannel(event!!.channelCid)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0F52BA)
                            )
                        ) {
                            Text("Open Event Channel")
                        }
                    }
                }
            }
        }
    }
}
