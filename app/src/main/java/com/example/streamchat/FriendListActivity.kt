package com.example.streamchat

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.example.streamchat.data.repository.ChatRepository
import com.example.streamchat.ui.ViewModelFactory
import com.example.streamchat.ui.friends.FriendListUiState
import com.example.streamchat.ui.friends.FriendListViewModel
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.models.User

class FriendListActivity : ComponentActivity() {

    private val viewModel by viewModels<FriendListViewModel> {
        ViewModelFactory(ChatClient.instance(), ChatRepository.getInstance(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Seamless white status bar with dark icons
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
                onChatSelected = { user ->
                    viewModel.createPrivateChannel(user) { cid ->
                        if (cid != null) {
                            startActivity(MessageListActivity.createIntent(this, cid, user.name))
                            finish()
                        } else {
                            Toast.makeText(this, "Failed to start chat", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onCreateGroup = {
                    startActivity(Intent(this, CreateGroupActivity::class.java))
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendListScreen(
    viewModel: FriendListViewModel,
    onBack: () -> Unit,
    onChatSelected: (User) -> Unit,
    onCreateGroup: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val coda = FontFamily(Font(resId = R.font.coda_extrabold))

    LaunchedEffect(Unit) {
        viewModel.loadFriends()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding(),
        topBar = {
            Column {
                FriendListTopBar(coda = coda, onBack = onBack)
                FriendListHeader(onCreateGroup)
            }
        },
        containerColor = Color.White
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(padding)
        ) {
            when (val state = uiState) {
                is FriendListUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF0F52BA))
                }

                is FriendListUiState.Error -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.message, color = Color.Red)
                }

                is FriendListUiState.Success -> {
                    if (state.users.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No friends found", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(state.users) { user ->
                                FriendRow(user = user, onClick = { onChatSelected(user) }, coda = coda)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FriendListTopBar(coda: FontFamily, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "New Chat",
            fontFamily = coda,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 30.sp,
            color = Color.Black
        )
    }
}

@Composable
fun FriendListHeader(onCreateGroup: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
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
}

@Composable
fun FriendRow(user: User, onClick: () -> Unit, coda: FontFamily) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = user.image.ifBlank { null },
            contentDescription = "User Avatar",
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color(0xFFE0E0E0))
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name.ifBlank { user.id },
                fontFamily = coda,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Tap to start chat",
                fontSize = 13.sp,
                color = Color.Gray
            )
        }

        Icon(
            Icons.Default.ChatBubbleOutline,
            contentDescription = "Chat",
            tint = Color(0xFF0F52BA),
            modifier = Modifier.size(20.dp)
        )
    }
}
