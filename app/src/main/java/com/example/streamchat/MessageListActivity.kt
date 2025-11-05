package com.example.streamchat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.livedata.observeAsState
import coil.compose.AsyncImage
import com.example.streamchat.data.repository.ChatRepository
import com.example.streamchat.ui.ViewModelFactory
import com.example.streamchat.ui.messages.MessageListUiState
import com.example.streamchat.ui.messages.MessageListViewModel
import com.google.android.material.appbar.MaterialToolbar
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.compose.ui.theme.ChatTheme
import io.getstream.chat.android.models.Message
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

class MessageListActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val channelId = intent.getStringExtra(KEY_CHANNEL_ID) ?: return finish()
        var channelNameFromIntent = intent.getStringExtra(KEY_CHANNEL_NAME) ?: ""
        
        val viewModel: MessageListViewModel by viewModels {
            ViewModelFactory(
                ChatClient.instance(),
                ChatRepository.getInstance(applicationContext),
                channelId,
                applicationContext
            )
        }
        
        setContentView(R.layout.activity_message_list)
        
        // Setup toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.apply {
            setNavigationOnClickListener { finish() }
            title = channelNameFromIntent.ifBlank { "Chat" }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_group_details -> {
                        GroupDetailsBottomSheet.show(
                            this@MessageListActivity,
                            channelId,
                            onNameUpdated = { newName ->
                                title = if (newName.isBlank()) channelNameFromIntent.ifBlank { "Chat" } else newName
                                channelNameFromIntent = newName
                            }
                        )
                        true
                    }
                    else -> false
                }
            }
        }
        
        findViewById<ComposeView>(R.id.composeView).setContent {
            ChatTheme {
                val uiState by viewModel.uiStateLiveData.observeAsState(MessageListUiState.Loading)
                val messageText by viewModel.messageTextLiveData.observeAsState("")
                val selectedImages by viewModel.selectedImagesLiveData.observeAsState(emptyList())
                val canSendMessages by viewModel.canSendMessagesLiveData.observeAsState(true)
                
                MessageListContent(
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
                    onDeleteMessage = { viewModel.deleteMessage(it) }
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

object GroupDetailsBottomSheet {
    fun show(activity: ComponentActivity, channelId: String, onNameUpdated: (String) -> Unit = {}) {
        // Use a Compose bottom sheet dialog for simplicity
        val dialog = androidx.appcompat.app.AlertDialog.Builder(activity).create()
        val composeView = ComposeView(activity).apply {
            setContent {
                ChatTheme {
                    GroupDetailsContent(channelId = channelId, onClose = { dialog.dismiss() }, onNameUpdated = onNameUpdated)
                }
            }
        }
        dialog.setView(composeView)
        dialog.show()
    }
}

@Composable
fun GroupDetailsContent(channelId: String, onClose: () -> Unit, onNameUpdated: (String) -> Unit) {
    val client = ChatClient.instance()
    val scope = rememberCoroutineScope()
    var createdAt by remember { mutableStateOf<Date?>(null) }
    var members by remember { mutableStateOf<List<String>>(emptyList()) }
    var groupName by remember { mutableStateOf("") }
    var originalName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(channelId) {
        try {
            val result = client.channel(channelId).watch().await()
            if (result.isSuccess) {
                val channel = result.getOrThrow()
                createdAt = channel.createdAt
                members = channel.members.map { it.user.name.ifBlank { it.user.id } }
                groupName = channel.name
                originalName = channel.name
                loading = false
            } else {
                error = result.errorOrNull()?.message ?: "Failed to load channel"
                loading = false
            }
        } catch (e: Exception) {
            error = e.message
            loading = false
        }
    }

    Surface(shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Group details", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
            }

            if (loading) {
                CircularProgressIndicator()
            } else if (error != null) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
            } else {
                if (groupName.isNotBlank()) {
                    Text("Name: $groupName", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                }
                createdAt?.let { ca ->
                    Text("Created: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(ca))
                }
                Spacer(Modifier.height(12.dp))
                Text("Members:")
                Spacer(Modifier.height(4.dp))
                members.forEach { name ->
                    Text("• $name")
                }
                Spacer(Modifier.height(16.dp))
                Text("Rename group", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Group name") }
                )
                saveError?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(err, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            // Update channel name via ChannelClient
                            saving = true
                            saveError = null
                            scope.launch {
                                try {
                                    val trimmed = groupName.trim()
                                    val channelExtraData: Map<String, Any> = if (trimmed.isNotEmpty()) {
                                        mapOf("name" to trimmed)
                                    } else {
                                        emptyMap()
                                    }
                                    val (type, id) = channelId.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                                    val res = client.updateChannel(type, id, null, channelExtraData).await()
                                    saving = false
                                    if (res.isSuccess) {
                                        onNameUpdated(groupName.trim())
                                        originalName = groupName
                                    } else {
                                        saveError = res.errorOrNull()?.message ?: "Failed to rename group"
                                    }
                                } catch (e: Exception) {
                                    saving = false
                                    saveError = e.message ?: "Failed to rename group"
                                }
                            }
                        },
                        enabled = !saving && groupName.trim() != originalName.trim()
                    ) { if (saving) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Save") }
                    OutlinedButton(onClick = { groupName = originalName }, enabled = !saving) { Text("Reset") }
                }
            }
        }
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
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> onAddImages(uris) }
    
    Column(Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        // Message list content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (uiState) {
                is MessageListUiState.Loading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                is MessageListUiState.Success -> {
                    if (uiState.messages.isEmpty()) {
                        Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(64.dp), Color.Gray)
                            Spacer(Modifier.height(16.dp))
                            Text("No messages yet", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.messages) { message ->
                                MessageItem(
                                    message = message,
                                    isOwnMessage = message.user.id == ChatClient.instance().getCurrentUser()?.id,
                                    onReactionClick = { onReactionClick(message.id, it) },
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
                is MessageListUiState.Error -> {
                    Text(
                        uiState.message,
                        Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        // Message Composer at bottom
        Column {
            // Show read-only banner for event channels where user is not admin
            if (!canSendMessages) {
                Surface(
                    color = Color(0xFFFFF3CD),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF856404),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Only the event organizer can post messages",
                            color = Color(0xFF856404),
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            // Show message composer only if user can send messages
            if (canSendMessages) {
                if (selectedImages.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedImages.forEach { uri ->
                            Box(Modifier.size(60.dp)) {
                                AsyncImage(model = uri, contentDescription = null)
                                IconButton(
                                    onClick = { onRemoveImage(uri) },
                                    modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
                                ) {
                                    Icon(Icons.Default.Close, "Remove", tint = Color.Red)
                                }
                            }
                        }
                    }
                }
                
                Surface(color = Color.White, shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        IconButton(onClick = { imagePicker.launch("image/*") }) {
                            Icon(Icons.Default.Add, "Attach", tint = Color(0xFF005FFF))
                        }
                        
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = onMessageTextChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Type a message...") },
                            maxLines = 5
                        )
                        
                        IconButton(
                            onClick = onSendMessage,
                            enabled = messageText.isNotBlank() || selectedImages.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.Send,
                                "Send",
                                tint = if (messageText.isNotBlank() || selectedImages.isNotEmpty())
                                    Color(0xFF005FFF) else Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItem(
    message: Message,
    isOwnMessage: Boolean,
    onReactionClick: (String) -> Unit,
    onDeleteClick: () -> Unit
) {
    var showOptions by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start
        ) {
            if (!isOwnMessage) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF005FFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        message.user.name.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            
            Column(horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start) {
                Card(
                    modifier = Modifier.clickable { showOptions = !showOptions },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOwnMessage) Color(0xFF005FFF) else Color.White
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        if (!isOwnMessage) {
                            Text(
                                message.user.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF005FFF)
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        
                        if (message.text.isNotEmpty()) {
                            Text(
                                message.text,
                                color = if (isOwnMessage) Color.White else Color.Black,
                                fontSize = 15.sp
                            )
                        }
                        
                        message.attachments.forEach { attachment ->
                            if (attachment.type == "image") {
                                Spacer(Modifier.height(8.dp))
                                AsyncImage(
                                    model = attachment.imageUrl ?: attachment.upload,
                                    contentDescription = null,
                                    modifier = Modifier.size(200.dp).clip(RoundedCornerShape(8.dp))
                                )
                            }
                        }
                    }
                }
                
                if (message.reactionCounts.isNotEmpty()) {
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        message.reactionCounts.forEach { (type, count) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                shadowElevation = 2.dp
                            ) {
                                Text(
                                    "$type $count",
                                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                
                Text(
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(message.createdAt ?: Date()),
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        
        if (showOptions) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("👍", "❤️", "😂", "😮", "😢", "🙏").forEach { emoji ->
                    Surface(
                        onClick = { onReactionClick(emoji) },
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 2.dp
                    ) {
                        Text(emoji, Modifier.padding(8.dp), fontSize = 16.sp)
                    }
                }
                
                if (isOwnMessage) {
                    IconButton(onClick = onDeleteClick) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                }
            }
        }
    }
}
