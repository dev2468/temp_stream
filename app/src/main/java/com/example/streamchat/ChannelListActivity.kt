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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.abs
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding(),
        topBar = {
            Column(modifier = Modifier.background(Color.White)) {
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

                    // --- extract events (channels flagged as event channels) ---
                    val eventChannels = allChannels.filter { (it.extraData["is_event_channel"] as? Boolean) == true }
                    // chats excluding events for the chat list below:
                    val nonEventChannels = allChannels.filter { (it.extraData["is_event_channel"] as? Boolean) != true }

                    val filteredChats = when (selectedFilter) {
                        "Unread" -> nonEventChannels.filter { extractUnreadCountSafe(it) > 0 }
                        "Groups" -> nonEventChannels.filter { it.memberCount > 2 }
                        "DMs" -> nonEventChannels.filter { it.memberCount <= 2 }
                        else -> nonEventChannels
                    }

                    // Single LazyColumn containing: events carousel (if any), Chats header, chat rows
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 100.dp) // space for nav
                    ) {
                        // --- Events carousel block ---
                        if (eventChannels.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Events", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                                        // small icon button to go to event creation or full list if you want
                                        IconButton(onClick = onCreateEventClick) {
                                            Icon(Icons.Default.Add, contentDescription = "Create Event", tint = Color(0xFF0F52BA))
                                        }
                                    }

                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(end = 16.dp)
                                    ) {
                                        items(eventChannels) { eventChannel ->
                                            EventCardFromChannel(eventChannel) {
                                                // route to details if channel has join link or event id in extraData
                                                val cid = eventChannel.cid
                                                if (!cid.isNullOrBlank()) {
                                                    // If event created a channel and stored event id in extraData, prefer eventDetails
                                                    val eventId = eventChannel.extraData["event_id"] as? String
                                                    if (!eventId.isNullOrBlank()) {
                                                        context.startActivity(EventDetailsActivity.createIntent(context, eventId))
                                                    } else {
                                                        // fallback to opening the channel chat
                                                        context.startActivity(
                                                            MessageListActivity.createIntent(context, cid, eventChannel.name ?: "")
                                                        )
                                                    }
                                                } else {
                                                    // fallback behavior
                                                    Log.w("ChannelList", "Event clicked but no cid present: $eventChannel")
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        // --- Chats header ---
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text("Chats", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                            }
                        }

                        // --- Chat items ---
                        items(filteredChats) { channel ->
                            ChannelRow(channel = channel, onClick = { onChannelClick(channel) }, coda)
                            Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                        }
                    }
                }
            }

            // Floating navigation bar aligned to the bottom (keeps same API)
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

/** Event card visual created from a channel that represents an event.
 *
 * Uses possible extraData keys:
 *  - "event_date" (Long milliseconds) to show days left
 *  - "is_event_channel" (Boolean) to mark event channels (we already filter on this)
 *  - "event_id" (String) fallback to open event detail page if present
 */
@Composable
fun EventCardFromChannel(channel: Channel, onClick: () -> Unit) {
    val name = channel.name.ifEmpty { channel.id ?: "Event" }
    val cover = channel.image ?: channel.extraData["coverImage"] as? String
    val eventDateMillis = (channel.extraData["event_date"] as? Number)?.toLong()
    val daysText = remember(eventDateMillis) {
        eventDateMillis?.let {
            val now = System.currentTimeMillis()
            val diff = it - now
            val days = (diff.toDuration(DurationUnit.MILLISECONDS).toLong(DurationUnit.DAYS))
            when {
                diff <= 0L -> "Today"
                days <= 1L -> "${abs(days)} day"
                else -> "${abs(days)} days"
            }
        } ?: ""
    }

    Box(
        modifier = Modifier
            .width(280.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        if (!cover.isNullOrBlank()) {
            AsyncImage(
                model = cover,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // placeholder background if no cover
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFeceff1)))
        }

        // gradient overlay for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0x00000000), Color(0xAA000000)),
                        startY = 60f
                    )
                )
        )

        // title and badge
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            if (daysText.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F52BA),
                    tonalElevation = 2.dp
                ) {
                    Text(daysText, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun TopBarTitle(coda: FontFamily, onAddClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Chats",
            fontFamily = coda,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 34.sp,
            color = Color.Black
        )

        Surface(
            onClick = onAddClick,
            shape = RoundedCornerShape(50),
            color = Color(0xFF0F52BA)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Group,
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

// your PillBottomNav kept as-is (glassified earlier)
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

        // Outer glassy container with your existing color scheme
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Color(0xFF4A4A4A).copy(alpha = 0.75f) // Same dark tone, translucent for glass feel
                )
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                )
        ) {
            val xOffset by animateDpAsState(
                targetValue = when (selectedTab) {
                    "profile" -> (rowWidth / 3) * 2
                    else -> 0.dp
                },
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )

            // Highlight (blue moving pill)
            Box(
                modifier = Modifier
                    .width(rowWidth / 3)
                    .fillMaxHeight()
                    .offset(x = xOffset)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF0F52BA),
                                Color(0xFF2765C7)
                            )
                        )
                    )
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
                // 💬 Chats
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected("channels") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Chat, contentDescription = "Channels", tint = Color.White)
                }

                // 📅 Events (Center)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onCenterClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Event, contentDescription = "Events", tint = Color.White)
                }

                // 👤 Profile
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onProfileClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White)
                }
            }
        }
    }
}

