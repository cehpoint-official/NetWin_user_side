package com.cehpoint.netwin.presentation.screens

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.cehpoint.netwin.BuildConfig
import com.cehpoint.netwin.R
import com.cehpoint.netwin.domain.model.Tournament
import com.cehpoint.netwin.domain.model.TournamentMode
import com.cehpoint.netwin.domain.model.TournamentStatus
import com.cehpoint.netwin.presentation.components.PrizeInfo
import com.cehpoint.netwin.presentation.components.PullRefreshComponent
import com.cehpoint.netwin.presentation.components.TournamentCapacityIndicator
import com.cehpoint.netwin.presentation.components.statusBarPadding
import com.cehpoint.netwin.presentation.navigation.Screen
import com.cehpoint.netwin.presentation.navigation.ScreenRoutes
import com.cehpoint.netwin.presentation.theme.NetwinTokens
import com.cehpoint.netwin.presentation.util.FeatureFlags
import com.cehpoint.netwin.presentation.viewmodels.TournamentEvent
import com.cehpoint.netwin.presentation.viewmodels.TournamentState
import com.cehpoint.netwin.presentation.viewmodels.TournamentViewModel
import com.cehpoint.netwin.ui.theme.NetWinCyan
import com.cehpoint.netwin.ui.theme.NetWinPink
import com.cehpoint.netwin.ui.theme.NetWinPurple
import com.cehpoint.netwin.utils.NGNTransactionUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Debug/preview toggle: when true, use brand token accents instead of hard-coded accent colors
private const val USE_ALT_ACCENTS: Boolean = false

