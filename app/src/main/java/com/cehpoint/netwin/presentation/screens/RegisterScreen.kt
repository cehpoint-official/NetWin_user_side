package com.cehpoint.netwin.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
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
    // State for registration details
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var termsAccepted by remember { mutableStateOf(false) }

    // ⭐ NEW STATE: Phone number details
    // In a real app, this should probably be a more robust CountryData object
    var countryCode by remember { mutableStateOf("+91") } // Default to a country code
    var phoneNumber by remember { mutableStateOf("") }

    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val verificationEmailSent by viewModel.verificationEmailSent.collectAsState()
    // isAuthenticated and currentUser are kept but not strictly used in navigation here

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(verificationEmailSent) {
        if (verificationEmailSent) {
            // Navigate to a dedicated screen asking the user to check their email
            navController.navigate(ScreenRoutes.VerificationPendingScreen) {
                // Clear the registration screen from the back stack
                popUpTo(ScreenRoutes.RegisterScreen) { inclusive = true }
            }
        }
    }

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
                "Sign up with your email, password and phone number", // Updated text
                color = Color.Gray,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(32.dp))

            EmailPasswordRegistration(
                email = email,
                password = password,
                confirmPassword = confirmPassword,
                countryCode = countryCode, // ⭐ PASS NEW STATE
                phoneNumber = phoneNumber,   // ⭐ PASS NEW STATE
                termsAccepted = termsAccepted,
                isLoading = isLoading,
                onEmailChange = { email = it },
                onPasswordChange = { password = it },
                onConfirmPasswordChange = { confirmPassword = it },
                onCountryCodeChange = { countryCode = it }, // ⭐ HANDLE NEW STATE CHANGE
                onPhoneNumberChange = { phoneNumber = it }, // ⭐ HANDLE NEW STATE CHANGE
                onTermsAccepted = { termsAccepted = it },
                onSubmit = {
                    if (password == confirmPassword && termsAccepted && phoneNumber.isNotBlank()) {
                        // ⭐ MODIFIED CALL: Pass countryCode and phoneNumber
                        viewModel.signUp(email, password, countryCode, phoneNumber)
                    } else if (password != confirmPassword) {
                        Toast.makeText(context, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                    } else if (phoneNumber.isBlank()) {
                        Toast.makeText(context, "Please enter your phone number.", Toast.LENGTH_SHORT).show()
                    } else if (!termsAccepted) {
                        Toast.makeText(context, "Please accept the Terms & Privacy Policy.", Toast.LENGTH_SHORT).show()
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

@Composable
fun EmailPasswordRegistration(
    email: String,
    password: String,
    confirmPassword: String,
    countryCode: String, // ⭐ NEW PARAMETER
    phoneNumber: String, // ⭐ NEW PARAMETER
    termsAccepted: Boolean,
    isLoading: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onCountryCodeChange: (String) -> Unit, // ⭐ NEW PARAMETER
    onPhoneNumberChange: (String) -> Unit, // ⭐ NEW PARAMETER
    onTermsAccepted: (Boolean) -> Unit,
    onSubmit: () -> Unit
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

        // ⭐ EMAIL FIELD
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !isLoading
        )

        Spacer(Modifier.height(16.dp))

        // ⭐ PHONE NUMBER INPUT (Country Code + Phone Number)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ⭐ SIMPLIFIED COUNTRY CODE FIELD (Ideally, this would be a Country Code Picker Library)
            OutlinedTextField(
                value = countryCode,
                onValueChange = onCountryCodeChange,
                label = { Text("Code", color = Color.White) },
                modifier = Modifier.width(100.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.Cyan,
                    unfocusedBorderColor = Color.Gray
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isLoading,
                maxLines = 1
            )

            Spacer(Modifier.width(8.dp))

            // ⭐ PHONE NUMBER FIELD
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = onPhoneNumberChange,
                label = { Text("Phone Number", color = Color.White) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.Cyan,
                    unfocusedBorderColor = Color.Gray
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                enabled = !isLoading,
                maxLines = 1
            )
        }


        Spacer(Modifier.height(16.dp))

        // ⭐ PASSWORD FIELD
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

        // ⭐ CONFIRM PASSWORD FIELD
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

        // ⭐ TERMS CHECKBOX
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

        // ⭐ SIGN UP BUTTON
        Button(
            onClick = onSubmit,
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank() &&
                    password == confirmPassword && termsAccepted && phoneNumber.isNotBlank(), // ⭐ ADD PHONE CHECK
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.Cyan
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
            modifier = Modifier.fillMaxWidth()
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
