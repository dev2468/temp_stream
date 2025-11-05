package com.example.streamchat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.fontResource
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

// <--- IMPORTANT: these imports reference the ViewModel and UI state in your repo --->
import com.example.streamchat.ui.channels.ChannelListUiState
import com.example.streamchat.ui.channels.ChannelListViewModel

import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.models.Channel
import io.getstream.chat.android.models.User
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ChannelListActivity : AppCompatActivity() {

    private val viewModel: ChannelListViewModel by viewModels {
        ViewModelFactory(ChatClient.instance(), ChatRepository.getInstance(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Make layout draw edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // ✅ Force white status & navigation bars (always white, even in dark mode)
        window.statusBarColor = Color.White.toArgb()
        window.navigationBarColor = Color.White.toArgb()

        // ✅ Make system bar icons dark for visibility (battery, clock, etc.)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true
        insetsController.isAppearanceLightNavigationBars = true

        setContent {
            // ✅ Keep background white to blend seamlessly into status bar
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.White
            ) {
                ChannelListScreen(
                    viewModel = viewModel,
                    onChannelClick = { channel ->
                        val cid = channel.cid
                        if (!cid.isNullOrBlank()) {
                            startActivity(MessageListActivity.createIntent(this, cid, channel.name ?: ""))
                        } else {
                            Log.e("ChannelListActivity", "Channel cid is null for channel: $channel")
                        }
                    },
                    onAddClick = {
                        startActivity(Intent(this, FriendListActivity::class.java))
                    },
                    onCreateEventClick = {
                        startActivity(Intent(this, CreateEventActivity::class.java))
                    }
                )
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelListScreen(
    viewModel: ChannelListViewModel,
    onChannelClick: (Channel) -> Unit,
    onAddClick: () -> Unit,
    onCreateEventClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Unread", "Groups", "DMs", "Events")
    var showFabMenu by remember { mutableStateOf(false) }

    // load channels if needed (ViewModel already does this in init, but safe to refresh)
    LaunchedEffect(Unit) {
        // no-op here, ViewModel already loads channels from init
    }

    // Font resource - make sure res/font/coda_caption.ttf exists
    val coda = FontFamily(Font(resId = R.font.coda_extrabold))

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding(),
        topBar = {
            Column {
                TopBarTitle(coda)
                FilterRow(filters = filters, selected = selectedFilter, onSelected = { selectedFilter = it })
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // Show menu items when expanded
                if (showFabMenu) {
                    // Create Event button
                    FloatingActionButton(
                        onClick = {
                            showFabMenu = false
                            onCreateEventClick()
                        },
                        containerColor = Color(0xFF0F52BA),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Event, contentDescription = "Create Event", tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Create Event", color = Color.White)
                        }
                    }
                    
                    // New Chat button
                    FloatingActionButton(
                        onClick = {
                            showFabMenu = false
                            onAddClick()
                        },
                        containerColor = Color(0xFF0F52BA),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "New chat", tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("New Chat", color = Color.White)
                        }
                    }
                }
                
                // Main FAB
                FloatingActionButton(
                    onClick = { showFabMenu = !showFabMenu },
                    containerColor = Color(0xFF0F52BA)
                ) {
                    Icon(
                        if (showFabMenu) Icons.Default.Add else Icons.Default.Add,
                        contentDescription = "Menu",
                        tint = Color.White
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
            when (uiState) {
                is ChannelListUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF0F52BA))
                    }
                }
                is ChannelListUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = (uiState as ChannelListUiState.Error).message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is ChannelListUiState.Empty -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No chats yet — start a new one!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is ChannelListUiState.Success -> {
                    val allChannels = (uiState as ChannelListUiState.Success).channels
                    val filtered = when (selectedFilter) {
                        "Unread" -> allChannels.filter { (it.extraData["unread_count"] as? Int ?: 0) > 0 }
                        "Groups" -> allChannels.filter { it.memberCount > 2 }
                        "DMs" -> allChannels.filter { it.memberCount <= 2 }
                        "Events" -> allChannels.filter { it.extraData["is_event_channel"] as? Boolean == true }
                        else -> allChannels
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filtered) { channel ->
                            ChannelRow(channel = channel, onClick = { onChannelClick(channel) }, coda)
                            Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopBarTitle(coda: FontFamily) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .statusBarsPadding() // ✅ pushes text below system bar height
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Chats",
            fontFamily = coda,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 34.sp,
            color = Color.Black, // matches light theme
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}

@Composable
fun FilterRow(filters: List<String>, selected: String, onSelected: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        filters.forEach { f ->
            val isSelected = f == selected
            val bg by animateColorAsState(if (isSelected) Color(0xFF0F52BA) else MaterialTheme.colorScheme.surface)
            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = bg,
                tonalElevation = if (isSelected) 4.dp else 0.dp,
                modifier = Modifier
                    .height(36.dp)
                    .wrapContentWidth()
                    .clickable { onSelected(f) }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(text = f, color = contentColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
fun ChannelRow(channel: Channel, onClick: () -> Unit, coda: FontFamily) {
    val currentUserId = ChatClient.instance().getCurrentUser()?.id
    val isGroup = channel.memberCount > 2

    val otherMember = channel.members.firstOrNull { it.user.id != currentUserId }?.user
    val displayName = when {
        isGroup -> channel.name.ifEmpty { "Unnamed Group" }
        !otherMember?.name.isNullOrBlank() -> otherMember!!.name
        !otherMember?.id.isNullOrBlank() -> otherMember!!.id
        else -> "Unknown"
    }
    val avatarUrl: String? = if (isGroup) channel.image else otherMember?.image

    // Last message text: try extraData["last_message"] (safe) else fallback
    val lastMessageText = channel.messages.lastOrNull()?.text ?: "No messages yet"

    // Last message time: use lastMessageAt if present
    val lastMessageTime = channel.lastMessageAt?.let { ts ->
        try {
            val instant = Instant.ofEpochMilli(ts.time)
            DateTimeFormatter.ofPattern("hh:mm", Locale.getDefault())
                .withZone(ZoneId.systemDefault())
                .format(instant)
        } catch (_: Exception) { "" }
    } ?: ""

    // unread badge if provided in extraData
    val unreadCount = (channel.extraData["unread_count"] as? Int) ?: 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "avatar",
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(displayName, fontFamily = coda, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                if (lastMessageTime.isNotEmpty()) {
                    Text(lastMessageTime, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = lastMessageText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                if (unreadCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF0F52BA),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = unreadCount.coerceAtMost(99).toString(), color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
