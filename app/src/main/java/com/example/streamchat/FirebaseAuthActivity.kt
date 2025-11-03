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
import com.example.streamchat.data.repository.ChatRepository
import com.example.streamchat.ui.ViewModelFactory
import com.example.streamchat.ui.auth.AuthUiState
import com.example.streamchat.ui.auth.FirebaseAuthViewModel
import io.getstream.chat.android.client.ChatClient

class FirebaseAuthActivity : ComponentActivity() {

    private val viewModel: FirebaseAuthViewModel by viewModels {
        ViewModelFactory(
            ChatClient.instance(),
            ChatRepository.getInstance(applicationContext)
        )
    }

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

            ModernAuthScreen(
                uiState = uiState,
                onSignIn = { email, password -> viewModel.signIn(email, password) },
                onSignUp = { email, password, fullName -> viewModel.signUp(email, password, fullName) }
            )
        }
    }
}

@Composable
fun ModernAuthScreen(
    uiState: AuthUiState,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var isSignUpMode by remember { mutableStateOf(false) }
    val isLoading = uiState is AuthUiState.Loading

    val robotoFlexLight = FontFamily(Font(R.font.roboto_flex_light))

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

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = if (isSignUpMode) "Create Account" else "Log In",
                fontFamily = robotoFlexLight,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            val textFieldColors = TextFieldDefaults.colors(
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
                    value = fullName,
                    onValueChange = { fullName = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(50),
                    label = { Text("Full Name", fontFamily = robotoFlexLight) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    colors = textFieldColors,
                    singleLine = true,
                    enabled = !isLoading
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            TextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(50),
                label = { Text("Email", fontFamily = robotoFlexLight) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                colors = textFieldColors,
                singleLine = true,
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(50),
                label = { Text("Password", fontFamily = robotoFlexLight) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                colors = textFieldColors,
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(24.dp))

            ClickableText(
                text = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            color = Color.White.copy(alpha = 0.8f),
                            fontFamily = robotoFlexLight
                        )
                    ) {
                        append(if (isSignUpMode) "Already have an account? " else "Don't have an account? ")
                    }
                    withStyle(
                        style = SpanStyle(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = robotoFlexLight
                        )
                    ) {
                        append(if (isSignUpMode) "Log In" else "Sign Up")
                    }
                },
                onClick = { isSignUpMode = !isSignUpMode }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (isSignUpMode) onSignUp(email, password, fullName)
                    else onSignIn(email, password)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank() && (!isSignUpMode || fullName.isNotBlank()),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black.copy(alpha = 0.35f),
                    contentColor = Color.White,
                    disabledContainerColor = Color.Black.copy(alpha = 0.2f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                } else {
                    Text(
                        if (isSignUpMode) "Sign Up" else "Log In",
                        fontFamily = robotoFlexLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
