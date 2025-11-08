package com.example.streamchat

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.streamchat.data.repository.ChatRepository
import com.example.streamchat.data.repository.EventImageManager
import com.example.streamchat.ui.finishWithSlide
import com.example.streamchat.ui.startActivityWithSlide
import com.google.firebase.auth.FirebaseAuth
import io.getstream.chat.android.compose.ui.theme.ChatTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.RenderEffect as AndroidRenderEffect

private val accentColor = Color(0xFF0F52BA)
private val textColor = Color.White
private val hintColor = Color(0xFFDADADA)

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
    var selectedDate by remember { mutableStateOf<Date?>(null) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Blurred gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0F52BA), Color(0xFF3B74D3), Color(0xFFFFFFFF))
                    )
                )
                .graphicsLayer {
                    renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        AndroidRenderEffect.createBlurEffect(45f, 45f, Shader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                    } else null
                }
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("New event", color = textColor, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textColor)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                Spacer(Modifier.height(12.dp))

                // Cover image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable(enabled = imageUri == null) { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri != null) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "Event Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FrostedMiniButton("Edit", Icons.Default.Edit) {
                                imagePickerLauncher.launch("image/*")
                            }
                            FrostedMiniButton("Remove", Icons.Default.Delete) {
                                imageUri = null
                            }
                        }
                    } else {
                        Text("Add cover image", color = textColor, fontWeight = FontWeight.Bold)
                    }
                }

                // Event title
                FrostedBox {
                    StyledTextField(
                        value = eventName,
                        onValueChange = { eventName = it },
                        placeholder = "Untitled event",
                        textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textColor)
                    )
                }

                // Date & Time
                FrostedBox {
                    DateTimePickerSection(selectedDate = selectedDate, onDateSelected = { selectedDate = it })
                }

                // Description
                Text("Description", color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                FrostedBox(Modifier.height(120.dp)) {
                    StyledTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = "Add details about your event..."
                    )
                }

                Spacer(Modifier.height(32.dp))

                // ✅ Create Button Logic
                CreateButton(isCreating = isCreating) {
                    if (eventName.isBlank()) {
                        Toast.makeText(context, "Event name is required", Toast.LENGTH_SHORT).show()
                        return@CreateButton
                    }
                    if (selectedDate == null) {
                        Toast.makeText(context, "Select date and time", Toast.LENGTH_SHORT).show()
                        return@CreateButton
                    }

                    isCreating = true
                    scope.launch {
                        try {
                            val user = FirebaseAuth.getInstance().currentUser
                            if (user == null) {
                                Toast.makeText(context, "Not logged in", Toast.LENGTH_SHORT).show()
                                isCreating = false
                                return@launch
                            }

                            val idToken = user.getIdToken(false).await().token
                            if (idToken == null) {
                                Toast.makeText(context, "Auth failed", Toast.LENGTH_SHORT).show()
                                isCreating = false
                                return@launch
                            }

                            // Step 1️⃣ — Create event first to get eventId
                            val response = repository.createEvent(
                                eventName = eventName.trim(),
                                description = description.trim(),
                                eventDate = selectedDate?.time,
                                coverImage = "", // temporarily empty; we'll upload manually next
                                firebaseIdToken = idToken
                            )

                            if (!response.eventId.isNullOrBlank()) {
                                val eventId = response.eventId
                                var uploadedUrl: String? = null

                                // Upload image using eventId as filename
                                imageUri?.let {
                                    uploadedUrl = EventImageManager.uploadEventCoverImage(it, eventId)
                                }

                                // Now create the Stream channel with the uploaded URL
                                val chatClient = io.getstream.chat.android.client.ChatClient.instance()
                                val currentUser = FirebaseAuth.getInstance().currentUser
                                val imageUrl = uploadedUrl.orEmpty()

                                if (currentUser != null) {
                                    val extraData: MutableMap<String, Any> = mutableMapOf(
                                        "is_event_channel" to true,
                                        "event_id" to eventId,
                                        "description" to description
                                    )
                                    selectedDate?.time?.let { extraData["event_date"] = it }

                                    chatClient.createChannel(
                                        channelType = "messaging",
                                        channelId = eventId,
                                        memberIds = listOf(currentUser.uid),
                                        extraData = extraData
                                    ).enqueue { result ->
                                        if (result.isSuccess) {
                                            Log.d("CreateEvent", "✅ Stream channel created for $eventId with image $imageUrl")
                                        } else {
                                            Log.e("CreateEvent", "⚠️ Failed to create Stream channel: ${result.errorOrNull()?.message}")
                                        }
                                    }
                                }

                                Toast.makeText(context, "Event created successfully!", Toast.LENGTH_SHORT).show()
                                onEventCreated(eventId, response.joinLink)
                            } else {
                                Toast.makeText(context, "Failed to create event.", Toast.LENGTH_SHORT).show()
                            }


                        } catch (e: Exception) {
                            Toast.makeText(context, e.message ?: "An error occurred", Toast.LENGTH_SHORT).show()
                        } finally {
                            isCreating = false
                        }
                    }
                }

                Spacer(Modifier.height(50.dp))
            }
        }
    }
}

@Composable
fun FrostedBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun FrostedMiniButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, tint = Color.White)
            Spacer(Modifier.width(4.dp))
            Text(label, color = Color.White, fontSize = 14.sp)
        }
    }
}

@Composable
fun DateTimePickerSection(selectedDate: Date?, onDateSelected: (Date) -> Unit) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())

    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date", tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Event Date & Time", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    selectedDate?.let { dateFormat.format(it) } ?: "Select date and time",
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.clickable {
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                TimePickerDialog(
                                    context,
                                    { _, h, min ->
                                        calendar.set(y, m, d, h, min)
                                        onDateSelected(calendar.time)
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
                    }
                )
            }
        }
    }
}

@Composable
fun StyledTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, textStyle: TextStyle = TextStyle(fontSize = 16.sp, color = textColor)) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        cursorBrush = SolidColor(textColor),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, color = hintColor)
            inner()
        }
    )
}

@Composable
fun CreateButton(isCreating: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(targetValue = if (isCreating) 0.96f else 1f, animationSpec = tween(150))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .animateContentSize(),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
        ) {
            if (isCreating) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = accentColor, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Creating...", color = accentColor, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.Check, null, tint = accentColor)
                Spacer(Modifier.width(6.dp))
                Text("Create Event", color = accentColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}