// Profile screen and supporting composables unchanged below
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, onLogout: () -> Unit, onTabSelected: (String) -> Unit, onCreateEventClick: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Use the new and improved ProfileContent
            BetterProfileContent(user = user, onLogout = onLogout)

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
fun BetterProfileContent(user: FirebaseUser?, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    val firestore = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val client = ChatClient.instance()

    var username by remember { mutableStateOf<String?>(null) }
    var photoUrl by remember { mutableStateOf(user?.photoUrl?.toString() ?: "") }
    var isLoading by remember { mutableStateOf(true) }

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

                    client.getCurrentUser()?.let { currentUser ->
                        client.updateUser(currentUser.copy(image = downloadUrl)).enqueue()
                    }
                }
        }
    }

    LaunchedEffect(user?.uid) {
        if (user == null) {
            isLoading = false
            return@LaunchedEffect
        }
        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { snapshot ->
                username = snapshot.getString("username") ?: user.displayName ?: "Unknown"
                snapshot.getString("profileImageUrl")?.let { photoUrl = it }
            }
            .addOnCompleteListener { isLoading = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF0F52BA), Color(0xFF1A73E8))
                )
            )
            .padding(bottom = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(Modifier.height(50.dp))

        // Profile Image
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .clickable { imagePicker.launch("image/*") }
        ) {
            AsyncImage(
                model = if (photoUrl.isNotBlank()) photoUrl else R.drawable.ic_person_placeholder,
                contentDescription = "Profile Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = username ?: "Loading...",
            color = Color.White,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(30.dp))


        // 🌫️ Frosted Glass Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(22.dp))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            ProfileGlassItem("Username", username ?: "Not set", Icons.Default.AccountCircle)
            ProfileGlassItem("Email", user?.email ?: "No email provided", Icons.Default.Email)
        }

        Spacer(Modifier.height(22.dp))

        // 🌫️ Frosted Action Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(22.dp))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            ProfileGlassButton("Change Password", Icons.Default.LockReset) {
                user?.email?.let { FirebaseAuth.getInstance().sendPasswordResetEmail(it) }
            }
            Spacer(Modifier.height(14.dp))
            ProfileGlassButton("Logout", Icons.AutoMirrored.Filled.ExitToApp, Color(0xFFFFC1C1)) {
                onLogout()
            }
        }

        Spacer(Modifier.height(120.dp))
    }
}

@Composable
fun ProfileGlassItem(label: String, value: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            Text(value, fontSize = 17.sp, color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ProfileGlassButton(label: String, icon: ImageVector, tint: Color = Color.White, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun ProfileHeader(
    username: String?,
    photoUrl: String,
    isLoading: Boolean,
    onImageClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(Color(0xFF0F52BA)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Card(
                shape = CircleShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .size(110.dp)
                    .clickable(onClick = onImageClick)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = if (photoUrl.isNotBlank()) photoUrl else R.drawable.ic_person_placeholder,
                        contentDescription = "Profile Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(32.dp)
                            .background(Color.White, CircleShape)
                            .border(2.dp, Color(0xFF0F52BA), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Edit Profile Picture",
                            tint = Color(0xFF0F52BA),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    text = username ?: "Anonymous",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun BetterProfileInfoItem(icon: ImageVector, label: String, value: String) {
    ListItem(
        headlineContent = { Text(value, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(label, fontSize = 14.sp) },
        leadingContent = { Icon(icon, contentDescription = label, tint = Color(0xFF0F52BA)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    color: Color = Color.Black,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(text, color = color, fontWeight = FontWeight.Medium) },
        leadingContent = { Icon(icon, contentDescription = text, tint = color) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
