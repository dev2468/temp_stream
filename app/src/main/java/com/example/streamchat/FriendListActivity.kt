package com.example.streamchat

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.streamchat.data.repository.ChatRepository
import com.example.streamchat.ui.ViewModelFactory
import com.example.streamchat.ui.friends.AppUser
import com.example.streamchat.ui.friends.FriendListUiState
import com.example.streamchat.ui.friends.FriendListViewModel
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.api.models.QueryChannelsRequest
import io.getstream.chat.android.models.Filters
import kotlinx.coroutines.launch

class FriendListActivity : ComponentActivity() {

    private val viewModel: FriendListViewModel by viewModels {
        ViewModelFactory(ChatClient.instance(), ChatRepository.getInstance(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.White.toArgb()
        window.navigationBarColor = Color.White.toArgb()
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true

        setContent {
            FriendListScreen(
                viewModel = viewModel,
                onBack = { finish() },
                onFriendClicked = { handleFriendChat(it) },
                onCreateGroup = {
                    startActivity(Intent(this, CreateGroupActivity::class.java))
                }
            )
        }
    }

    private fun handleFriendChat(friend: AppUser) {
        val client = ChatClient.instance()
        val currentUser = client.getCurrentUser() ?: return
        val currentUserId = currentUser.id
        val friendId = friend.uid

        // deterministic channel id for 1:1 chats
        val channelId = listOf(currentUserId, friendId).sorted().joinToString("-")

        lifecycleScope.launch {
            try {
                // Build QueryChannelsRequest (fixes the FilterObject vs QueryChannelsRequest mismatch)
                val request = QueryChannelsRequest(
                    filter = Filters.and(
                        Filters.eq("type", "messaging"),
                        Filters.eq("id", channelId)
                    ),
                    offset = 0,
                    limit = 1
                )

                val existingResult = client.queryChannels(request).await()

                val channel = if (existingResult.isSuccess && existingResult.getOrThrow().isNotEmpty()) {
                    existingResult.getOrThrow().first()
                } else {
                    val createResult = client.createChannel(
                        channelType = "messaging",
                        channelId = channelId,
                        memberIds = listOf(currentUserId, friendId),
                        extraData = mapOf("name" to friend.username)
                    ).await()

                    if (!createResult.isSuccess) {
                        Toast.makeText(this@FriendListActivity, "Error creating chat: ${createResult.errorOrNull()?.message ?: "unknown"}", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    createResult.getOrThrow()
                }

                val cid = channel.cid
                val intent = MessageListActivity.createIntent(
                    this@FriendListActivity,
                    cid,
                    friend.username
                )
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@FriendListActivity, "Failed to open chat: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendListScreen(
    viewModel: FriendListViewModel,
    onBack: () -> Unit,
    onFriendClicked: (AppUser) -> Unit,
    onCreateGroup: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val coda = FontFamily(Font(R.font.coda_extrabold))
    val roboto = FontFamily(Font(R.font.roboto_flex_light))
    var usernameInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadFriendsAndRequests()
    }

    val textFieldColors = TextFieldDefaults.outlinedTextFieldColors(
        focusedBorderColor = Color(0xFF0F52BA),
        unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
        cursorColor = Color(0xFF0F52BA),
        focusedLabelColor = Color(0xFF0F52BA),
        unfocusedLabelColor = Color.Gray,
        containerColor = Color.White
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Friends",
                    fontFamily = coda,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 30.sp,
                    color = Color.Black
                )
            }
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // 🔹 Add Friend Section
            OutlinedTextField(
                value = usernameInput,
                onValueChange = { usernameInput = it.lowercase().trim() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                label = { Text("Add friend by username", fontFamily = roboto) },
                leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                singleLine = true,
                colors = textFieldColors
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (usernameInput.isNotBlank()) {
                        viewModel.sendFriendRequest(usernameInput) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) usernameInput = ""
                        }
                    } else {
                        Toast.makeText(context, "Enter a username", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F52BA))
            ) {
                Text("Send Request", color = Color.White, fontFamily = roboto)
            }

            Spacer(Modifier.height(16.dp))

            // 🔹 Create Group Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEFF3FF))
                    .clickable { onCreateGroup() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.GroupAdd,
                    contentDescription = "Create Group",
                    tint = Color(0xFF0F52BA),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Create New Group",
                    color = Color(0xFF0F52BA),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            when (val state = uiState) {
                is FriendListUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF0F52BA))
                }

                is FriendListUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message, color = Color.Red)
                }

                is FriendListUiState.Success -> {
                    if (state.requests.isNotEmpty()) {
                        Text(
                            text = "Friend Requests",
                            fontFamily = coda,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF0F52BA)
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(state.requests) { request ->
                                FriendRequestRow(
                                    user = request,
                                    onAccept = {
                                        viewModel.acceptFriendRequest(request.uid, request.username) { ok ->
                                            Toast.makeText(
                                                context,
                                                if (ok) "Friend added!" else "Failed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    roboto = roboto
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }

                    if (state.friends.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No friends yet", color = Color.Gray)
                        }
                    } else {
                        Text(
                            text = "Your Friends",
                            fontFamily = coda,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF0F52BA)
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(state.friends) { friend ->
                                FriendRow(
                                    user = friend,
                                    roboto = roboto,
                                    onClick = { onFriendClicked(friend) },
                                    onRemove = {
                                        viewModel.removeFriend(friend.uid) { ok ->
                                            Toast.makeText(
                                                context,
                                                if (ok) "Removed ${friend.username}" else "Error removing",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun FriendRequestRow(user: AppUser, onAccept: () -> Unit, roboto: FontFamily) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = Color(0xFFEFF3FF),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = null,
                contentDescription = "User Avatar",
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD0D0D0))
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "@${user.username}",
                fontFamily = roboto,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onAccept,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F52BA))
            ) {
                Text("Accept", color = Color.White, fontFamily = roboto)
            }
        }
    }
}
@Composable
fun FriendRow(user: AppUser, roboto: FontFamily, onClick: () -> Unit, onRemove: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = null,
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD0D0D0))
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "@${user.username}",
                fontFamily = roboto,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red)
            }
        }
    }
}
}
