package com.example.streamchat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.models.Channel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

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
                        onCreateEventClick = { startActivity(Intent(this, CreateEventActivity::class.java)) },
                        onTabSelected = { selectedTab = it }
                    )

                    "profile" -> ProfileScreen(
                        onBack = { selectedTab = "channels" },
                        onLogout = {
                            FirebaseAuth.getInstance().signOut()
                            startActivity(Intent(this, FirebaseAuthActivity::class.java))
                            finish()
                        },
                        onTabSelected = { selectedTab = it },
                        onCreateEventClick = { startActivity(Intent(this, CreateEventActivity::class.java)) }
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
    onCreateEventClick: () -> Unit,
    onTabSelected: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }

    val filters = listOf("All", "Unread", "Groups", "DMs")
    val coda = FontFamily(Font(resId = R.font.coda_extrabold))

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding(),
        topBar = {
            Column(modifier = Modifier.background(Color.White)) {
                // The Top Bar now includes the new "Friends" button
                TopBarTitle(coda = coda, onAddClick = onAddClick)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    FilterRow(filters = filters, selected = selectedFilter, onSelected = { selectedFilter = it })
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White)
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
                        "Unread" -> allChannels.filter { (extractUnreadCountSafe(it)) > 0 }
                        "Groups" -> allChannels.filter { it.memberCount > 2 }
                        "DMs" -> allChannels.filter { it.memberCount <= 2 }
                        else -> allChannels
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 100.dp) // Space for floating navbar
                    ) {
                        items(filtered) { channel ->
                            ChannelRow(channel = channel, onClick = { onChannelClick(channel) }, coda)
                            Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                        }
                    }
                }
            }

            // Floating navigation bar aligned to the bottom
            PillBottomNav(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedTab = "channels",
                onTabSelected = onTabSelected,
                onCenterClick = onCreateEventClick,
                onProfileClick = { onTabSelected("profile") }
            )
        }
    }
}

@Composable
fun TopBarTitle(coda: FontFamily, onAddClick: () -> Unit) {
    // Refactored into a Row to accommodate the new button
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween // Pushes items to the ends
    ) {
        Text(
            text = "Chats",
            fontFamily = coda,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 34.sp,
            color = Color.Black
        )

        // New "Friends" button
        Surface(
            onClick = onAddClick,
            shape = RoundedCornerShape(50), // Pill shape
            color = Color(0xFF0F52BA)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Group, // Friends icon
                    contentDescription = "Friends",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Friends",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun FilterRow(filters: List<String>, selected: String, onSelected: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(filters) { f ->
            val isSelected = f == selected
            val bg by animateColorAsState(if (isSelected) Color(0xFF0F52BA) else Color(0xFFF3F3F3))
            val textColor = if (isSelected) Color.White else Color.Gray

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = bg,
                modifier = Modifier
                    .height(36.dp)
                    .clickable { onSelected(f) }
            ) {
                Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = f,
                        color = textColor,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
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
    val avatarUrl = if (isGroup) channel.image else otherMember?.image
    val lastMessageText = channel.messages.lastOrNull()?.text ?: "No messages yet"
    val lastMessageTime = channel.lastMessageAt?.let {
        try {
            val instant = Instant.ofEpochMilli(it.time)
            DateTimeFormatter.ofPattern("hh:mm", Locale.getDefault()).withZone(ZoneId.systemDefault()).format(instant)
        } catch (_: Exception) { "" }
    } ?: ""
    val unreadCount = extractUnreadCountSafe(channel)

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "avatar",
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xFFE0E0E0))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    displayName,
                    fontFamily = coda,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                if (lastMessageTime.isNotEmpty()) Text(lastMessageTime, color = Color.Gray, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    lastMessageText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.Gray,
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
                            Text(unreadCount.coerceAtMost(99).toString(), color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

fun extractUnreadCountSafe(channel: Channel): Int {
    return try {
        val method = channel::class.java.methods.firstOrNull { it.name.equals("getUnreadCount", ignoreCase = true) }
        val res = method?.invoke(channel)
        when (res) {
            is Int -> res
            is Number -> res.toInt()
            else -> (channel.extraData["unread_count"] as? Int) ?: 0
        }
    } catch (_: Exception) {
        (channel.extraData["unread_count"] as? Int) ?: 0
    }
}

@Composable
fun PillBottomNav(
    modifier: Modifier = Modifier,
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    onCenterClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        var rowWidth by remember { mutableStateOf(0.dp) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(50))
        ) {
            val xOffset by animateDpAsState(
                targetValue = when (selectedTab) {
                    "profile" -> (rowWidth / 3) * 2
                    else -> 0.dp
                },
                // --- SMOOTHER ANIMATION ---
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )

            Surface(
                color = Color(0xFF4A4A4A),
                modifier = Modifier.fillMaxSize()
            ) {}

            Box(
                modifier = Modifier
                    .width(rowWidth / 3)
                    .fillMaxHeight()
                    .offset(x = xOffset)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF0F52BA))
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        rowWidth = with(density) { it.size.width.toDp() }
                    },
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().clickable { onTabSelected("channels") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Chat, contentDescription = "Channels", tint = Color.White)
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().clickable { onCenterClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Event, contentDescription = "Events", tint = Color.White)
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().clickable { onProfileClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, onLogout: () -> Unit, onTabSelected: (String) -> Unit, onCreateEventClick: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ProfileContent(user = user, onLogout = onLogout)

            PillBottomNav(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedTab = "profile",
                onTabSelected = onTabSelected,
                onCenterClick = onCreateEventClick,
                onProfileClick = { onTabSelected("profile") }
            )
        }
    }
}

