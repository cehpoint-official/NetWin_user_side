package com.cehpoint.netwin.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cehpoint.netwin.presentation.screens.Alert
import com.cehpoint.netwin.presentation.screens.AlertType
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await // Import for using await() on Firebase Tasks
import javax.inject.Inject

// ** NOTE: Assuming FirebaseFirestore is provided via a Hilt Module elsewhere **
@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val firestore: FirebaseFirestore // 1. Inject FirebaseFirestore
) : ViewModel() {
    private val _alertsState = MutableStateFlow(AlertsState())
    val alertsState: StateFlow<AlertsState> = _alertsState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadAlerts()
    }

    private fun loadAlerts() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                // 2. Query the "announcements" collection
                val snapshot = firestore.collection("announcements")
                    // Optional: Add ordering (e.g., by timestamp descending)
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get()
                    .await() // Suspend the coroutine until the result is available

                // 3. Map Firestore documents to Alert data class
                val announcements = snapshot.documents.mapNotNull { document ->
                    // Documents are mapped to a FirestoreDocument model first (see below)
                    val firestoreDoc = document.toObject<FirestoreAlertDocument>()
                    firestoreDoc?.let { doc ->
                        Alert(
                            id = document.id, // Use document ID as the Alert ID
                            type = AlertType.valueOf(doc.type.uppercase()), // Convert string to enum
                            title = doc.title,
                            message = doc.message,
                            timestamp = doc.timestamp ?: System.currentTimeMillis(), // Use Firestore timestamp
                            isRead = false // Alerts start as unread on first load
                        )
                    }
                }

                _alertsState.value = AlertsState(alerts = announcements)

            } catch (e: Exception) {
                _error.value = "Failed to load announcements: ${e.message}"
                _alertsState.value = AlertsState(alerts = emptyList())
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            try {
                val currentAlerts = _alertsState.value.alerts
                val updatedAlerts = currentAlerts.map { it.copy(isRead = true) }
                _alertsState.value = _alertsState.value.copy(alerts = updatedAlerts)

                // TODO: Update read status in the backend (e.g., in a 'user_alerts' subcollection)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to mark alerts as read"
            }
        }
    }

    fun markAlertAsRead(alertId: String) {
        viewModelScope.launch {
            try {
                val currentAlerts = _alertsState.value.alerts
                val updatedAlerts = currentAlerts.map {
                    if (it.id == alertId) it.copy(isRead = true) else it
                }
                _alertsState.value = _alertsState.value.copy(alerts = updatedAlerts)

                // TODO: Update read status in the backend
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to mark alert as read"
            }
        }
    }

    fun refreshAlerts() {
        loadAlerts()
    }
}

data class AlertsState(
    val alerts: List<Alert> = emptyList()
)

// ** NEW: Internal Data Model for Firestore mapping **
// This mirrors the structure of a document in the 'announcements' collection.
data class FirestoreAlertDocument(
    val type: String = "", // e.g., "TOURNAMENT", "WIN", "SYSTEM"
    val title: String = "",
    val message: String = "",
    // Firestore stores timestamps as Long (milliseconds since epoch) in its default Kotlin mapping
    val timestamp: Long? = null
)