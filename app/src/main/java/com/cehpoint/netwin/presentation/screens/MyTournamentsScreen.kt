package com.cehpoint.netwin.presentation.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cehpoint.netwin.R
import com.cehpoint.netwin.domain.model.Tournament
import com.cehpoint.netwin.domain.model.TournamentStatus
import com.cehpoint.netwin.presentation.components.TournamentStatusBadge
import com.cehpoint.netwin.presentation.viewmodels.TournamentViewModel
import com.cehpoint.netwin.utils.formatDateTime // Assuming this is available

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTournamentsScreen(
    onNavigateToTournaments: () -> Unit,
    onNavigateToVictoryPass: (String) -> Unit,
    onNavigateToTournamentDetails: (String) -> Unit,
    onBackClick: () -> Unit,
    viewModel: TournamentViewModel = hiltViewModel()
) {
    val uiState by viewModel.myTournamentsUiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    // Fetch tournaments when screen is first displayed
    LaunchedEffect(Unit) {
        viewModel.getRegisteredTournaments()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "My Tournaments",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading && uiState.tournaments.isEmpty() -> {
                    LoadingTournamentsList()
                }
                uiState.error != null && uiState.tournaments.isEmpty() -> {
                    ErrorState(
                        message = uiState.error ?: "Unknown error",
                        onRetry = { viewModel.getRegisteredTournaments() }
                    )
                }
                uiState.tournaments.isEmpty() -> {
                    EmptyTournamentsState(onDiscoverClick = onNavigateToTournaments)
                }
                else -> {
                    TournamentsList(
                        tournaments = uiState.tournaments,
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refreshTournaments() },
                        onTournamentClick = { tournament ->
                            when (tournament.computedStatus) {
                                TournamentStatus.ROOM_OPEN,
                                TournamentStatus.ONGOING -> onNavigateToVictoryPass(tournament.id) // Navigate to VictoryPass for live/room open
                                else -> onNavigateToTournamentDetails(tournament.id) // Navigate to details for upcoming/completed
                            }
                        }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Dual-Section List
// ---------------------------------------------------------------------

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun TournamentsList(
    tournaments: List<Tournament>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onTournamentClick: (Tournament) -> Unit
) {
    val refreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = onRefresh
    )

    // 1. Separate the lists (already existing logic, confirmed correct for requirements)
    val (liveAndUpcoming, completed) = remember(tournaments) {
        tournaments.partition {
            it.computedStatus != TournamentStatus.COMPLETED
        }.run {
            // Sort live/upcoming to put immediate action items first
            val sortedLive = first.sortedWith(compareBy<Tournament> {
                when (it.computedStatus) {
                    TournamentStatus.ONGOING -> 0
                    TournamentStatus.ROOM_OPEN -> 1
                    TournamentStatus.STARTS_SOON -> 2
                    else -> 3
                }
            }.thenBy { it.startTime })
            sortedLive to second.sortedByDescending { it.startTime }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(refreshState)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Live & Upcoming Section ---
            if (liveAndUpcoming.isNotEmpty()) {
                item { SectionHeader("Live & Upcoming") }
                items(liveAndUpcoming) { tournament ->
                    RegisteredTournamentCard(
                        tournament = tournament,
                        onClick = { onTournamentClick(tournament) }
                    )
                }
                if (completed.isNotEmpty()) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }

            // --- Completed Section ---
            if (completed.isNotEmpty()) {
                item { SectionHeader("Completed") }
                items(completed) { tournament ->
                    RegisteredTournamentCard(
                        tournament = tournament,
                        onClick = { onTournamentClick(tournament) }
                    )
                }
            }
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = refreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.ExtraBold
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

// ---------------------------------------------------------------------
// Tournament Card Component
// ---------------------------------------------------------------------

@Composable
private fun RegisteredTournamentCard(
    tournament: Tournament,
    onClick: () -> Unit
) {
    val buttonText = when (tournament.computedStatus) {
        TournamentStatus.UPCOMING, TournamentStatus.STARTS_SOON -> "View Details"
        TournamentStatus.ONGOING, TournamentStatus.ROOM_OPEN -> "JOIN NOW!"
        else -> "View Results"
    }

    val isLive = tournament.computedStatus == TournamentStatus.ONGOING ||
            tournament.computedStatus == TournamentStatus.ROOM_OPEN

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh, // Use a slightly raised surface
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Tournament Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                // Background placeholder/image
                val imageUrl = tournament.bannerImage ?: ""
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(imageUrl).crossfade(true).build(),
                    contentDescription = "Tournament Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (imageUrl.isBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Placeholder",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Status Badge
                TournamentStatusBadge(
                    status = tournament.computedStatus,
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopEnd)
                )
            }

            // Content Area
            Column(modifier = Modifier.padding(16.dp)) {
                // Tournament Title
                Text(
                    text = tournament.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Tournament Date & Time
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDateTime(tournament.startTime),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Room Access Card for LIVE tournaments (now shown directly in card for convenience)
                if (isLive && tournament.roomId != null && tournament.roomPassword != null) {
                    RoomAccessCard(
                        roomId = tournament.roomId,
                        roomPassword = tournament.roomPassword,
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                    )
                }

                // Action Button
                Button(
                    onClick = onClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .then(if (isLive) Modifier.shimmerEffect() else Modifier),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, // Use error for 'LIVE' emphasis
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = when (tournament.computedStatus) {
                            TournamentStatus.ONGOING, TournamentStatus.ROOM_OPEN -> Icons.Default.PlayArrow
                            else -> Icons.Default.Info
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Helper Components
// ---------------------------------------------------------------------

@Composable
private fun LoadingTournamentsList() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(5) {
            LoadingTournamentCard()
        }
    }
}

@Composable
private fun LoadingTournamentCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp
    ) {
        // Shimmer effect for loading state
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shimmerEffect()
        )
    }
}

@Composable
private fun EmptyTournamentsState(
    onDiscoverClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.EmojiEvents,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No Registered Tournaments",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "You haven't registered for any tournaments yet. Join the competition and start your journey!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onDiscoverClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Discover Tournaments",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Error Loading Tournaments",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Retry",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun Modifier.shimmerEffect(): Modifier {
    val shimmerColors = listOf(
        Color.White.copy(alpha = 0.6f),
        Color.White.copy(alpha = 0.2f),
        Color.White.copy(alpha = 0.6f)
    )

    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ), label = "ShimmerTranslate"
    )

    return this.then(
        Modifier.background(
            brush = Brush.linearGradient(
                colors = shimmerColors,
                start = Offset(0f, 0f),
                end = Offset(translateAnim.value, translateAnim.value)
            )
        )
    )
}

data class MyTournamentsUiState(
    val tournaments: List<Tournament> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)


@Composable
private fun RoomAccessCard(
    roomId: String,
    roomPassword: String,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var roomIdVisible by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "🔑 Room Credentials",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Room ID Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Room ID",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (roomIdVisible) roomId else "••••••••",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row {
                    IconButton(
                        onClick = { roomIdVisible = !roomIdVisible },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (roomIdVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (roomIdVisible) "Hide" else "Show",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(roomId)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Room ID",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Password Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Password",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (passwordVisible) roomPassword else "••••••••",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide" else "Show",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(roomPassword)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Password",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

fun String.toTournamentStatus(): TournamentStatus = when (this.lowercase()) {
    "upcoming" -> TournamentStatus.UPCOMING
    "starts_soon" -> TournamentStatus.STARTS_SOON
    "room_open" -> TournamentStatus.ROOM_OPEN
    "ongoing" -> TournamentStatus.ONGOING
    "completed" -> TournamentStatus.COMPLETED
    else -> TournamentStatus.UPCOMING // Default/fallback
}