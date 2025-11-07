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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.example.streamchat.data.repository.ChatRepository
import com.example.streamchat.ui.ViewModelFactory
import com.example.streamchat.ui.messages.MessageListUiState
import com.example.streamchat.ui.messages.MessageListViewModel
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.compose.ui.theme.ChatTheme
import io.getstream.chat.android.models.Message
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MessageListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                val canSendMessages by viewModel.canSendMessagesLiveData.observeAsState(true)

                MessageListScreen(
                    channelId = channelId,
                    channelName = channelNameFromIntent,
                    uiState = uiState,
                    messageText = messageText,
                    selectedImages = selectedImages,
                    canSendMessages = canSendMessages,
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
    channelId: String,
    channelName: String,
    uiState: MessageListUiState,
    messageText: String,
    selectedImages: List<android.net.Uri>,
    canSendMessages: Boolean,
    onMessageTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onAddImages: (List<android.net.Uri>) -> Unit,
    onRemoveImage: (android.net.Uri) -> Unit,
    onReactionClick: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    var showGroupDetails by remember { mutableStateOf(false) }
    val coda = FontFamily(Font(resId = R.font.coda_extrabold))

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding().imePadding(),
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
                actions = {
                    IconButton(onClick = { showGroupDetails = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Group Info", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            MessageListContent(
                uiState = uiState,
                messageText = messageText,
                selectedImages = selectedImages,
                canSendMessages = canSendMessages,
                onMessageTextChange = onMessageTextChange,
                onSendMessage = onSendMessage,
                onAddImages = onAddImages,
                onRemoveImage = onRemoveImage,
                onReactionClick = onReactionClick,
                onDeleteMessage = onDeleteMessage
            )
        }
    }

    if (showGroupDetails) {
        GroupDetailsDialog(channelId = channelId, onDismiss = { showGroupDetails = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageListContent(
    uiState: MessageListUiState,
    messageText: String,
    selectedImages: List<android.net.Uri>,
    canSendMessages: Boolean,
    onMessageTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onAddImages: (List<android.net.Uri>) -> Unit,
    onRemoveImage: (android.net.Uri) -> Unit,
    onReactionClick: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit
) {
    val listState = rememberLazyListState()
    
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        onAddImages(uris)
    }

    Column(Modifier.fillMaxSize().background(Color.White).imePadding()) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth()
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
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxSize(),
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

        if (!canSendMessages) {
            Surface(color = Color(0xFFFFF3CD), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF856404))
                    Spacer(Modifier.width(8.dp))
                    Text("Only the event organizer can post messages", color = Color(0xFF856404), fontSize = 14.sp)
                }
            }
        } else {
            MessageComposer(
                messageText = messageText,
                selectedImages = selectedImages,
                onMessageTextChange = onMessageTextChange,
                onSendMessage = onSendMessage,
                onAddImages = { imagePicker.launch("image/*") },
                onRemoveImage = onRemoveImage
            )
        }
    }
}

@Composable
fun MessageComposer(
    messageText: String,
    selectedImages: List<android.net.Uri>,
    onMessageTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onAddImages: () -> Unit,
    onRemoveImage: (android.net.Uri) -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        color = Color(0xFFF8F8F8),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            if (selectedImages.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedImages) { uri ->
                        Box(Modifier.size(70.dp)) {
                            AsyncImage(model = uri, contentDescription = null, modifier = Modifier.clip(RoundedCornerShape(10.dp)).fillMaxSize())
                            IconButton(
                                onClick = { onRemoveImage(uri) },
                                modifier = Modifier.align(Alignment.TopEnd).size(20.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAddImages) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Attach", tint = Color(0xFF0F52BA))
                }

                OutlinedTextField(
                    value = messageText,
                    onValueChange = onMessageTextChange,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    placeholder = { Text("Type a message...", color = Color.Gray) },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 5
                )

                IconButton(
                    onClick = onSendMessage,
                    enabled = messageText.isNotBlank() || selectedImages.isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (messageText.isNotBlank() || selectedImages.isNotEmpty()) Color(0xFF0F52BA) else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun GroupDetailsDialog(channelId: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = {}, text = {
        GroupDetailsContent(channelId = channelId, onClose = onDismiss)
    })
}

@Composable
fun GroupDetailsContent(channelId: String, onClose: () -> Unit) {
    val client = ChatClient.instance()
    val scope = rememberCoroutineScope()
    var createdAt by remember { mutableStateOf<Date?>(null) }
    var members by remember { mutableStateOf<List<String>>(emptyList()) }
    var groupName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(channelId) {
        scope.launch {
            val result = client.channel(channelId).watch().await()
            if (result.isSuccess) {
                val channel = result.getOrThrow()
                createdAt = channel.createdAt
                members = channel.members.map { it.user.name.ifBlank { it.user.id } }
                groupName = channel.name
                loading = false
            } else {
                error = result.errorOrNull()?.message ?: "Failed to load group details"
                loading = false
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Group details", fontWeight = FontWeight.Bold)
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }

        if (loading) CircularProgressIndicator()
        else if (error != null) Text(error ?: "", color = Color.Red)
        else {
            Text("Name: $groupName", fontWeight = FontWeight.Bold)
            createdAt?.let {
                Text("Created: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(it))
            }
            Spacer(Modifier.height(8.dp))
            Text("Members:")
            members.forEach { Text("• $it") }
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

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            modifier = Modifier.defaultMinSize(minWidth = 60.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = alignment) {
                if (message.text.isNotEmpty()) {
                    Text(message.text, color = textColor, fontSize = 15.sp)
                }
                message.attachments.forEach { attachment ->
                    if (attachment.type == "image") {
                        Spacer(Modifier.height(8.dp))
                        AsyncImage(
                            model = attachment.imageUrl ?: attachment.upload,
                            contentDescription = null,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).fillMaxWidth(0.7f)
                        )
                    }
                }
                Text(
                    text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(message.createdAt ?: Date()),
                    fontSize = 11.sp,
                    color = if (isOwnMessage) Color.White.copy(alpha = 0.8f) else Color.DarkGray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
