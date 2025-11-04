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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
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
import coil.compose.AsyncImage
import com.example.streamchat.data.repository.ChatRepository
import com.example.streamchat.ui.ViewModelFactory
import com.example.streamchat.ui.channels.ChannelListViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.models.User
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CreateGroupActivity : ComponentActivity() {

    private val chatClient = ChatClient.instance()
    private val viewModel by viewModels<ChannelListViewModel> {
        ViewModelFactory(chatClient, ChatRepository.getInstance(applicationContext))
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
            CreateGroupScreen(
                viewModel = viewModel,
                onBack = { finish() },
                onGroupCreated = { success ->
                    Toast.makeText(
                        this,
                        if (success) "Group created!" else "Failed to create group",
                        Toast.LENGTH_SHORT
                    ).show()
                    if (success) finish()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    viewModel: ChannelListViewModel,
    onBack: () -> Unit,
    onGroupCreated: (Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val coda = FontFamily(Font(R.font.coda_extrabold))
    val roboto = FontFamily(Font(R.font.roboto_flex_light))

    val firestore = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }

    var groupName by remember { mutableStateOf("") }
    var friends by remember { mutableStateOf<List<User>>(emptyList()) }
    val selectedUsers = remember { mutableStateListOf<User>() }

    // 🔹 Load friends only (not all Stream users)
    LaunchedEffect(Unit) {
        val currentUid = auth.currentUser?.uid ?: return@LaunchedEffect
        try {
            val friendDocs = firestore.collection("friends")
                .document(currentUid)
                .collection("list")
                .get()
                .await()

            val friendList = friendDocs.mapNotNull { doc ->
                val friendId = doc.id
                val username = doc.getString("username") ?: ""
                val email = doc.getString("email") ?: ""
                User(
                    id = friendId,
                    name = username,
                    extraData = mutableMapOf("email" to email)
                )
            }
            friends = friendList
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to load friends: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val textFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Black.copy(alpha = 0.05f),
        unfocusedContainerColor = Color.Black.copy(alpha = 0.03f),
        cursorColor = Color(0xFF0F52BA),
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        focusedTextColor = Color.Black,
        unfocusedTextColor = Color.Black,
        focusedLabelColor = Color(0xFF0F52BA),
        unfocusedLabelColor = Color.Gray,
        focusedLeadingIconColor = Color(0xFF0F52BA),
        unfocusedLeadingIconColor = Color.Gray
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
                    "Create Group",
                    fontFamily = coda,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 30.sp,
                    color = Color.Black
                )
            }
        },
        bottomBar = {
            Button(
                onClick = {
                    coroutineScope.launch {
                        if (selectedUsers.isEmpty()) {
                            Toast.makeText(context, "Select at least one friend", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        val cid = viewModel.createChannel(
                            memberIds = selectedUsers.map { it.id },
                            groupName = groupName.ifBlank { null }
                        )

                        if (cid != null) {
                            val intent = MessageListActivity.createIntent(context, cid, groupName)
                            context.startActivity(intent)
                            onGroupCreated(true)
                        } else {
                            onGroupCreated(false)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F52BA))
            ) {
                Text("Create Group", fontFamily = roboto, fontSize = 16.sp, color = Color.White)
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
            // 🔹 Group Name Input
            TextField(
                value = groupName,
                onValueChange = { groupName = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(50),
                label = { Text("Group Name (optional)", fontFamily = roboto) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = "Group name",
                        tint = Color(0xFF0F52BA)
                    )
                },
                colors = textFieldColors,
                singleLine = true
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "Select Friends to Add",
                fontFamily = coda,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F52BA),
                fontSize = 18.sp
            )

            Spacer(Modifier.height(8.dp))

            if (friends.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No friends available", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(friends) { user ->
                        FriendSelectableRow(
                            user = user,
                            selected = selectedUsers.contains(user),
                            onToggle = {
                                if (selectedUsers.contains(user)) selectedUsers.remove(user)
                                else selectedUsers.add(user)
                            },
                            roboto = roboto
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FriendSelectableRow(user: User, selected: Boolean, onToggle: () -> Unit, roboto: FontFamily) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() },
        color = if (selected) Color(0xFFEFF3FF) else Color.White,
        tonalElevation = if (selected) 1.dp else 0.dp,
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = user.image.ifBlank { null },
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0E0E0))
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = user.name.ifBlank { user.id },
                fontFamily = roboto,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF0F52BA),
                    uncheckedColor = Color.Gray
                )
            )
        }
    }
}