// =====================================================================
// Main Screen Composable
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun LegacyTournamentsScreenUI(navController: NavController, viewModel: TournamentViewModel = hiltViewModel()) {
    Log.d("TournamentsScreen", "=== TournamentsScreenUI COMPOSABLE STARTED ===")

    val state by viewModel.state.collectAsState()
    val registrationState by viewModel.registrationState.collectAsState()
    val walletBalance by viewModel.walletBalance.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val refreshError by viewModel.refreshError.collectAsState()
    val refreshSuccess by viewModel.refreshSuccess.collectAsState()
    val context = LocalContext.current

    // ⭐ FIX 1: Get CoroutineScope for launching non-Compose related async tasks
    val coroutineScope = rememberCoroutineScope()

    // State for the result submission dialog
    var showSubmissionDialog by remember { mutableStateOf(false) }
    var selectedTournamentForSubmission by remember { mutableStateOf<Tournament?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    // State to track if result has been submitted for a specific tournament (for UI rendering)
    var submittedTournamentIds by remember { mutableStateOf(setOf<String>()) }

    // Get user country and currency
    var userCountry by remember { mutableStateOf("India") }
    var userCurrency by remember { mutableStateOf("INR") }

    LaunchedEffect(Unit) {
        // Get current user to determine country
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let { user ->
            try {
                // Get user country from Firestore or use default
                val userDoc = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.uid)
                    .get()
                    .await() // .await() is safe inside LaunchedEffect

                userCountry = userDoc.getString("country") ?: "India"
                userCurrency = if (userCountry.equals("Nigeria", ignoreCase = true) || userCountry.equals("NG", ignoreCase = true)) "NGN" else "INR"
            } catch (e: Exception) {
                Log.e("TournamentsScreen", "Error getting user country: ${e.message}")
                userCountry = "India"
                userCurrency = "INR"
            }
        }
    }

    // ⭐ NEW: Function to load existing tournament results
    fun loadSubmittedTournamentIds(userId: String) {
        coroutineScope.launch { // Use coroutineScope here
            try {
                val resultsSnapshot = FirebaseFirestore.getInstance()
                    .collection("tournament_results")
                    .whereEqualTo("userId", userId)
                    .whereIn("status", listOf("Pending Verification", "Verification Failed", "Prize Distributed", "Verified - No Prize"))
                    .get()
                    .await() // Suspend call is safe inside launch

                submittedTournamentIds = resultsSnapshot.documents.mapNotNull { it.getString("tournamentId") }.toSet()
                Log.d("TournamentsScreen", "Loaded ${submittedTournamentIds.size} existing results for user.")

            } catch (e: Exception) {
                Log.e("TournamentsScreen", "Error loading existing results: ${e.message}")
            }
        }
    }

    // Initial load of submitted IDs
    LaunchedEffect(Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            loadSubmittedTournamentIds(userId)
        }
    }

    val tournamentState = state as? TournamentState ?: TournamentState()
    val isLoading = tournamentState.isLoading
    val error = tournamentState.error
    val tournaments = tournamentState.tournaments

    Log.d("TournamentsScreen", "Tournament state updated:")
    Log.d("TournamentsScreen", "Loading: $isLoading")
    Log.d("TournamentsScreen", "Error: $error")
    Log.d("TournamentsScreen", "Number of tournaments: ${tournaments.size}")
    tournaments.forEach { tournament ->
        Log.d("TournamentsScreen", "Tournament in UI: ${tournament.name}, ID: ${tournament.id}, Status: ${tournament.computedStatus}, Mode: ${tournament.mode}, Map: ${tournament.map}")
    }

    LaunchedEffect(registrationState) {
        registrationState?.let { result ->
            val message = result.fold(
                onSuccess = { "Successfully registered for tournament!" },
                onFailure = { it.message ?: "Registration failed." }
            )
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearRegistrationState()
        }
    }

    LaunchedEffect(Unit) {
        Log.d("TournamentsScreen", "=== LaunchedEffect TRIGGERED ===")
        Log.d("TournamentsScreen", "LaunchedEffect - Loading tournaments")
        viewModel.handleEvent(TournamentEvent.LoadTournaments)
        Log.d("TournamentsScreen", "LaunchedEffect - Tournament load event sent")
    }

    // Activity Result Launcher for picking an image
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && selectedTournamentForSubmission != null) {
            selectedImageUri = uri
            showSubmissionDialog = true // Show the submission dialog after picking the image
        } else {
            selectedTournamentForSubmission = null
            Toast.makeText(context, "No screenshot selected or tournament not set.", Toast.LENGTH_SHORT).show()
        }
    }


    Scaffold(
        topBar = {
            TournamentsTopBar(
                walletBalance = walletBalance.toInt(),
                currency = userCurrency,
                isRefreshing = false, // Don't show refresh indicator in top bar
                onToggleUi = if (BuildConfig.DEBUG) {
                    {
                        FeatureFlags.runtimeTournamentUiV2 = !FeatureFlags.runtimeTournamentUiV2
                    }
                } else null,
                navController = navController
            )
        },
        // Uncomment and implement if you have a bottom nav bar
        // bottomBar = { BottomNavBar(navController) }
    ) { paddingValues ->

        PullRefreshComponent(
            isRefreshing = isRefreshing,
            refreshError = refreshError,
            refreshSuccess = refreshSuccess,
            onRefresh = {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@PullRefreshComponent
                viewModel.handleEvent(TournamentEvent.RefreshTournaments(force = true))

                // ⭐ FIX 2: Use coroutineScope to launch suspend function
                coroutineScope.launch {
                    try {
                        val resultsSnapshot = FirebaseFirestore.getInstance()
                            .collection("tournament_results")
                            .whereEqualTo("userId", userId)
                            .whereIn("status", listOf("Pending Verification", "Verification Failed", "Prize Distributed", "Verified - No Prize"))
                            .get()
                            .await() // Suspend call is safe inside launch
                        submittedTournamentIds = resultsSnapshot.documents.mapNotNull { it.getString("tournamentId") }.toSet()
                    } catch (e: Exception) {
                        Log.e("TournamentsScreen", "Error refreshing existing results: ${e.message}")
                    }
                }
            },
            onClearRefreshSuccess = {
                viewModel.clearRefreshSuccess()
            },
            onClearRefreshError = {
                viewModel.clearRefreshError()
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NetwinTokens.Background)
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                    bottom = 48.dp,
                    top = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { WelcomeCard(userName = userName) }
                item {
                    Text(
                        text = "Available Tournaments",
                        color = Color.White,
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // ** FILTER ITEM REMOVED AS REQUESTED **

                if (isLoading && !isRefreshing) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = NetwinTokens.Accent)
                        }
                    }
                } else if (error != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = error,
                                color = Color.Red,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    // Collect the registration status from the ViewModel state
                    val userRegistrations = tournamentState.userRegistrations

                    items(tournaments) { tournament ->
                        val isUserRegistered = userRegistrations.containsKey(tournament.id)
                        // ⭐ NEW CHECK: Has the user submitted a result for this tournament ID?
                        val isResultSubmitted = submittedTournamentIds.contains(tournament.id)

                        TournamentCard(
                            tournament = tournament,
                            viewModel = viewModel,
                            onCardClick = {
                                Log.d("TournamentsScreen", "Navigating to tournament details: ${tournament.id}")
                                navController.navigate(Screen.TournamentDetails.createRoute(tournament.id))
                            },
                            navController = navController,
                            currency = userCurrency,
                            isUserRegistered = isUserRegistered,
                            isResultSubmitted = isResultSubmitted, // <<< PASS NEW STATE
                            onScanAndEarnClick = {
                                if (isResultSubmitted) {
                                    Toast.makeText(context, "You have already submitted a result for this tournament.", Toast.LENGTH_SHORT).show()
                                } else {
                                    // 1. Set the tournament to be submitted for
                                    selectedTournamentForSubmission = tournament
                                    // 2. Launch the image picker
                                    imagePickerLauncher.launch("image/*")
                                }
                            }
                        )
                    }
                }
            }
        }

        // Show the dialog after the image is picked
        if (showSubmissionDialog && selectedTournamentForSubmission != null && selectedImageUri != null) {
            ResultSubmissionCustomDialog( // CHANGED TO CUSTOM DIALOG
                tournament = selectedTournamentForSubmission!!,
                screenshotUri = selectedImageUri!!,
                viewModel = viewModel,
                onDismiss = {
                    showSubmissionDialog = false
                    selectedTournamentForSubmission = null
                    selectedImageUri = null
                }
            )
        }
    }
}

