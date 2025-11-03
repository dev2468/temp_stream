package com.example.streamchat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.livedata.observeAsState
import com.example.streamchat.data.repository.ChatRepository
import com.example.streamchat.ui.ViewModelFactory
import com.example.streamchat.ui.channels.ChannelListUiState
import com.example.streamchat.ui.channels.ChannelListViewModel
import com.google.android.material.appbar.MaterialToolbar
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.compose.ui.theme.ChatTheme
import io.getstream.chat.android.models.Channel
import io.getstream.chat.android.models.User
import java.text.SimpleDateFormat
import java.util.*

class ChannelListActivity : AppCompatActivity() {

    private val viewModel: ChannelListViewModel by viewModels {
        ViewModelFactory(ChatClient.instance(), ChatRepository.getInstance(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channel_list)

        // Setup toolbar
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setSupportActionBar(this)
        }

        // Setup search bar
        findViewById<androidx.appcompat.widget.SearchView>(R.id.searchBar).apply {
            setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false
                override fun onQueryTextChange(newText: String?): Boolean {
                    viewModel.onSearchQueryChanged(newText ?: "")
                    return true
                }
            })
        }

        findViewById<ComposeView>(R.id.composeView).setContent {
            ChatTheme {
                val uiState by viewModel.uiStateLiveData.observeAsState(ChannelListUiState.Loading)
                val availableUsers by viewModel.availableUsersLiveData.observeAsState(emptyList())
                var showCreateChannelDialog by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxSize()) {
                    ChannelListContent(
                        uiState = uiState,
                        onChannelClick = { channel ->
                            startActivity(
                                MessageListActivity.createIntent(
                                    this@ChannelListActivity,
                                    channel.cid,
                                    channel.name
                                )
                            )
                        }
                    )

                    // Floating Action Button
                    FloatingActionButton(
                        onClick = { showCreateChannelDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = Color(0xFF005FFF)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Create Channel",
                            tint = Color.White
                        )
                    }

                    if (showCreateChannelDialog) {
                        CreateChannelDialog(
                            onDismiss = { showCreateChannelDialog = false },
                            availableUsers = availableUsers,
                            onCreateChannel = { memberIds, groupName ->
                                viewModel.createChannel(memberIds, groupName)
                                showCreateChannelDialog = false
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_channel_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                ChatClient.instance().disconnect(flushPersistence = true).enqueue()
                ChatRepository.getInstance(applicationContext).clearSession()
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, ChannelListActivity::class.java)
        }
    }
}

@Composable
fun ChannelListContent(
    uiState: ChannelListUiState,
    onChannelClick: (Channel) -> Unit
) {
    when (uiState) {
        is ChannelListUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ChannelListUiState.Empty -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Chat, null, Modifier.size(64.dp), tint = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    Text("No channels yet", color = Color.Gray)
                }
            }
        }
        is ChannelListUiState.Success -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.channels) { channel ->
                    ChannelItem(
                        channel = channel,
                        currentUserId = ChatClient.instance().getCurrentUser()?.id ?: "",
                        onClick = { onChannelClick(channel) }
                    )
                }
            }
        }
        is ChannelListUiState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun ChannelItem(channel: Channel, currentUserId: String, onClick: () -> Unit) {
    val lastMessage = channel.messages.lastOrNull()
    val unreadCount = channel.unreadCount ?: 0
    val channelName = channel.name.ifEmpty {
        channel.members.filter { it.user.id != currentUserId }
            .joinToString(", ") { it.user.name }.ifEmpty { "Unnamed Channel" }
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (unreadCount > 0) Color(0xFFF0F8FF) else Color.White
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF005FFF)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    channelName.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        channelName,
                        fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    lastMessage?.createdAt?.let {
                        Text(
                            formatTime(it),
                            fontSize = 12.sp,
                            color = if (unreadCount > 0) Color(0xFF005FFF) else Color.Gray
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        lastMessage?.text ?: "No messages yet",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (unreadCount > 0) {
                        Badge(containerColor = Color(0xFF005FFF)) {
                            Text(
                                if (unreadCount > 99) "99+" else unreadCount.toString(),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatTime(date: Date): String {
    val now = Calendar.getInstance()
    val messageTime = Calendar.getInstance().apply { time = date }
    return when {
        now.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        now.get(Calendar.WEEK_OF_YEAR) == messageTime.get(Calendar.WEEK_OF_YEAR) ->
            SimpleDateFormat("EEE", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)
    }
}

@Composable
fun CreateChannelDialog(
    onDismiss: () -> Unit,
    availableUsers: List<User>,
    onCreateChannel: (List<String>, String?) -> Unit
) {
    var selectedUsers by remember { mutableStateOf(setOf<String>()) }
    var groupName by remember { mutableStateOf("") }
    val entries = availableUsers.map { it.id to (it.name.takeIf { n -> n.isNotBlank() } ?: it.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Channel") },
        text = {
            Column {
                Text(
                    "Select users to chat with:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Group name (optional)") }
                )
                Spacer(Modifier.height(8.dp))

                if (entries.isEmpty()) {
                    Text(
                        "No other users exist yet in this app. Ask a teammate to log in once or create users in the Stream Dashboard.",
                        color = Color.Gray
                    )
                }
                entries.forEach { (userId, userName) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedUsers = if (userId in selectedUsers) {
                                    selectedUsers - userId
                                } else {
                                    selectedUsers + userId
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = userId in selectedUsers,
                            onCheckedChange = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(userName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreateChannel(selectedUsers.toList(), groupName.ifBlank { null }) },
                enabled = selectedUsers.isNotEmpty()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}