package com.example.streamchat

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.streamchat.data.repository.ChatRepository
import com.example.streamchat.ui.finishWithSlide
import com.example.streamchat.ui.startActivityWithSlide
import com.google.firebase.auth.FirebaseAuth
import io.getstream.chat.android.compose.ui.theme.ChatTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class CreateEventActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val repository = ChatRepository.getInstance(applicationContext)
        
        setContent {
            ChatTheme {
                CreateEventScreen(
                    repository = repository,
                    onBack = { finishWithSlide() },
                    onEventCreated = { eventId, _ ->
                        // Navigate to Event Details so the organizer can share the join link
                        if (eventId.isNotBlank()) {
                            startActivityWithSlide(EventDetailsActivity.createIntent(this, eventId))
                        } else {
                            Toast.makeText(this, "Event created, but no ID returned", Toast.LENGTH_LONG).show()
                        }
                        finishWithSlide()
                    }
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    repository: ChatRepository,
    onBack: () -> Unit,
    onEventCreated: (eventId: String, joinLink: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var eventName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var coverImage by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<Date?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Event", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Event Name
            OutlinedTextField(
                value = eventName,
                onValueChange = { eventName = it },
                label = { Text("Event Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = eventName.isBlank() && errorMessage != null
            )
            
            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )
            
            // Cover Image URL (optional)
            OutlinedTextField(
                value = coverImage,
                onValueChange = { coverImage = it },
                label = { Text("Cover Image URL (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // Date & Time Picker
            OutlinedButton(
                onClick = {
                    val calendar = Calendar.getInstance()
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    calendar.set(year, month, day, hour, minute)
                                    selectedDate = calendar.time
                                },
                                calendar.get(Calendar.HOUR_OF_DAY),
                                calendar.get(Calendar.MINUTE),
                                false
                            ).show()
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = selectedDate?.let { dateFormat.format(it) } ?: "Select Date & Time (optional)"
                )
            }
            
            // Error Message
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
            }
            
            Spacer(Modifier.weight(1f))
            
            // Create Button
            Button(
                onClick = {
                    if (eventName.isBlank()) {
                        errorMessage = "Event name is required"
                        return@Button
                    }
                    
                    isCreating = true
                    errorMessage = null
                    
                    scope.launch {
                        try {
                            // Get Firebase ID token
                            val firebaseUser = FirebaseAuth.getInstance().currentUser
                            if (firebaseUser == null) {
                                errorMessage = "Not logged in with Firebase"
                                isCreating = false
                                return@launch
                            }
                            
                            val idToken = firebaseUser.getIdToken(false).await().token
                            if (idToken == null) {
                                errorMessage = "Failed to get authentication token"
                                isCreating = false
                                return@launch
                            }
                            
                            val response = repository.createEvent(
                                eventName = eventName.trim(),
                                description = description.trim(),
                                eventDate = selectedDate?.time,
                                coverImage = coverImage.trim(),
                                firebaseIdToken = idToken
                            )
                            
                            if (response.success) {
                                onEventCreated(response.eventId.orEmpty(), response.joinLink)
                            } else {
                                errorMessage = response.error ?: "Failed to create event"
                            }
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "An error occurred"
                        } finally {
                            isCreating = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCreating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0F52BA)
                )
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isCreating) "Creating..." else "Create Event")
            }
        }
    }
}