@Composable
fun ProfileContent(user: FirebaseUser?, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    val firestore = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val client = ChatClient.instance()

    var username by remember { mutableStateOf<String?>(null) }
    var photoUrl by remember { mutableStateOf(user?.photoUrl?.toString() ?: "") }

    val imagePicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val uid = user?.uid ?: return@let
            val fileRef = storage.reference.child("profile_images/$uid.jpg")

            fileRef.putFile(it)
                .continueWithTask { task ->
                    if (!task.isSuccessful) throw task.exception ?: Exception("Upload failed")
                    fileRef.downloadUrl
                }
                .addOnSuccessListener { downloadUri ->
                    val downloadUrl = downloadUri.toString()
                    photoUrl = downloadUrl

                    firestore.collection("users").document(uid)
                        .update("profileImageUrl", downloadUrl)
                        .addOnFailureListener {
                            firestore.collection("users").document(uid)
                                .set(mapOf("profileImageUrl" to downloadUrl))
                        }

                    val currentUser = client.getCurrentUser()
                    if (currentUser != null) {
                        val updatedUser = currentUser.copy(image = downloadUrl)
                        client.updateUser(updatedUser).enqueue()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ProfileUpload", "Failed to upload: ${e.message}")
                }
        }
    }

    LaunchedEffect(user?.uid) {
        user?.uid?.let { uid ->
            firestore.collection("users").document(uid).get()
                .addOnSuccessListener { snapshot ->
                    username = snapshot.getString("username") ?: "Unknown"
                    photoUrl = snapshot.getString("profileImageUrl") ?: photoUrl
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
            .background(Color.White)
            .padding(bottom = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = if (photoUrl.isNotBlank()) photoUrl else R.drawable.ic_person_placeholder,
                        contentDescription = "Profile Image",
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                    )

                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Edit",
                        tint = Color(0xFF0F52BA),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .background(Color(0x80FFFFFF), CircleShape)
                            .padding(4.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(text = username ?: "Loading...", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }

        Spacer(Modifier.height(24.dp))

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
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Color.Black)
    }
}
