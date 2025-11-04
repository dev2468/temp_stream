package com.example.streamchat

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.streamchat.data.repository.ChatRepository
import com.example.streamchat.ui.ViewModelFactory
import com.example.streamchat.ui.auth.AuthUiState
import com.example.streamchat.ui.auth.FirebaseAuthViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.getstream.chat.android.client.ChatClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirebaseAuthActivity : ComponentActivity() {

    private val viewModel: FirebaseAuthViewModel by viewModels {
        ViewModelFactory(
            ChatClient.instance(),
            ChatRepository.getInstance(applicationContext)
        )
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by viewModel.uiStateLiveData.observeAsState(AuthUiState.Initial)

            LaunchedEffect(uiState) {
                when (val state = uiState) {
                    is AuthUiState.Success -> {
                        startActivity(Intent(this@FirebaseAuthActivity, ChannelListActivity::class.java))
                        finish()
                    }
                    is AuthUiState.Error -> {
                        Toast.makeText(this@FirebaseAuthActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                    else -> {}
                }
            }

            UsernameAuthScreen(
                uiState = uiState,
                onSignIn = { username, password ->
                    signInWithUsername(username, password)
                },
                onSignUp = { email, password, username ->
                    validateAndSignUp(username, email, password)
                }
            )
        }
    }

    // ✅ Validate username, ensure uniqueness, and save user record
    private fun validateAndSignUp(username: String, email: String, password: String) {
        val context = this

        if (username.isBlank()) {
            Toast.makeText(context, "Please enter a username", Toast.LENGTH_SHORT).show()
            return
        }

        if (!username.matches(Regex("^[a-z0-9_]{3,15}$"))) {
            Toast.makeText(
                context,
                "Username must be 3–15 lowercase letters, digits, or underscores",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        lifecycleScope.launch {
            try {
                val querySnapshot = firestore.collection("users")
                    .whereEqualTo("username", username.lowercase().trim())
                    .get()
                    .await()

                if (!querySnapshot.isEmpty) {
                    Toast.makeText(context, "Username already taken", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // ✅ Create Firebase Auth user
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user

                if (user != null) {
                    val uid = user.uid
                    val userMap = mapOf(
                        "uid" to uid,
                        "username" to username.lowercase().trim(),
                        "email" to email.trim(),
                        "createdAt" to System.currentTimeMillis()
                    )

                    // ✅ Save user info in Firestore
                    firestore.collection("users").document(uid).set(userMap).await()

                    Toast.makeText(context, "Account created successfully!", Toast.LENGTH_SHORT).show()

                    // ✅ Connect to Stream Chat and continue
                    viewModel.signIn(email, password)
                } else {
                    Toast.makeText(context, "Failed to create user", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ✅ Login using username (Firestore lookup → Firebase Auth sign-in)
    private fun signInWithUsername(username: String, password: String) {
        val context = this

        if (username.isBlank() || password.isBlank()) {
            Toast.makeText(context, "Enter both username and password", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val querySnapshot = firestore.collection("users")
                    .whereEqualTo("username", username.lowercase().trim())
                    .get()
                    .await()

                if (querySnapshot.isEmpty) {
                    Toast.makeText(context, "No account found with that username", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val document = querySnapshot.documents.first()
                val email = document.getString("email")

                if (email.isNullOrBlank()) {
                    Toast.makeText(context, "User record incomplete — missing email", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                viewModel.signIn(email, password)
            } catch (e: Exception) {
                Toast.makeText(context, "Login error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun UsernameAuthScreen(
    uiState: AuthUiState,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUpMode by remember { mutableStateOf(false) }
    val isLoading = uiState is AuthUiState.Loading
    val roboto = FontFamily(Font(R.font.roboto_flex_light))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F52BA), Color(0xFF487BCA), Color.White)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = "temp.",
                style = TextStyle(
                    fontFamily = FontFamily(Font(R.font.angkor_regular)),
                    fontSize = 80.sp,
                    color = Color.White,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.5f),
                        offset = androidx.compose.ui.geometry.Offset(4f, 4f),
                        blurRadius = 8f
                    )
                )
            )

            Spacer(Modifier.height(40.dp))

            Text(
                text = if (isSignUpMode) "Create Account" else "Log In",
                fontFamily = roboto,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = Color.White
            )

            Spacer(Modifier.height(24.dp))

            val fieldColors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Black.copy(alpha = 0.35f),
                unfocusedContainerColor = Color.Black.copy(alpha = 0.25f),
                cursorColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                focusedLeadingIconColor = Color.White,
                unfocusedLeadingIconColor = Color.White.copy(alpha = 0.7f)
            )

            if (isSignUpMode) {
                TextField(
                    value = username,
                    onValueChange = { username = it.lowercase().trim() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    label = { Text("Username (unique)", fontFamily = roboto) },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    colors = fieldColors,
                    singleLine = true,
                    enabled = !isLoading
                )
                Spacer(Modifier.height(16.dp))

                TextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    label = { Text("Email", fontFamily = roboto) },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    colors = fieldColors,
                    singleLine = true,
                    enabled = !isLoading
                )
            } else {
                TextField(
                    value = username,
                    onValueChange = { username = it.lowercase().trim() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    label = { Text("Username", fontFamily = roboto) },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    colors = fieldColors,
                    singleLine = true,
                    enabled = !isLoading
                )
            }

            Spacer(Modifier.height(16.dp))

            TextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                label = { Text("Password", fontFamily = roboto) },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                colors = fieldColors,
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !isLoading
            )

            Spacer(Modifier.height(24.dp))

            ClickableText(
                text = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(color = Color.White.copy(alpha = 0.8f), fontFamily = roboto)
                    ) {
                        append(if (isSignUpMode) "Already have an account? " else "Don't have an account? ")
                    }
                    withStyle(
                        style = SpanStyle(color = Color.White, fontWeight = FontWeight.Bold, fontFamily = roboto)
                    ) {
                        append(if (isSignUpMode) "Log In" else "Sign Up")
                    }
                },
                onClick = { isSignUpMode = !isSignUpMode }
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    if (isSignUpMode) onSignUp(email, password, username)
                    else onSignIn(username, password)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isLoading && username.isNotBlank() && password.isNotBlank() && (!isSignUpMode || email.isNotBlank()),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black.copy(alpha = 0.35f),
                    contentColor = Color.White
                )
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
                else Text(if (isSignUpMode) "Sign Up" else "Log In", fontFamily = roboto, fontWeight = FontWeight.Bold)
            }
        }
    }
}
