package com.cehpoint.netwin.presentation.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator // Correct import
import androidx.compose.material3.ExperimentalMaterial3Api // Needed for OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults // Needed for colors
import androidx.compose.material3.Scaffold // Added Scaffold for SnackbarHost and structure
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
//import androidx.compose.ui.platform.LocalContext // Not used directly, can be removed if not needed elsewhere
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.cehpoint.netwin.R
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import com.cehpoint.netwin.presentation.viewmodels.ProfileViewModel
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.ui.text.input.PasswordVisualTransformation
// --- IMPORT YOUR SCREEN ROUTES ---
import com.cehpoint.netwin.presentation.navigation.ScreenRoutes
import com.cehpoint.netwin.presentation.viewmodels.AuthViewModel
import kotlinx.coroutines.delay // --- IMPORT DELAY ---

@OptIn(ExperimentalMaterial3Api::class) // Added OptIn
@Composable
fun ProfileSetupScreenUI(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
    // Removed onComplete
) {
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var profilePicUri by remember { mutableStateOf<Uri?>(null) } // State for selected image URI
    var displayNameError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val profileSetupState by viewModel.profileSetupState.collectAsState()

    // --- Image Picker Launcher ---
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        profilePicUri = uri // Update state with selected URI
    }

    // --- Email/password for phone-auth users ---
    val currentUser = FirebaseAuth.getInstance().currentUser
    // Check providerData safely, default to false if currentUser is null
    val isPhoneAuthOnly = currentUser?.providerData?.any { it.providerId == "phone" } == true && currentUser?.email.isNullOrBlank()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var linkLoading by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }
    var linkSuccess by remember { mutableStateOf(false) }

    // Username uniqueness check
    LaunchedEffect(username) {
        val trimmedUsername = username.trim()
        if (trimmedUsername.isNotBlank() && trimmedUsername.length >= 3) { // Check trimmed length
            viewModel.checkUsernameAvailability(trimmedUsername)
        }
        // No explicit clear needed here, UI conditional logic handles hiding feedback
    }


    // Show error snackbar
    LaunchedEffect(profileSetupState.error) {
        profileSetupState.error?.let { errorMsg ->
            coroutineScope.launch { snackbarHostState.showSnackbar(errorMsg) }
            // Consider adding a mechanism in your ViewModel to clear the error
            // after it has been observed/consumed, or use a single-event pattern.
        }
    }
    LaunchedEffect(linkError) {
        linkError?.let { errorMsg ->
            coroutineScope.launch { snackbarHostState.showSnackbar(errorMsg) }
            // Optionally clear linkError after showing
            linkError = null
        }
    }
    LaunchedEffect(linkSuccess) {
        if (linkSuccess) {
            coroutineScope.launch { snackbarHostState.showSnackbar("Email/password linked!") }
            // Optionally reset linkSuccess after showing
            // linkSuccess = false
        }
    }

    // --- *** AUTOMATIC NAVIGATION ON SUCCESS (3 Second Delay) *** ---
    LaunchedEffect(profileSetupState.saveSuccess) {
        if (profileSetupState.saveSuccess) {
            // Wait for 3 seconds
            delay(3000L) // <-- UPDATED DELAY TO 3 SECONDS
            // Navigate to TournamentsScreen and clear back stack
            navController.navigate(ScreenRoutes.TournamentsScreen) { // <-- Navigates to TournamentsScreen
                // Pop up to the start destination of the graph to remove setup screens
                popUpTo(navController.graph.startDestinationId) {
                    inclusive = true // Clears the start destination itself
                }
                // Avoid multiple copies of the destination screen
                launchSingleTop = true
            }
            // Optional: Implement resetSaveSuccessFlag() in your ViewModel if needed
            // viewModel.resetSaveSuccessFlag()
        }
    }

    // --- Themed TextField Colors ---
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color.Cyan,
        unfocusedBorderColor = Color.Gray,
        cursorColor = Color.Cyan,
        focusedLabelColor = Color.Cyan,
        unfocusedLabelColor = Color.White,
        errorBorderColor = Color.Red, // Added error color
        errorLabelColor = Color.Red   // Added error color
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // --- Background Image ---
        Image(
            painter = painterResource(id = R.drawable.login_screen), // Use your image
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // --- Dark Overlay ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xCC000000), Color(0xCC000000)) // 80% Black
                    )
                )
        )

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent, // Make Scaffold transparent
            topBar = { /* Optionally add a transparent TopAppBar if needed */ }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues) // Apply Scaffold padding
                    .verticalScroll(rememberScrollState()) // Make content scrollable
                    .padding(horizontal = 24.dp, vertical = 32.dp), // Overall padding
                horizontalAlignment = Alignment.CenterHorizontally,
                // Adjust vertical arrangement if needed, Center might push content down too much
                verticalArrangement = Arrangement.Top // Changed to Top for better scroll start
            ) {
                Spacer(modifier = Modifier.height(32.dp)) // Top spacing

                Text(
                    text = "Complete Your Profile",
                    color = Color.Cyan,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(32.dp)) // Increased spacing

                // --- Functional Profile Picture Picker ---
                Box(
                    modifier = Modifier
                        .size(120.dp) // Slightly larger
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)) // Thematic background
                        .clickable { imagePickerLauncher.launch("image/*") }, // Launch picker
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = if (profilePicUri != null) {
                            rememberAsyncImagePainter(profilePicUri)
                        } else {
                            painterResource(id = R.mipmap.ic_launcher_foreground) // Use original default
                        },
                        contentDescription = "Profile Picture",
                        contentScale = ContentScale.Crop,
                        modifier = if (profilePicUri != null) Modifier.fillMaxSize() else Modifier.size(80.dp) // Adjust default size
                    )
                }
                TextButton(onClick = { imagePickerLauncher.launch("image/*") }) { // Launch picker
                    Text("Choose Profile Picture", color = Color.Cyan)
                }
                Spacer(modifier = Modifier.height(32.dp)) // Increased spacing

                // --- Username ---
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        // Basic validation: Allow only letters, numbers, underscore
                        val filtered = it.filter { char -> char.isLetterOrDigit() || char == '_' }
                        if (filtered.length <= 20) { // Limit length
                            username = filtered
                        }
                    },
                    label = { Text("Username") },
                    placeholder = { Text("Letters, numbers, _ (min 3)", color = Color.Gray) },
                    isError = profileSetupState.isUsernameAvailable == false,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !profileSetupState.isSaving,
                    colors = textFieldColors // Thematic colors
                )
                // Username availability feedback with height to prevent layout jumps
                Box(modifier = Modifier.height(20.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    // Check trimmed username length for feedback
                    val trimmedUsername = username.trim()
                    if (trimmedUsername.length >= 3) {
                        when (profileSetupState.isUsernameAvailable) {
                            true -> Text("Username available!", color = Color.Green, fontSize = 12.sp)
                            false -> Text("Username taken", color = Color.Red, fontSize = 12.sp)
                            null -> if (trimmedUsername.isNotBlank()) Text("Checking...", color = Color.Gray, fontSize = 12.sp)
                        }
                    } else if (trimmedUsername.isNotEmpty()) { // Check trimmed username here too
                        Text("Minimum 3 characters", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp)) // Reduced space after feedback

                // --- Display Name ---
                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        if (it.length <= 30) { // Limit length
                            displayName = it
                            displayNameError = null
                        }
                    },
                    label = { Text("Display Name") },
                    placeholder = { Text("How you want to appear", color = Color.Gray) },
                    isError = displayNameError != null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !profileSetupState.isSaving,
                    colors = textFieldColors // Thematic colors
                )
                // Display name error feedback
                Box(modifier = Modifier.height(20.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (displayNameError != null) {
                        Text(displayNameError!!, color = Color.Red, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp)) // Consistent spacing

                // --- Optional Email/password for phone-auth users ---
                if (isPhoneAuthOnly) {
                    Text(
                        "Add Email & Password",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "(Optional, for account recovery)",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; linkError = null },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !linkLoading && !linkSuccess, // Also disable after success
                        colors = textFieldColors // Thematic colors
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; linkError = null },
                        label = { Text("Password (min 6 chars)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !linkLoading && !linkSuccess, // Also disable after success
                        visualTransformation = PasswordVisualTransformation(),
                        colors = textFieldColors // Thematic colors
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            linkLoading = true
                            linkError = null // Clear previous error explicitly
                            // Basic email/pass validation before sending
                            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                                linkError = "Invalid Email format"
                                linkLoading = false
                                return@Button
                            }
                            if (password.length < 6) {
                                linkError = "Password must be at least 6 characters"
                                linkLoading = false
                                return@Button
                            }
                            authViewModel.linkEmailPasswordToPhoneUser(email, password) { success, errorMsg ->
                                linkLoading = false
                                linkSuccess = success
                                linkError = errorMsg
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = !linkLoading && email.isNotBlank() && password.isNotBlank() && !linkSuccess, // Disable after success
                        // --- Thematic Button Style (Solid Cyan) ---
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Cyan,
                            contentColor = Color.Black,
                            disabledContainerColor = Color.Gray, // Indicate disabled state clearly
                            disabledContentColor = Color.DarkGray
                        )
                    ) {
                        when {
                            linkLoading -> {
                                CircularProgressIndicator(
                                    color = Color.Black, // Match content color
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            linkSuccess -> {
                                Text("Credentials Linked!", fontWeight = FontWeight.Bold)
                            }
                            else -> {
                                Text("Link Email & Password", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp)) // Increased spacing
                }

                // --- Complete Profile Button ---
                Button(
                    onClick = {
                        // Trim inputs before validation
                        val finalUsername = username.trim()
                        val finalDisplayName = displayName.trim()

                        // Refined Validation
                        if (finalUsername.length < 3) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Username must be at least 3 characters") }
                        } else if (profileSetupState.isUsernameAvailable == false) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Username is already taken") }
                        } else if (profileSetupState.isUsernameAvailable == null) {
                            // Re-trigger check if needed
                            viewModel.checkUsernameAvailability(finalUsername)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Checking username availability...") }
                        } else if (finalDisplayName.isBlank()) {
                            displayNameError = "Display name required"
                        } else {
                            displayNameError = null // Clear any previous error

                            // Pass URI string to saveProfile; ViewModel handles upload
                            viewModel.saveProfile(
                                username = finalUsername,
                                displayName = finalDisplayName,
                                country = "", // Country handled in KYC
                                profilePicUrl = profilePicUri?.toString() // Pass URI as String
                            )
                        }
                    },
                    // --- Thematic Button Style (Transparent/Bordered) ---
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.Cyan,
                        disabledContainerColor = Color.Transparent.copy(alpha = 0.5f), // Dim when disabled
                        disabledContentColor = Color.Gray
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp, brush = Brush.horizontalGradient(listOf(Color.Cyan, Color.Cyan))),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    // --- Improved Enabled Logic ---
                    enabled = !profileSetupState.isSaving &&
                            username.trim().length >= 3 &&
                            displayName.trim().isNotBlank() &&
                            profileSetupState.isUsernameAvailable == true // Explicitly check for true
                ) {
                    if (profileSetupState.isSaving) {
                        CircularProgressIndicator(
                            color = Color.Cyan, // Match content color
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text("Complete Profile", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp)) // Ensure spacing at the bottom
            }
        }
    }
}