package com.example.streamchat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.livedata.observeAsState
import coil.compose.AsyncImage
import com.example.streamchat.data.repository.ChatRepository
import com.example.streamchat.ui.ViewModelFactory
import com.example.streamchat.ui.messages.MessageListUiState
import com.example.streamchat.ui.messages.MessageListViewModel
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.models.Message
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.TopAppBar
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import io.getstream.chat.android.compose.ui.theme.ChatTheme
import kotlinx.coroutines.launch

class MessageListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge + light bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.WHITE
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true

        val channelId = intent.getStringExtra(KEY_CHANNEL_ID) ?: return finish()
        val channelNameFromIntent = intent.getStringExtra(KEY_CHANNEL_NAME) ?: "Chat"

        val viewModel: MessageListViewModel by viewModels {
            ViewModelFactory(
                ChatClient.instance(),
                ChatRepository.getInstance(applicationContext),
                channelId,
                applicationContext
            )
        }

        setContent {
            ChatTheme {
                val uiState by viewModel.uiStateLiveData.observeAsState(MessageListUiState.Loading)
                val messageText by viewModel.messageTextLiveData.observeAsState("")
                val selectedImages by viewModel.selectedImagesLiveData.observeAsState(emptyList())

                MessageListScreen(
                    channelName = channelNameFromIntent,
                    uiState = uiState,
                    messageText = messageText,
                    selectedImages = selectedImages,
                    onMessageTextChange = { viewModel.onMessageTextChanged(it) },
                    onSendMessage = { viewModel.sendMessage() },
                    onAddImages = { viewModel.addImages(it) },
                    onRemoveImage = { viewModel.removeImage(it) },
                    onReactionClick = { messageId, reaction ->
                        viewModel.addReaction(messageId, reaction)
                    },
                    onDeleteMessage = { viewModel.deleteMessage(it) },
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        private const val KEY_CHANNEL_ID = "channel_id"
        private const val KEY_CHANNEL_NAME = "channel_name"

        fun createIntent(context: Context, channelId: String, channelName: String = ""): Intent {
            return Intent(context, MessageListActivity::class.java).apply {
                putExtra(KEY_CHANNEL_ID, channelId)
                putExtra(KEY_CHANNEL_NAME, channelName)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageListScreen(
    channelName: String,
    uiState: MessageListUiState,
    messageText: String,
    selectedImages: List<android.net.Uri>,
    onMessageTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onAddImages: (List<android.net.Uri>) -> Unit,
    onRemoveImage: (android.net.Uri) -> Unit,
    onReactionClick: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    val coda = FontFamily(Font(resId = R.font.coda_extrabold))

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding(),
        topBar = {
            SmallTopAppBar(
                title = {
                    Text(
                        text = channelName,
                        fontFamily = coda,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color(0xFFFFFFFF))
            )
        },
        content = { padding ->
            MessageListContent(
                uiState = uiState,
                messageText = messageText,
                selectedImages = selectedImages,
                onMessageTextChange = onMessageTextChange,
                onSendMessage = onSendMessage,
                onAddImages = onAddImages,
                onRemoveImage = onRemoveImage,
                onReactionClick = onReactionClick,
                onDeleteMessage = onDeleteMessage,
                modifier = Modifier.padding(padding)
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageListContent(
    uiState: MessageListUiState,
    messageText: String,
    selectedImages: List<android.net.Uri>,
    onMessageTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onAddImages: (List<android.net.Uri>) -> Unit,
    onRemoveImage: (android.net.Uri) -> Unit,
    onReactionClick: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> onAddImages(uris) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (uiState) {
                is MessageListUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is MessageListUiState.Success -> {
                    if (uiState.messages.isEmpty()) {
                        Box(Modifier.align(Alignment.Center)) {
                            Text("Start a conversation!", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            reverseLayout = false,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .navigationBarsPadding(),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            items(uiState.messages) { message ->
                                MessageItemModern(
                                    message = message,
                                    isOwnMessage = message.user.id == ChatClient.instance().getCurrentUser()?.id,
                                    onReactionClick = { emoji ->
                                        onReactionClick(message.id, emoji)
                                    },
                                    onDeleteClick = { onDeleteMessage(message.id) }
                                )
                            }
                        }

                        LaunchedEffect(uiState.messages.size) {
                            if (uiState.messages.isNotEmpty()) {
                                listState.animateScrollToItem(uiState.messages.size - 1)
                            }
                        }
                    }
                }

                is MessageListUiState.Error -> Text(
                    text = uiState.message,
                    color = Color.Red,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Surface(
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
            color = Color(0xFFF8F8F8),
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            modifier = Modifier.fillMaxWidth().imePadding()
        ) {
            Column(Modifier.fillMaxWidth()) {
                if (selectedImages.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selectedImages) { uri ->
                            Box(Modifier.size(70.dp)) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .fillMaxSize()
                                )
                                IconButton(
                                    onClick = { onRemoveImage(uri) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(20.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { imagePicker.launch("image/*") }) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Attach", tint = Color(0xFF0F52BA))
                    }

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = onMessageTextChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        placeholder = { Text("Type a message...", color = Color.Gray) },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0F52BA),
                            unfocusedBorderColor = Color.LightGray,
                            cursorColor = Color(0xFF0F52BA)
                        )
                    )

                    IconButton(
                        onClick = onSendMessage,
                        enabled = messageText.isNotBlank() || selectedImages.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (messageText.isNotBlank() || selectedImages.isNotEmpty())
                                Color(0xFF0F52BA) else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItemModern(
    message: Message,
    isOwnMessage: Boolean,
    onReactionClick: (String) -> Unit,
    onDeleteClick: () -> Unit
) {
    val alignment = if (isOwnMessage) Alignment.End else Alignment.Start
    val bubbleColor = if (isOwnMessage) Color(0xFF0F52BA) else Color(0xFFEFEFEF)
    val textColor = if (isOwnMessage) Color.White else Color.Black
    val timeColor = if (isOwnMessage) Color.White.copy(alpha = 0.9f) else Color.DarkGray
    val scope = rememberCoroutineScope()

    // 🔹 Holder for profile image
    var senderImage by remember { mutableStateOf<String?>(message.user.image) }

    // 🔹 Fetch profile image from Firestore if not own message
    LaunchedEffect(message.user.id) {
        if (!isOwnMessage && senderImage.isNullOrEmpty()) {
            scope.launch {
                val img = com.example.streamchat.data.repository.UserProfileManager.getUserProfileImage(message.user.id)
                if (!img.isNullOrEmpty()) senderImage = img
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 🔹 Display avatar for messages NOT sent by current user
            if (!isOwnMessage) {
                AsyncImage(
                    model = senderImage ?: R.drawable.ic_person_placeholder,
                    contentDescription = "Sender profile",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDADADA))
                )
                Spacer(Modifier.width(8.dp))
            }

            // 🔹 Chat bubble
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = bubbleColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.defaultMinSize(minWidth = 60.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = alignment
                ) {
                    if (message.text.isNotEmpty()) {
                        Text(
                            message.text,
                            color = textColor,
                            fontSize = 15.sp
                        )
                    }

                    message.attachments.forEach { attachment ->
                        if (attachment.type == "image") {
                            Spacer(Modifier.height(8.dp))
                            AsyncImage(
                                model = attachment.imageUrl ?: attachment.upload,
                                contentDescription = null,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .fillMaxWidth(0.7f)
                            )
                        }
                    }

                    // Timestamp (bottom-left inside bubble)
                    Text(
                        text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(message.createdAt ?: Date()),
                        fontSize = 11.sp,
                        color = timeColor,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .align(Alignment.Start)
                    )
                }
            }
        }
    }
}
