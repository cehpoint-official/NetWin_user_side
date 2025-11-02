package com.cehpoint.netwin.presentation.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cehpoint.netwin.domain.model.Tournament
import com.cehpoint.netwin.presentation.viewmodels.TournamentViewModel
import com.cehpoint.netwin.utils.formatDateTime
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VictoryPassScreen(
    tournamentId: String,
    onBackClick: () -> Unit,
    onNavigateToRules: () -> Unit = {},
    viewModel: TournamentViewModel = hiltViewModel()
) {
    val selectedTournament by viewModel.selectedTournament.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Local UI states
    var roomIdVisible by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }

    // ⭐️ NEW: State to determine if credentials should be revealed
    var isRevealTime by remember { mutableStateOf(false) }

    // Mock room credentials - replace with actual data from viewModel
    val roomId = selectedTournament?.roomId ?: "12345678"
    val roomPassword = selectedTournament?.roomPassword ?: "NETWIN2024"

    // Logic to calculate reveal status and update countdown every second
    LaunchedEffect(selectedTournament) {
        // Only run if the tournament is loaded
        selectedTournament?.let { tournament ->
            while (true) {
                val now = System.currentTimeMillis()
                // Reveal time is 15 minutes (900,000 milliseconds) before start time
                val revealTimeMillis = tournament.startTime - (15 * 60 * 1000)

                isRevealTime = now >= revealTimeMillis

                // If revealed, stop the loop and don't re-check
                if (isRevealTime) break

                delay(1000) // Check every second
            }
        }
    }


    // Load tournament if not already loaded
    LaunchedEffect(tournamentId) {
        if (selectedTournament?.id != tournamentId) {
            viewModel.getTournamentById(tournamentId)
        }
        // Show confetti animation on screen enter
        delay(300)
        showConfetti = true
        delay(1000)
        showConfetti = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Victory Pass",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Confetti Space (reserved for animation)
                if (showConfetti) {
                    ConfettiAnimation()
                }

                // Hero Card with Room Credentials
                CredentialsCard(
                    // ⭐️ CONDITIONAL VALUES: Show real credentials only if it's reveal time
                    roomId = if (isRevealTime) roomId else "Hidden",
                    roomPassword = if (isRevealTime) roomPassword else "Hidden",
                    roomIdVisible = roomIdVisible,
                    passwordVisible = passwordVisible,
                    isRevealTime = isRevealTime, // Pass the reveal status
                    onToggleRoomId = { roomIdVisible = !roomIdVisible },
                    onTogglePassword = { passwordVisible = !passwordVisible },
                    onCopyRoomId = {
                        if (isRevealTime) clipboardManager.setText(AnnotatedString(roomId))
                        else Toast.makeText(context, "Room ID is hidden until 15 min before match.", Toast.LENGTH_SHORT).show()
                    },
                    onCopyPassword = {
                        if (isRevealTime) clipboardManager.setText(AnnotatedString(roomPassword))
                        else Toast.makeText(context, "Password is hidden until 15 min before match.", Toast.LENGTH_SHORT).show()
                    },
                    onCopyAll = {
                        if (isRevealTime) {
                            val allDetails = "Room ID: $roomId\nPassword: $roomPassword"
                            clipboardManager.setText(AnnotatedString(allDetails))
                        } else {
                            Toast.makeText(context, "Credentials are not yet available.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // Tournament Info Card
                selectedTournament?.let { tournament ->
                    TournamentInfoCard(tournament = tournament)
                }

                // Countdown and Actions
                selectedTournament?.let { tournament ->
                    CountdownAndActions(
                        tournament = tournament,
                        onAddToCalendar = { /* TODO: Add calendar integration */ },
                        onSharePoster = { /* TODO: Share functionality */ }
                    )
                }

                // Quick Tips Card
                QuickTipsCard(onViewRules = onNavigateToRules)

                // Bottom CTA
                Button(
                    // ⭐️ UPDATED: Calls onBackClick which should navigate to TournamentScreen.kt
                    onClick = onBackClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6C3AFF),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Got It!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CredentialsCard(
    roomId: String,
    roomPassword: String,
    roomIdVisible: Boolean,
    passwordVisible: Boolean,
    isRevealTime: Boolean, // ⭐️ NEW PARAMETER
    onToggleRoomId: () -> Unit,
    onTogglePassword: () -> Unit,
    onCopyRoomId: () -> Unit,
    onCopyPassword: () -> Unit,
    onCopyAll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E2F)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with glow effect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF6C3AFF).copy(alpha = 0.3f),
                                Color(0xFF3AFFDC).copy(alpha = 0.3f)
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isRevealTime) "🎉 Room Credentials Ready!" else "⏳ Room Credentials Hidden",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Room ID Section
            CredentialRow(
                label = "Room ID",
                value = roomId,
                isVisible = roomIdVisible,
                isEnabled = isRevealTime, // Disable interaction if hidden
                onToggleVisibility = onToggleRoomId,
                onCopy = onCopyRoomId
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password Section
            CredentialRow(
                label = "Password",
                value = roomPassword,
                isVisible = passwordVisible,
                isEnabled = isRevealTime, // Disable interaction if hidden
                onToggleVisibility = onTogglePassword,
                onCopy = onCopyPassword
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!isRevealTime) {
                Text(
                    text = "ID and Password will be revealed 15 minutes before match start.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Yellow,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
            }


            // Copy All Button
            Button(
                onClick = onCopyAll,
                enabled = isRevealTime, // Disable button if not reveal time
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRevealTime) Color(0xFF3AFFDC) else Color(0xFF3AFFDC).copy(alpha = 0.4f),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Copy All Details",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CredentialRow(
    label: String,
    value: String,
    isVisible: Boolean,
    isEnabled: Boolean, // ⭐️ NEW PARAMETER
    onToggleVisibility: () -> Unit,
    onCopy: () -> Unit
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF3AFFDC),
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color(0xFF282840),
                    RoundedCornerShape(12.dp)
                )
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                // ⭐️ DISPLAY LOGIC: Show stars if hidden and not visible, or the hidden text if revealed but toggle is off
                text = when {
                    !isEnabled -> "••••••••"
                    isVisible -> value
                    else -> "••••••••"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (isEnabled) Color.White else Color.Gray,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            Row {
                // Toggle Visibility Button
                IconButton(
                    onClick = onToggleVisibility,
                    enabled = isEnabled, // Disable if credentials are not revealed
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isVisible) "Hide" else "Show",
                        tint = if (isEnabled) Color(0xFF3AFFDC) else Color.Gray
                    )
                }

                // Copy Button
                IconButton(
                    onClick = onCopy,
                    enabled = isEnabled, // Disable if credentials are not revealed
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = if (isEnabled) Color(0xFF3AFFDC) else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun TournamentInfoCard(tournament: Tournament) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Tournament Details",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF3AFFDC),
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            TournamentInfoRow("Tournament", tournament.name)
            TournamentInfoRow("Mode", tournament.matchType.ifEmpty { "SQUAD" })
            TournamentInfoRow("Map", tournament.map.ifEmpty { "Erangel" })
            TournamentInfoRow("Prize Pool", "₹${tournament.prizePool.toInt()}")
        }
    }
}

@Composable
private fun CountdownAndActions(
    tournament: Tournament,
    onAddToCalendar: () -> Unit,
    onSharePoster: () -> Unit
) {
    // ⭐️ Dynamic countdown text based on remaining time
    var timeUntilStart by remember { mutableStateOf("Calculating...") }

    LaunchedEffect(tournament.startTime) {
        while (true) {
            val now = System.currentTimeMillis()
            val timeLeft = tournament.startTime - now

            timeUntilStart = if (timeLeft > 0) {
                val hours = timeLeft / (1000 * 60 * 60)
                val minutes = (timeLeft % (1000 * 60 * 60)) / (1000 * 60)
                val seconds = (timeLeft % (1000 * 60)) / 1000
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                "LIVE NOW"
            }
            if (timeLeft <= 0) break
            delay(1000)
        }
    }


    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Countdown Chip
            Box(
                modifier = Modifier
                    .background(
                        Color(0xFF6C3AFF).copy(alpha = 0.2f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (timeUntilStart == "LIVE NOW") timeUntilStart else "Starts in $timeUntilStart",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF6C3AFF),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onAddToCalendar,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF3AFFDC)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF3AFFDC), Color(0xFF3AFFDC))
                        )
                    )
                ) {
                    Icon(
                        Icons.Default.Event,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Calendar")
                }

                OutlinedButton(
                    onClick = onSharePoster,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF3AFFDC)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF3AFFDC), Color(0xFF3AFFDC))
                        )
                    )
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
            }
        }
    }
}

@Composable
private fun QuickTipsCard(onViewRules: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "How to Join",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF3AFFDC),
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            val tips = listOf(
                "Open PUBG Mobile/BGMI",
                "Go to Custom Room",
                "Enter Room ID and Password",
                "Wait for match to start"
            )

            tips.forEachIndexed { index, tip ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                Color(0xFF6C3AFF),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = tip,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onViewRules,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF3AFFDC)
                )
            ) {
                Text("View Full Rules")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun TournamentInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ConfettiAnimation() {
    // Simple confetti placeholder - you can enhance this with actual animation
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "🎉 🎊 🎉 🎊 🎉",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
    }
}