package com.cehpoint.netwin.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MailOutline
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.cehpoint.netwin.R
import com.cehpoint.netwin.presentation.navigation.ScreenRoutes
import com.cehpoint.netwin.presentation.viewmodels.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VerificationPendingScreen(
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 🔁 NEW: Automatically refresh verification status every few seconds
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.reloadUser()
            delay(5000) // check every 5 seconds
        }
    }

    // ✅ Navigation: When user verified, move to next screen automatically
    LaunchedEffect(currentUser, isLoading) {
        val user = currentUser
        if (!isLoading && user != null && user.isEmailVerified) {
            navController.navigate(ScreenRoutes.TournamentsScreen) {
                popUpTo(ScreenRoutes.VerificationPendingScreen) { inclusive = true }
            }
        }
    }

    // Error handling
    LaunchedEffect(error) {
        error?.let { errorMsg ->
            coroutineScope.launch {
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                viewModel.clearError()
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

        // Dark overlay
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
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.MailOutline,
                contentDescription = "Email Sent",
                tint = Color.Cyan,
                modifier = Modifier.size(64.dp)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "Verify Your Email Address",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "A verification link has been sent to your registered email. Once verified, you’ll be automatically redirected.",
                color = Color.Gray,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = Color.Cyan
                )
            } else {
                Text(
                    "Waiting for verification...",
                    color = Color.Cyan,
                    fontWeight = FontWeight.Light,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(32.dp))

            // Resend Verification Email Button
            TextButton(
                onClick = { viewModel.resendVerificationEmail() },
                enabled = !isLoading
            ) {
                Text(
                    "Resend Verification Link",
                    color = Color.Gray.copy(alpha = 0.8f)
                )
            }

            Spacer(Modifier.height(48.dp))

            // Sign Out Button
            TextButton(
                onClick = {
                    viewModel.signOut()
                    navController.navigate(ScreenRoutes.LoginScreen) {
                        popUpTo(ScreenRoutes.VerificationPendingScreen) { inclusive = true }
                    }
                }
            ) {
                Text(
                    "Sign Out",
                    color = Color.Red.copy(alpha = 0.8f)
                )
            }
        }
    }
}