// =====================================================================
// NEW: Result Submission Dialog (UPDATED for better design)
// =====================================================================

@Composable
fun ResultSubmissionCustomDialog(
    tournament: Tournament,
    screenshotUri: Uri,
    viewModel: TournamentViewModel,
    onDismiss: () -> Unit
) {
    // *** NEW: Get the current context for use in the ViewModel ***
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }

    // Use a standard Dialog to host the custom content
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = NetwinTokens.SurfaceAlt),
            border = BorderStroke(
                2.dp,
                Brush.horizontalGradient(listOf(NetWinCyan, NetWinPurple)) // Vibrant border
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Custom Title
                Text(
                    text = "Submit Proof for ${tournament.name}", // Dynamic name
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Tournament ID
                Text(
                    text = tournament.id,
                    color = Color.Gray,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Instruction Text
                Text(
                    text = "Please upload the screenshot of the **final result screen** for verification. This image will be analyzed by AI.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Screenshot Preview
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp) // Slightly taller preview
                        .clip(RoundedCornerShape(12.dp)),
                    border = BorderStroke(3.dp, NetWinCyan.copy(alpha = 0.8f)) // Thicker, vibrant border
                ) {
                    AsyncImage(
                        model = screenshotUri,
                        contentDescription = "Selected Screenshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Submit Button (Using the Gradient Primary Button style)
                    GradientPrimaryButton(
                        text = if (isLoading) "Submitting..." else "Submit Proof",
                        enabled = !isLoading,
                        onClick = {
                            isLoading = true
                            // *** CRITICAL UPDATE: Pass the context for image analysis ***
                            viewModel.submitTournamentResult(
                                context = context, // Pass the context here
                                tournamentId = tournament.id,
                                screenshotUri = screenshotUri,
                                onSuccess = {
                                    Toast.makeText(context, "Proof submitted successfully! Pending AI review.", Toast.LENGTH_LONG).show()
                                    onDismiss()
                                },
                                onFailure = { errorMessage ->
                                    // Removed 'kills = 0' as it's no longer required by the new ViewModel signature
                                    Toast.makeText(context, "Submission failed: $errorMessage", Toast.LENGTH_LONG).show()
                                    isLoading = false
                                }
                            )
                        }
                    )

                    // Cancel Button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(NetwinTokens.RadiusSm),
                        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
                    ) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            }
        }
    }
}

// =====================================================================
// Core Screen Components (MODIFIED)
// =====================================================================

