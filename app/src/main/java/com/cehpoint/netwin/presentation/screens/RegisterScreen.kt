package com.cehpoint.netwin.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.cehpoint.netwin.R
import com.cehpoint.netwin.presentation.navigation.ScreenRoutes
import com.cehpoint.netwin.presentation.viewmodels.AuthViewModel
import kotlinx.coroutines.launch

@Composable
fun RegisterScreenUI(
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel()
) {
    // State simplified to only include email registration
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var termsAccepted by remember { mutableStateOf(false) }

    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isAuthenticated, currentUser) {
        if (isAuthenticated && currentUser != null) {
            navController.navigate(ScreenRoutes.TournamentsScreen) {
                popUpTo("register") { inclusive = true }
            }
        }
    }

    LaunchedEffect(error) {
        error?.let { errorMsg ->
            coroutineScope.launch {
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Background image
        Image(
            painter = painterResource(id = R.drawable.login_screen),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Optional dark overlay for contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xAA000000), Color(0xAA000000))
                    )
                )
        )

        // Foreground content
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Create Your Account",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Sign up with your email and password", // Updated text
                color = Color.Gray,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(32.dp))

            // Phone option logic is removed, directly showing Email registration
            EmailPasswordRegistration(
                email = email,
                password = password,
                confirmPassword = confirmPassword,
                termsAccepted = termsAccepted,
                isLoading = isLoading,
                onEmailChange = { email = it },
                onPasswordChange = { password = it },
                onConfirmPasswordChange = { confirmPassword = it },
                onTermsAccepted = { termsAccepted = it },
                onSubmit = {
                    if (password == confirmPassword && termsAccepted) {
                        viewModel.signUp(email, password)
                    }
                }
            )

            Spacer(Modifier.height(24.dp))

            TextButton(
                onClick = { navController.navigate(ScreenRoutes.LoginScreen) }
            ) {
                Text(
                    "Already have an account? Sign In",
                    color = Color.Cyan
                )
            }
        }
    }
}

// Removed RegistrationOption enum
// Removed RegistrationOptionCard composable

@Composable
fun EmailPasswordRegistration(
    email: String,
    password: String,
    confirmPassword: String,
    termsAccepted: Boolean,
    isLoading: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onTermsAccepted: (Boolean) -> Unit,
    onSubmit: () -> Unit
    // Removed onBack parameter
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Sign up with Email",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email", color = Color.White) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.Cyan,
                unfocusedBorderColor = Color.Gray
            ),
            enabled = !isLoading
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password", color = Color.White) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.Cyan,
                unfocusedBorderColor = Color.Gray
            ),
            enabled = !isLoading
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text("Confirm Password", color = Color.White) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.Cyan,
                unfocusedBorderColor = Color.Gray
            ),
            enabled = !isLoading
        )

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = termsAccepted,
                onCheckedChange = onTermsAccepted,
                colors = CheckboxDefaults.colors(
                    checkedColor = Color.Cyan,
                    uncheckedColor = Color.Gray
                )
            )
            Text(
                "I agree to Terms & Privacy Policy",
                color = Color.White,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(24.dp))

        // Replaced Row with a single full-width Button
        Button(
            onClick = onSubmit,
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank() &&
                    password == confirmPassword && termsAccepted,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.Cyan
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
            modifier = Modifier.fillMaxWidth() // Made button full width
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.Cyan
                )
            } else {
                Text("Sign Up", color = Color.Cyan, fontWeight = FontWeight.Bold)
            }
        }
    }
}
