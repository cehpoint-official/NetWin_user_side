package com.cehpoint.netwin.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
// Removed FilterChip imports
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
// Removed remember, mutableStateOf, setValue imports as they are no longer needed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.cehpoint.netwin.presentation.viewmodels.AlertsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Removed AlertFilter enum

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreenUI(
    navController: NavController,
    viewModel: AlertsViewModel = hiltViewModel()
) {
    val alertsState by viewModel.alertsState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    // Removed selectedFilter state

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alerts", color = Color.White) },
                actions = {
                    IconButton(onClick = {
                        // ** UPDATE 1: Mark all as read action **
                        viewModel.markAllAsRead()
                    }) {
                        Icon(Icons.Default.DoneAll, contentDescription = "Mark all as read", tint = Color.Cyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF181A20))
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Removed Filter Chips Row (LazyRow)

            // Alerts List
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.Cyan)
                }
            } else if (error != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error ?: "Unknown error", color = Color.Red)
                }
            } else {
                // Use alertsState.alerts directly
                val allAlerts = alertsState.alerts
                if (allAlerts.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No alerts", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp), // Apply horizontal padding here
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp), // Add top padding
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allAlerts) { alert -> // Use allAlerts
                            AlertCard(
                                alert = alert,
                                onAlertClick = {
                                    // ** UPDATE 3: Click handler to mark individual alert as read **
                                    if (!alert.isRead) {
                                        viewModel.markAlertAsRead(alert.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlertCard(alert: Alert, onAlertClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAlertClick), // ** UPDATE 2: Add clickable modifier **
        colors = CardDefaults.cardColors(
            containerColor = if (alert.isRead) Color(0xFF23272F) else Color(0xFF2A2A2A)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(getAlertColor(alert.type).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(getAlertIcon(alert.type), contentDescription = null, tint = getAlertColor(alert.type))
            }
            Spacer(Modifier.width(16.dp))
            // Content
            Column(Modifier.weight(1f)) {
                Text(alert.title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(alert.message, color = Color.Gray, fontSize = 14.sp)
                Text(formatDate(alert.timestamp), color = Color.Gray, fontSize = 12.sp)
            }
            // Unread dot
            if (!alert.isRead) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.Cyan)
                )
            }
        }
    }
}

private fun getAlertIcon(type: AlertType): ImageVector {
    return when (type) {
        AlertType.TOURNAMENT -> Icons.Default.EmojiEvents
        AlertType.PAYMENT -> Icons.Default.AccountBalanceWallet
        AlertType.SYSTEM -> Icons.Default.Info
        AlertType.WIN -> Icons.Default.Star
    }
}

private fun getAlertColor(type: AlertType): Color {
    return when (type) {
        AlertType.TOURNAMENT -> Color(0xFFFFD700) // Gold
        AlertType.PAYMENT -> Color(0xFF4CAF50) // Green
        AlertType.SYSTEM -> Color(0xFF2196F3) // Blue
        AlertType.WIN -> Color(0xFFFF9800) // Orange
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

data class Alert(
    val id: String,
    val type: AlertType,
    val title: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean
)

enum class AlertType {
    TOURNAMENT,
    PAYMENT,
    SYSTEM,
    WIN
}