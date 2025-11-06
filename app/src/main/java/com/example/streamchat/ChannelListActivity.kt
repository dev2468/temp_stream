package com.example.streamchat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import com.example.streamchat.ui.channels.ChannelListUiState
import com.example.streamchat.ui.channels.ChannelListViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.models.Channel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore




class ChannelListActivity : ComponentActivity() {

    private val viewModel: ChannelListViewModel by viewModels {
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
            var selectedTab by remember { mutableStateOf("channels") }

            Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                when (selectedTab) {
                    "channels" -> ChannelListScreen(
                        viewModel = viewModel,
                        onChannelClick = { channel ->
                            val cid = channel.cid
                            if (!cid.isNullOrBlank()) {
                                startActivity(
                                    MessageListActivity.createIntent(
                                        this, cid, channel.name ?: ""
                                    )
                                )
                            } else {
                                Log.e("ChannelListActivity", "Channel cid is null: $channel")
                            }
                        },
                        onAddClick = { startActivity(Intent(this, FriendListActivity::class.java)) },
                        onTabSelected = { selectedTab = it }
                    )

                    "profile" -> ProfileScreen(
                        onBack = { selectedTab = "channels" },
                        onLogout = {
                            FirebaseAuth.getInstance().signOut()
                            startActivity(Intent(this, FirebaseAuthActivity::class.java))
                            finish()
                        },
                        onTabSelected = { selectedTab = it }
                    )
                }
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
    onTabSelected: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Unread", "Groups", "DMs")
    val coda = FontFamily(Font(resId = R.font.coda_extrabold))

    Scaffold(
        modifier = Modifier.fillMaxSize().background(Color.White).systemBarsPadding(),
        topBar = {
            Column {
                TopBarTitle(coda)
                FilterRow(filters = filters, selected = selectedFilter, onSelected = { selectedFilter = it })
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick, containerColor = Color(0xFF0F52BA)) {
                Icon(Icons.Default.Add, contentDescription = "New chat", tint = Color.White)
            }
        },
        bottomBar = { BottomNavigationBar(selectedTab = "channels", onTabSelected = onTabSelected) }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding)
        ) {
            when (uiState) {
                is ChannelListUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF0F52BA))
                }

                is ChannelListUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text((uiState as ChannelListUiState.Error).message, color = Color.Red)
                }

                is ChannelListUiState.Empty -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("No chats yet — start a new one!", color = Color.Gray)
                }

                is ChannelListUiState.Success -> {
                    val allChannels = (uiState as ChannelListUiState.Success).channels
                    val filtered = when (selectedFilter) {
                        "Unread" -> allChannels.filter { (it.extraData["unread_count"] as? Int ?: 0) > 0 }
                        "Groups" -> allChannels.filter { it.memberCount > 2 }
                        "DMs" -> allChannels.filter { it.memberCount <= 2 }
                        else -> allChannels
                    }

                    LazyColumn(Modifier.fillMaxSize()) {
                        items(filtered) { channel ->
                            ChannelRow(channel = channel, onClick = { onChannelClick(channel) }, coda)
                            Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)
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
        Modifier.fillMaxWidth()
            .background(Color.White)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Chats",
            fontFamily = coda,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 34.sp,
            color = Color.Black,
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}

@Composable
fun FilterRow(filters: List<String>, selected: String, onSelected: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        filters.forEach { f ->
            val isSelected = f == selected
            val bg by animateColorAsState(if (isSelected) Color(0xFF0F52BA) else Color(0xFFF3F3F3))
            val textColor = if (isSelected) Color.White else Color.Gray

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = bg,
                modifier = Modifier.height(36.dp).wrapContentWidth().clickable { onSelected(f) }
            ) {
                Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text(text = f, color = textColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
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
        isGroup -> {
            val otherMembers = channel.members.filter { it.user.id != currentUserId }.joinToString(", ") { it.user.name.ifEmpty { it.user.id } }
            channel.name.ifEmpty { otherMembers.ifEmpty { "Unnamed Group" } }
        }
        !otherMember?.name.isNullOrBlank() -> otherMember!!.name
        !otherMember?.id.isNullOrBlank() -> otherMember!!.id
        else -> "Unknown"
    }
    val avatarUrl = if (isGroup) channel.image else otherMember?.image
    val lastMessageText = channel.messages.lastOrNull()?.text ?: "No messages yet"
    val lastMessageTime = channel.lastMessageAt?.let {
        try {
            val instant = Instant.ofEpochMilli(it.time)
            DateTimeFormatter.ofPattern("hh:mm", Locale.getDefault()).withZone(ZoneId.systemDefault()).format(instant)
        } catch (_: Exception) { "" }
    } ?: ""
    val unreadCount = (channel.extraData["unread_count"] as? Int) ?: 0

    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "avatar",
            modifier = Modifier.size(52.dp).clip(CircleShape).background(Color(0xFFE0E0E0))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(displayName, fontFamily = coda, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                if (lastMessageTime.isNotEmpty()) Text(lastMessageTime, color = Color.Gray, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(lastMessageText, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Gray, modifier = Modifier.weight(1f))
                if (unreadCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF0F52BA),
                        modifier = Modifier.padding(start = 8.dp).size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(unreadCount.coerceAtMost(99).toString(), color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/* -------------------- BOTTOM NAVIGATION -------------------- */
@Composable
fun BottomNavigationBar(selectedTab: String, onTabSelected: (String) -> Unit) {
    NavigationBar(containerColor = Color.White) {
        NavigationBarItem(
            selected = selectedTab == "channels",
            onClick = { onTabSelected("channels") },
            icon = { Icon(Icons.Default.Chat, contentDescription = "Channels") },
            label = { Text("Channels") }
        )
        NavigationBarItem(
            selected = selectedTab == "profile",
            onClick = { onTabSelected("profile") },
            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
            label = { Text("Profile") }
        )
    }
}

/* -------------------- PROFILE SCREEN -------------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, onLogout: () -> Unit, onTabSelected: (String) -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        },
        bottomBar = { BottomNavigationBar(selectedTab = "profile", onTabSelected = onTabSelected) }
    ) { padding ->
        ProfileContent(user = user, onLogout = onLogout, modifier = Modifier.padding(padding))
    }
}
@Composable
fun ProfileContent(user: FirebaseUser?, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    var username by remember { mutableStateOf<String?>(null) }

    // Fetch username from Firebase Realtime Database
    LaunchedEffect(user?.uid) {
        user?.uid?.let { uid ->
            val db = FirebaseFirestore.getInstance()
            db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { snapshot ->
                    val fetchedUsername = snapshot.getString("username")
                    username = fetchedUsername ?: "Unknown"
                }
                .addOnFailureListener {
                    username = "Unknown"
                }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header with theme blue
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color(0xFF0F52BA)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = user?.photoUrl ?: "",
                    contentDescription = "Profile Image",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = username ?: "Loading...",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Info section
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            ProfileInfoItem("Username", username ?: user?.uid?.take(8) ?: "Unknown")
            ProfileInfoItem("Email", user?.email ?: "No email")

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { user?.email?.let { FirebaseAuth.getInstance().sendPasswordResetEmail(it) } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F52BA))
            ) {
                Text("Change Password", color = Color.White)
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
            ) {
                Text("Logout", color = Color.White)
            }
        }
    }
}


@Composable
fun ProfileInfoItem(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Color.Black)
    }
}
 