@Composable
fun TournamentsTopBar(
    walletBalance: Int,
    currency: String,
    isRefreshing: Boolean = false,
    onToggleUi: (() -> Unit)? = null,
    navController: NavController
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF121212)) // Explicit dark background
            .statusBarPadding()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "NETWIN",
                style = MaterialTheme.typography.titleLarge,
                color = NetwinTokens.TextPrimary,
                fontWeight = FontWeight.Bold
            )

            // Show refresh indicator next to title when refreshing
            if (isRefreshing) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    color = NetwinTokens.Primary,
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        Surface(
            color = NetwinTokens.SurfaceAlt,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(NetWinPurple, NetWinPink, NetWinCyan))),
            onClick = { navController.navigate(ScreenRoutes.WalletScreen) }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )

                Text(
                    // Use the new tidy formatter here as well for consistency
                    NGNTransactionUtils.formatAmountTidy(walletBalance.toDouble(), currency),
                    modifier = Modifier.padding(start = 6.dp),
                    color = NetwinTokens.TextPrimary,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                )
            }
        }
    }
}

@Composable
fun WelcomeCard(userName: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        // Background image for welcome card
        Image(
            painter = painterResource(id = R.drawable.esport),
            contentDescription = "Welcome background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay for better text contrast
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = "Trophy",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Welcome Back",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.headlineMedium.fontSize
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ready to compete? Join a tournament and showcase your skills!",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = MaterialTheme.typography.bodyMedium.fontSize
            )
        }
    }
}

/**
 * The final, pixel-perfect implementation of the Tournament Card.
 *
 * **UPDATED:** Added `onScanAndEarnClick` and `isResultSubmitted` parameter.
 */
