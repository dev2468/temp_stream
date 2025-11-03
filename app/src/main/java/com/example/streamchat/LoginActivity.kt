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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.streamchat.data.repository.ChatRepository
import com.example.streamchat.ui.ViewModelFactory
import com.example.streamchat.ui.login.LoginUiState
import com.example.streamchat.ui.login.LoginViewModel
import io.getstream.chat.android.client.ChatClient

class LoginActivity : ComponentActivity() {

    private val viewModel: LoginViewModel by viewModels {
        ViewModelFactory(
            ChatClient.instance(),
            ChatRepository.getInstance(applicationContext)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by viewModel.uiStateLiveData.observeAsState(LoginUiState.Initial)

            LaunchedEffect(uiState) {
                when (val state = uiState) {
                    is LoginUiState.Success -> {
                        startActivity(Intent(this@LoginActivity, ChannelListActivity::class.java))
                        finish()
                    }

                    is LoginUiState.Error -> {
                        Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_LONG).show()
                    }

                    else -> {}
                }
            }

            NewLoginScreen(
                uiState = uiState,
                onLogin = { username, _ ->
                    viewModel.loginWithServer(userId = username, userName = username)
                }
            )
        }
    }
}

@Composable
fun NewLoginScreen(
    uiState: LoginUiState,
    onLogin: (String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val isLoading = uiState is LoginUiState.Loading

    val robotoFlexLight = FontFamily(Font(R.font.roboto_flex_light))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F52BA), // Top Blue
                        Color(0xFF487BCA), // Middle Blue
                        Color(0xFFFFFFFF)  // Bottom White
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
        )

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
                text = "Log In",
                fontFamily = robotoFlexLight,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            val textFieldColors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                unfocusedContainerColor = Color.Black.copy(alpha = 0.3f),
                disabledContainerColor = Color.Black.copy(alpha = 0.2f),
                cursorColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = Color.White.copy(alpha = 0.8f),
                unfocusedLabelColor = Color.White.copy(alpha = 0.8f),
                focusedLeadingIconColor = Color.White,
                unfocusedLeadingIconColor = Color.White,
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                label = { Text("Username", fontFamily = robotoFlexLight) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Username") },
                colors = textFieldColors,
                singleLine = true,
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                label = { Text("Password", fontFamily = robotoFlexLight) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
                colors = textFieldColors,
                singleLine = true,
                enabled = !isLoading,
                visualTransformation = PasswordVisualTransformation()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ClickableText(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.White.copy(alpha = 0.8f), fontFamily = robotoFlexLight)) {
                            append("Don't have an account? ")
                        }
                        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold, fontFamily = robotoFlexLight)) {
                            append("Sign Up")
                        }
                    },
                    onClick = { }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Forgot Password",
                    color = Color.White,
                    fontFamily = robotoFlexLight,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { onLogin(username, password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0F52BA)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF0F52BA)
                    )
                } else {
                    Text("Log In", fontFamily = robotoFlexLight, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
