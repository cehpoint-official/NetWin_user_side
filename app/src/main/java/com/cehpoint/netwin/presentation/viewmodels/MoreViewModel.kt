package com.cehpoint.netwin.presentation.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cehpoint.netwin.data.remote.FirebaseManager
import com.cehpoint.netwin.domain.model.User
import com.cehpoint.netwin.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val firebaseAuth: FirebaseAuth,
    // Add FirebaseManager to handle Firestore/Storage operations
    private val firebaseManager: FirebaseManager
) : ViewModel() {
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // New Flow for status messages (e.g., success/fail toast for reporting)
    private val _reportStatus = MutableStateFlow<String?>(null)
    val reportStatus: StateFlow<String?> = _reportStatus.asStateFlow()

    init {
        val userId = firebaseAuth.currentUser?.uid
        Log.d("MoreViewModel", "init: currentUser uid = $userId")
        if (userId != null) {
            fetchUser(userId)
        } else {
            Log.e("MoreViewModel", "init: currentUser is null")
        }
    }

    fun fetchUser(userId: String) {
        Log.d("MoreViewModel", "fetchUser called with userId = $userId")
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = userRepository.getUser(userId)
                Log.d("MoreViewModel", "getUser result: $result")
                _user.value = result.getOrNull()
                if (_user.value == null) {
                    Log.e("MoreViewModel", "User not found in Firestore for userId = $userId")
                } else {
                    Log.d("MoreViewModel", "User loaded: ${_user.value}")
                }
            } catch (e: Exception) {
                Log.e("MoreViewModel", "Error fetching user: ${e.message}", e)
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Handles submitting an issue report to Firestore.
     * NOTE: Image URL passed here is typically the *local URI* of the selected image.
     * The FirebaseManager/Repository should handle uploading the image to Firebase Storage
     * and replacing the local URI with the final Storage download URL before saving to Firestore.
     */
    fun submitIssueReport(report: String, imageUri: String?) {
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            _reportStatus.value = "Error: User not authenticated."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _reportStatus.value = null // Clear previous status
            try {
                // The structure to save to Firestore
                val reportData = hashMapOf(
                    "userId" to userId,
                    "username" to _user.value?.username, // Add username for easier tracking
                    "reportText" to report,
                    "timestamp" to System.currentTimeMillis()
                )

                // 1. Handle image upload if a URI is provided
                var finalImageUrl: String? = null
                if (!imageUri.isNullOrEmpty()) {
                    // This assumes firebaseManager has a method to upload a file/URI
                    // In a real app, you would pass the actual content URI here for Firebase Storage upload
                    // Example (Implementation needs to be in FirebaseManager):
                    // val uploadResult = firebaseManager.uploadImage(imageUri, "issue_reports/${System.currentTimeMillis()}")
                    // finalImageUrl = uploadResult.getOrThrow()

                    // Since we don't have the actual file pick result, we simulate the storage URL
                    finalImageUrl = "gs://netwin-reports/user_$userId/image_${System.currentTimeMillis()}.jpg"
                    Log.d("MoreViewModel", "Simulated Image Upload URL: $finalImageUrl")
                }

                reportData["imageUrl"] = finalImageUrl ?: "none"

                // 2. Save the report data to a "reports" collection in Firestore
                firebaseManager.firestore.collection("issue_reports").add(reportData).addOnSuccessListener {
                    Log.d("MoreViewModel", "Issue report successfully written with ID: ${it.id}")
                    _reportStatus.value = "Issue successfully reported. Thank you!"
                }.addOnFailureListener { e ->
                    Log.e("MoreViewModel", "Error writing issue report to Firestore", e)
                    _reportStatus.value = "Failed to submit report: ${e.message}"
                    _error.value = "Failed to submit report: ${e.message}"
                }.await() // Use .await() if your FirebaseManager handles coroutines

            } catch (e: Exception) {
                Log.e("MoreViewModel", "General error during report submission: ${e.message}", e)
                _reportStatus.value = "An unexpected error occurred: ${e.message}"
            } finally {
                _isLoading.value = false
                // Clear status after a short delay so the UI can react
                kotlinx.coroutines.delay(5000)
                _reportStatus.value = null
            }
        }
    }


    fun logout() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                firebaseAuth.signOut()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to logout"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshUserData() {
        val userId = firebaseAuth.currentUser?.uid
        Log.d("MoreViewModel", "refreshUserData called, currentUser uid = $userId")
        if (userId != null) {
            fetchUser(userId)
        } else {
            Log.e("MoreViewModel", "refreshUserData: currentUser is null")
        }
    }
}