@Composable
fun TournamentCard(
    tournament: Tournament,
    onCardClick: () -> Unit,
    viewModel: TournamentViewModel,
    currency: String,
    navController: NavController,
    isUserRegistered: Boolean,
    isResultSubmitted: Boolean, // <<< NEW STATE ADDED
    onScanAndEarnClick: (Tournament) -> Unit
) {
    // Define the exact colors from the screenshot for clarity
    val cardBackgroundColor = Color(0xFF2C2B3E)
    val prizeColor = Color(0xFFFFD600) // Yellowish gold
    val killColor = Color(0xFF00E5FF)   // Cyan/Blue
    val feeColor = Color(0xFFFF4D6D)     // Reddish pink
    val gradientColors = listOf(
        Color(0xFF903AE3), // Left - Purple
        Color(0xFFA970B4), // Center - Purple-Pink blend
        Color(0xFFDD3BA8)  // Right - Pink
    )
    val detailsButtonOutlineColor = Color(0xFF625F83)
    val capacityProgressColor = Color(0xFF00E676) // Bright Green

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // No ripple effect
                onClick = onCardClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Banner Image with Status Badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                AsyncImage(
                    model = tournament.bannerImage,
                    contentDescription = "Tournament Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                )
                // Status badge positioned at the top start
                TournamentStatusBadge(
                    status = tournament.computedStatus,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                )
            }

            // 2. Content Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title and Sub-details (Mode, Map)
                Column {
                    Text(
                        text = tournament.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ModeChip(mode = tournament.mode)
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Map",
                            tint = NetwinTokens.Primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = tournament.map,
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                    }
                }

                // Prize, Kill, and Entry Fee Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PrizeInfo(
                        label = "Prize Pool",
                        value = NGNTransactionUtils.formatAmountTidy(tournament.prizePool, currency),
                        icon = Icons.Default.EmojiEvents,
                        color = prizeColor,
                        showLabel = true
                    )
                    PrizeInfo(
                        label = "Per Kill",
                        value = NGNTransactionUtils.formatAmountTidy(tournament.killReward ?: 0.0, currency),
                        icon = Icons.Default.EmojiEvents,
                        color = killColor,
                        showLabel = true
                    )
                    PrizeInfo(
                        label = "Entry Fee",
                        value = if (tournament.entryFee > 0) NGNTransactionUtils.formatAmountTidy(tournament.entryFee, currency) else "Free",
                        icon = Icons.Default.AccountBalanceWallet,
                        color = if (tournament.entryFee > 0) feeColor else Color(0xFF4CAF50),
                        showLabel = true
                    )
                }



                // UPDATED: Countdown Section is now a separate, decorated composable on the left
                if (tournament.computedStatus == TournamentStatus.UPCOMING || tournament.computedStatus == TournamentStatus.STARTS_SOON) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        CountdownBadge(targetTimeMillis = tournament.startTime)
                    }
                }


                // Capacity Indicator
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Capacity:", color = Color.LightGray, fontSize = 14.sp)
                        Text(
                            "${tournament.registeredTeams}/${tournament.maxTeams} Players",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (tournament.registeredTeams.toFloat() / tournament.maxTeams.toFloat()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = capacityProgressColor,
                        trackColor = detailsButtonOutlineColor
                    )
                }

                // ** ACTION BUTTONS - MODIFIED LOGIC **
                val showScanButton = tournament.computedStatus == TournamentStatus.ONGOING && isUserRegistered

                if (showScanButton || isResultSubmitted) {
                    val buttonText = if (isResultSubmitted) "Result Submitted" else "Scan and Earn"
                    val buttonEnabled = !isResultSubmitted
                    val buttonColor = if (isResultSubmitted) Color(0xFF616161) else Color(0xFF4CAF50)

                    Button(
                        onClick = {
                            if (buttonEnabled) {
                                // Launch the image picker/submission flow
                                onScanAndEarnClick(tournament)
                            }
                            // If disabled, the Toast is handled in onScanAndEarnClick lambda in LegacyTournamentsScreenUI
                        },
                        enabled = buttonEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    // Use gray gradient when submitted, otherwise green
                                    brush = Brush.horizontalGradient(
                                        if (isResultSubmitted) listOf(Color(0xFF616161), Color(0xFF424242))
                                        else listOf(Color(0xFF4CAF50), Color(0xFF8BC34A))
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isResultSubmitted) Icons.Default.CheckCircle else Icons.Default.QrCodeScanner,
                                    contentDescription = buttonText,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    buttonText,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                } else {
                    // Scenario 2: Default - Show Details and Register
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                /* Handle Details Click */
                                navController.navigate(Screen.TournamentDetails.createRoute(tournament.id))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, detailsButtonOutlineColor)
                        ) {
                            Text("Details", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                /* Handle Register Click */
                                navController.navigate(
                                    ScreenRoutes.TournamentRegistration(
                                        tournamentId = tournament.id,
                                        stepIndex = 1
                                    )
                                )

                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(), // Remove default padding
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.horizontalGradient(gradientColors),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Register", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}


// =====================================================================
// Helper & Sub-Components (UNCHANGED)
// =====================================================================

/**
 * NEW: A decorated, standalone badge that displays a countdown timer.
 */
@Composable
fun CountdownBadge(targetTimeMillis: Long, modifier: Modifier = Modifier) {
    var remainingTime by remember { mutableStateOf("") }

    LaunchedEffect(targetTimeMillis) {
        while (true) {
            val now = System.currentTimeMillis()
            val diff = targetTimeMillis - now

            if (diff <= 0) {
                remainingTime = "00:00:00"
                break
            }

            val hours = diff / (1000 * 60 * 60)
            val minutes = (diff % (1000 * 60 * 60)) / (1000 * 60)
            val seconds = (diff % (1000 * 60)) / 1000

            remainingTime = String.format("%02d:%02d:%02d", hours, minutes, seconds)
            delay(1000)
        }
    }

    if (remainingTime.isNotEmpty() && remainingTime != "00:00:00") {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF6A11CB).copy(alpha = 0.1f)
            ),
            border = BorderStroke(1.dp, Color(0xFF6A11CB))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Starts In",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Countdown",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = remainingTime,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}


/**
 * A composable for displaying a piece of info with a label and a colored value.
 * Renamed from InfoColumn to InfoDetail to avoid conflicts.
 */
@Composable
private fun InfoDetail(label: String, value: String, valueColor: Color) {
    Column {
        Text(text = label, color = Color.LightGray, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


/**
 * A badge with improved visibility that displays the tournament status.
 */
@Composable
fun TournamentStatusBadge(status: TournamentStatus, modifier: Modifier = Modifier) {
    val (text, color) = when (status) {
        TournamentStatus.COMPLETED -> "Completed" to Color(0xFF616161) // Dark Grey
        TournamentStatus.UPCOMING -> "Upcoming" to Color(0xFFD84315)   // Deep Orange
        TournamentStatus.STARTS_SOON -> "Starts Soon" to Color(0xFF0277BD) // Light Blue
        TournamentStatus.ONGOING -> "Live" to Color(0xFF2E7D32)         // Dark Green
        TournamentStatus.ROOM_OPEN -> "Room Open" to Color(0xFFC62828) // Red
    }

    Surface(
        color = color, // Use a solid, vibrant color for the background
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Status",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text.uppercase(),
                color = Color.White, // White text provides good contrast
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * A chip to display the tournament mode (e.g., Solo, Duo, Squad).
 */
@Composable
fun ModeChip(mode: TournamentMode, modifier: Modifier = Modifier) {
    val text = when (mode) {
        TournamentMode.SOLO -> "Solo"
        TournamentMode.DUO -> "Duo"
        TournamentMode.SQUAD -> "Squad"
        TournamentMode.TRIO -> "Trio"
        TournamentMode.CUSTOM -> "Custom"
    }
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF625F83)),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = Color.Gray,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}


@Composable
fun GradientPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, // Use transparent so the gradient box can show through
            disabledContainerColor = NetwinTokens.Primary.copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(), // Remove default padding
        shape = RoundedCornerShape(NetwinTokens.RadiusSm)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(0xFF903AE3), // Left - Purple
                            Color(0xFFA970B4), // Center - Purple-Pink blend
                            Color(0xFFDD3BA8)  // Right - Pink
                        )
                    ),
                    shape = RoundedCornerShape(NetwinTokens.RadiusSm)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (text.endsWith("...")) { // Check for loading text
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Text(
                    text = text,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

// Shimmer utilities and placeholder (UNCHANGED)
@Composable
private fun ShimmerTournamentCardPlaceholder() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "x"
    )
    val shimmerColors = listOf(
        NetwinTokens.Surface.copy(alpha = 0.9f),
        NetwinTokens.SurfaceAlt.copy(alpha = 0.6f),
        NetwinTokens.Surface.copy(alpha = 0.9f)
    )
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(shimmerX, 0f),
        end = Offset(shimmerX + 200f, 0f)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NetwinTokens.Sp16),
        shape = RoundedCornerShape(NetwinTokens.RadiusLg),
        colors = CardDefaults.cardColors(containerColor = NetwinTokens.Surface)
    ) {
        Column(modifier = Modifier.padding(NetwinTokens.Sp14)) {
            // Title line
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush)
            )
            Spacer(Modifier.height(NetwinTokens.Sp8))
            // Chips row
            Row(horizontalArrangement = Arrangement.spacedBy(NetwinTokens.Sp4)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .width(70.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(brush)
                    )
                }
            }
            Spacer(Modifier.height(NetwinTokens.Sp8))
            // Capacity bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush)
            )
            Spacer(Modifier.height(NetwinTokens.Sp8))
            // Buttons row
            Row(horizontalArrangement = Arrangement.spacedBy(NetwinTokens.Sp4)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(NetwinTokens.RadiusSm))
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(NetwinTokens.RadiusSm))
                        .background(brush)
                )
            }
        }
    }
}

// =====================================================================
// Alternative/Unused Components (V2 Card) - UNCHANGED
// =====================================================================

@Composable
fun TournamentCardV2(
    t: Tournament,
    onDetailsClick: () -> Unit,
    currency: String, // Added currency parameter
    onRegisterClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NetwinTokens.Sp8)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        NetwinTokens.Primary.copy(alpha = 0.12f),
                        NetwinTokens.Accent.copy(alpha = 0.12f)
                    )
                ),
                shape = RoundedCornerShape(NetwinTokens.RadiusLg)
            )
            .clip(RoundedCornerShape(NetwinTokens.RadiusLg)),
        colors = CardDefaults.cardColors(containerColor = NetwinTokens.SurfaceAlt)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = NetwinTokens.Sp4)
        ) {
            // Banner with overlay and countdown (if available)
            if (!t.bannerImage.isNullOrBlank()) {
                // TODO: Implement banner image handling
            }

            Spacer(Modifier.height(NetwinTokens.Sp8))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = NetwinTokens.Sp4),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                // UPDATED to use the centralized, tidy formatting function
                PrizeInfo(
                    label = "Prize Pool",
                    value = NGNTransactionUtils.formatAmountTidy(t.prizePool, currency),
                    icon = Icons.Default.EmojiEvents,
                    color = Color(0xFFFFC107)
                )
                PrizeInfo(
                    label = "Entry Fee",
                    value = if (t.entryFee > 0) NGNTransactionUtils.formatAmountTidy(t.entryFee, currency) else "Free",
                    icon = Icons.Default.AccountBalanceWallet,
                    color = if (t.entryFee > 0) Color(0xFFFF7043) else Color(0xFF4CAF50)
                )
                PrizeInfo(
                    label = "Per Kill",
                    value = NGNTransactionUtils.formatAmountTidy(t.killReward ?: 0.0, currency),
                    icon = Icons.Default.EmojiEvents,
                    color = Color(0xFF26C6DA)
                )
            }
            // Countdown between currency row and capacity
            if (t.computedStatus == TournamentStatus.UPCOMING || t.computedStatus == TournamentStatus.STARTS_SOON) {
                Spacer(Modifier.height(NetwinTokens.Sp4))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    /* Box(modifier = Modifier.scale(0.8f)) { // subtle size reduction for visual balance
                        DetailsCountdownBadge(targetTimeMillis = t.startTime)
                    } */
                }
            }
            // Subtle divider for hierarchy
            Divider(
                color = NetwinTokens.Primary.copy(alpha = 0.08f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = NetwinTokens.Sp4)
            )
            TournamentCapacityIndicator(
                registeredTeams = t.registeredTeams,
                maxTeams = t.maxTeams
            )
            Spacer(Modifier.height(NetwinTokens.Sp8))
            Row(horizontalArrangement = Arrangement.spacedBy(NetwinTokens.Sp4)) {
                OutlinedButton(
                    onClick = onDetailsClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(NetwinTokens.RadiusSm),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NetwinTokens.Primary),
                    border = BorderStroke(1.dp, NetwinTokens.Primary)
                ) { Text("Details", fontWeight = FontWeight.SemiBold, color = NetwinTokens.Primary) }

                GradientPrimaryButton(
                    text = "Register",
                    modifier = Modifier.weight(1f),
                    onClick = onRegisterClick
                )
            }
        }
    }
}


// =====================================================================
// Compose Previews - UNCHANGED
// =====================================================================

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun Preview_TournamentsTopBar() {
//    TournamentsTopBar(walletBalance = 1250, currency = "INR", isRefreshing = false, onToggleUi = {}, navController = )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun Preview_InfoChip() {
    Row(
        modifier = Modifier
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        // ** FIX: Use the renamed InfoDetail composable **
        InfoDetail(
            label = "Prize Pool", value = "₹5,000",
            valueColor = Color(0xFFFFD600)
        )
        Spacer(Modifier.width(12.dp))
        InfoDetail(
            label = "Per Kill", value = "₹10",
            valueColor = Color(0xFF00E5FF)
        )
        Spacer(Modifier.width(12.dp))
        InfoDetail(
            label = "Entry Fee", value = "Free",
            valueColor = Color(0xFF4CAF50)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun Preview_TournamentStatusBadge() {
    Row(
        modifier = Modifier
            .background(Color(0xFF121212))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TournamentStatusBadge(TournamentStatus.UPCOMING)
        TournamentStatusBadge(TournamentStatus.STARTS_SOON)
        TournamentStatusBadge(TournamentStatus.ONGOING)
        TournamentStatusBadge(TournamentStatus.ROOM_OPEN)
        TournamentStatusBadge(TournamentStatus.COMPLETED)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun Preview_ModeChip() {
    Row(
        modifier = Modifier
            .background(Color(0xFF121212))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModeChip(TournamentMode.SOLO)
        ModeChip(TournamentMode.DUO)
        ModeChip(TournamentMode.SQUAD)
        ModeChip(TournamentMode.TRIO)
        ModeChip(TournamentMode.CUSTOM)
    }
}