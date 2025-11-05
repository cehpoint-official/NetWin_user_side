package com.cehpoint.netwin.presentation.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cehpoint.netwin.BuildConfig
import com.cehpoint.netwin.data.local.DataStoreManager
import com.cehpoint.netwin.data.remote.FirebaseManager
import com.cehpoint.netwin.domain.model.RegistrationStep
import com.cehpoint.netwin.domain.model.RegistrationStepData
import com.cehpoint.netwin.domain.model.Tournament
import com.cehpoint.netwin.domain.model.TournamentStatus
import com.cehpoint.netwin.domain.repository.TournamentRepository
import com.cehpoint.netwin.domain.repository.UserRepository
import com.cehpoint.netwin.domain.repository.WalletRepository
import com.cehpoint.netwin.presentation.events.RegistrationFlowEvent
import com.cehpoint.netwin.utils.NetworkStateMonitor // KEEP THIS IMPORT (Assuming this is the correct path for the utility class)
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.storage
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

// =====================================================================
// NEW: Data Structures for Gemini Analysis
// =====================================================================

/**
 * Data class to hold the structured data parsed from Gemini's JSON output.
 */
data class AnalyzedResult(
    val rank: Int = 0,
    val kills: Int = 0,
    val maxCapacity: Int = 0,
    val playerName: String = ""
)

data class TournamentState(
    val tournaments: List<Tournament> = emptyList(),
    val selectedFilter: TournamentFilter = TournamentFilter.ALL,
    val isLoading: Boolean = false,
    val error: String? = null,
    val countdowns: Map<String, String> = emptyMap(), // tournamentId -> remainingTime
    // NEW: Map for quick registration check (Tournament ID -> isRegistered)
    val userRegistrations: Map<String, Boolean> = emptyMap()
)

// RegistrationStepData is now defined in domain.model package

enum class TournamentFilter {
    ALL, UPCOMING, ONGOING, COMPLETED
}

sealed class TournamentEvent {
    object LoadTournaments : TournamentEvent()
    data class FilterTournaments(val filter: TournamentFilter) : TournamentEvent()
    data class CreateTournament(val tournament: Tournament) : TournamentEvent()
    data class RefreshTournaments(val force: Boolean = false) : TournamentEvent()
}

@HiltViewModel
class TournamentViewModel @Inject constructor(
    private val repository: TournamentRepository,
    private val firebaseManager: FirebaseManager,
    private val userRepository: UserRepository,
    private val walletRepository: WalletRepository, // INJECTED
    private val dataStoreManager: DataStoreManager,
    private val savedStateHandle: SavedStateHandle,
    // 🔥 FIX: Use the simple name and rely on the import for Hilt/KSP resolution
    private val networkStateMonitor: NetworkStateMonitor
) : BaseViewModel<TournamentState, TournamentEvent>() {

    companion object {
        private const val KEY_CURRENT_STEP = "current_step"
        private const val KEY_STEP_DATA = "step_data"
    }

    // --- DATABASE, STORAGE, & GEMINI INIT ---
    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    private val storage = Firebase.storage.reference
    private val currentUserId = auth.currentUser?.uid

    // NEW: Gemini Model Initialization (Uses the key exposed via BuildConfig)
    private val geminiModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    // Tournament Details State
    private val _selectedTournament = MutableStateFlow<Tournament?>(null)
    val selectedTournament: StateFlow<Tournament?> = _selectedTournament.asStateFlow()

    private val _isLoadingDetails = MutableStateFlow(false)
    val isLoadingDetails: StateFlow<Boolean> = _isLoadingDetails.asStateFlow()

    private val _detailsError = MutableStateFlow<String?>(null)
    val detailsError: StateFlow<String?> = _detailsError.asStateFlow()

    // Registration State
    private val _registrationState = MutableStateFlow<Result<Unit>?>(null)
    val registrationState: StateFlow<Result<Unit>?> = _registrationState.asStateFlow()

    private val _showKycRequiredDialog = MutableStateFlow(false)
    val showKycRequiredDialog: StateFlow<Boolean> = _showKycRequiredDialog.asStateFlow()

    private val _walletBalance = MutableStateFlow(0.0)
    val walletBalance: StateFlow<Double> = _walletBalance.asStateFlow()

    private val _userName = MutableStateFlow("Gamer")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _lastProcessedTournamentId = MutableStateFlow<String?>(null)
    val lastProcessedTournamentId: StateFlow<String?> = _lastProcessedTournamentId.asStateFlow()

    // User-specific data
    private val _userCountry = MutableStateFlow("India")
    val userCountry: StateFlow<String> = _userCountry.asStateFlow()

    private val _userCurrency = MutableStateFlow("INR")
    val userCurrency: StateFlow<String> = _userCurrency.asStateFlow()

    // NEW: Dedicated refresh state
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()

    private val _refreshSuccess = MutableStateFlow(false)
    val refreshSuccess: StateFlow<Boolean> = _refreshSuccess.asStateFlow()

    // Debounce mechanism for refresh
    private var lastRefreshTime = 0L
    private val refreshDebounceTime = 2000L // 2 seconds

    // NEW: Registration loading and error states
    private val _isRegistering = MutableStateFlow(false)
    val isRegistering: StateFlow<Boolean> = _isRegistering.asStateFlow()

    private val _registrationError = MutableStateFlow<String?>(null)
    val registrationError: StateFlow<String?> = _registrationError.asStateFlow()

    // Registration Flow State Management
    private val _currentStep = savedStateHandle.getStateFlow(KEY_CURRENT_STEP, RegistrationStep.REVIEW)
    val currentStep: StateFlow<RegistrationStep> = _currentStep

    private val _stepData = savedStateHandle.getStateFlow(KEY_STEP_DATA, RegistrationStepData())
    val stepData: StateFlow<RegistrationStepData> = _stepData

    private val _currentError = MutableStateFlow<String?>(null)
    val currentError: StateFlow<String?> = _currentError.asStateFlow()

    val isLastStep = _currentStep.map { it == RegistrationStep.CONFIRM }

    // Network State Monitoring
    private val _networkAvailable = MutableStateFlow(true)
    val networkAvailable: StateFlow<Boolean> = _networkAvailable.asStateFlow()

    // State for My Tournaments screen
    private val _myTournamentsUiState = MutableStateFlow(MyTournamentsUiState())
    val myTournamentsUiState: StateFlow<MyTournamentsUiState> = _myTournamentsUiState.asStateFlow()

    // Retry State Management
    private val _retryCount = MutableStateFlow(0)
    val retryCount: StateFlow<Int> = _retryCount.asStateFlow()

    private val _isRetrying = MutableStateFlow(false)
    val isRetrying: StateFlow<Boolean> = _isRetrying.asStateFlow()

    // NEW: Efficiently track registered tournament IDs for the user
    private val _registeredTournamentIds = MutableStateFlow<Set<String>>(emptySet())
    val registeredTournamentIds: StateFlow<Set<String>> = _registeredTournamentIds.asStateFlow()

    // NEW: State for Prize Payout Status
    private val _payoutStatus = MutableStateFlow<Result<String>?>(null)
    val payoutStatus: StateFlow<Result<String>?> = _payoutStatus.asStateFlow()

    // RegistrationUiState data class for exposing immutable UI state
    data class MyTournamentsUiState(
        val tournaments: List<Tournament> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null
    )

    data class RegistrationUiState(
        val step: RegistrationStep,
        val data: RegistrationStepData,
        val error: String?,
        val loading: Boolean,
        val networkAvailable: Boolean = true,
        val retryCount: Int = 0,
        val isRetrying: Boolean = false
    )

    // Combine internal flows to provide single source of truth for UI
    val registrationUiState: StateFlow<RegistrationUiState> = combine(
        _currentStep,
        _stepData,
        _currentError,
        _isRegistering,
        _networkAvailable,
        _retryCount,
        _isRetrying
    ) { values ->
        val step = values[0] as RegistrationStep
        val data = values[1] as RegistrationStepData
        val error = values[2] as String?
        val loading = values[3] as Boolean
        val networkAvailable = values[4] as Boolean
        val retryCount = values[5] as Int
        val isRetrying = values[6] as Boolean

        RegistrationUiState(
            step = step,
            data = data,
            error = error,
            loading = loading || isRetrying,
            networkAvailable = networkAvailable,
            retryCount = retryCount,
            isRetrying = isRetrying
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RegistrationUiState(
            step = RegistrationStep.REVIEW,
            data = RegistrationStepData(),
            error = null,
            loading = false,
            networkAvailable = true,
            retryCount = 0,
            isRetrying = false
        )
    )

    // =====================================================================
    // Initialization & User Data Load
    // =====================================================================

    init {
        Log.d("TournamentViewModel", "Initializing ViewModel")
        setState(TournamentState())
        loadTournaments()
        loadUserRegistrations()
        startCountdownTimer()

        val userId = firebaseManager.auth.currentUser?.uid
        if (userId != null) {
            viewModelScope.launch {
                walletRepository.getWalletBalance(userId).collect { balance ->
                    _walletBalance.value = balance
                }
            }
            viewModelScope.launch {
                try {
                    val userNameFromDataStore = dataStoreManager.userName.first()
                    if (userNameFromDataStore.isNotBlank()) {
                        _userName.value = userNameFromDataStore
                    } else {
                        val userResult = userRepository.getUser(userId)
                        val user = userResult.getOrNull()
                        _userName.value = user?.username?.takeIf { it.isNotBlank() }
                            ?: user?.displayName?.takeIf { it.isNotBlank() }
                                    ?: "Gamer"
                        val country = user?.country ?: "India"
                        _userCountry.value = country
                        _userCurrency.value = if (country.equals("Nigeria", true) || country.equals("NG", true)) "NGN" else "INR"
                    }
                } catch (e: Exception) {
                    Log.e("TournamentViewModel", "Error fetching user data", e)
                    _userName.value = "Gamer"
                    _userCountry.value = "India"
                    _userCurrency.value = "INR"
                }
            }
        }
    }

    private fun loadUserRegistrations() {
        if (currentUserId == null) return
        viewModelScope.launch {
            try {
                // Collect the real-time flow of registered IDs and update the internal StateFlow
                repository.getUserTournamentRegistrations(currentUserId).collect { registeredIds ->
                    Log.d("TournamentViewModel", "Loaded ${registeredIds.size} registered tournament IDs")
                    _registeredTournamentIds.value = registeredIds.toSet()

                    // Update main TournamentState for the UI to reflect new registration status
                    setState(state.value!!.copy(
                        userRegistrations = registeredIds.associateWith { true }
                    ))
                }
            } catch (e: Exception) {
                Log.e("TournamentViewModel", "Error loading user registrations", e)
            }
        }
    }


    // =====================================================================
    // Registration Flow Logic
    // =====================================================================

    fun nextStep() {
        val currentStepValue = _currentStep.value
        val nextStep = when (currentStepValue) {
            RegistrationStep.REVIEW -> RegistrationStep.PAYMENT
            RegistrationStep.PAYMENT -> RegistrationStep.DETAILS
            RegistrationStep.DETAILS -> RegistrationStep.CONFIRM
            RegistrationStep.CONFIRM -> RegistrationStep.CONFIRM // Stay at last step
        }
        if (nextStep != currentStepValue) {
            savedStateHandle[KEY_CURRENT_STEP] = nextStep
            _currentError.value = null
        }
    }

    fun previousStep() {
        val currentStepValue = _currentStep.value
        val previousStep = when (currentStepValue) {
            RegistrationStep.CONFIRM -> RegistrationStep.DETAILS
            RegistrationStep.DETAILS -> RegistrationStep.PAYMENT
            RegistrationStep.PAYMENT -> RegistrationStep.REVIEW
            RegistrationStep.REVIEW -> RegistrationStep.REVIEW // Stay at first step
        }
        if (previousStep != currentStepValue) {
            savedStateHandle[KEY_CURRENT_STEP] = previousStep
            _currentError.value = null
        }

    }

    fun setStepError(error: String?) {
        _currentError.value = error
    }

    fun resetRegistrationFlow() {
        savedStateHandle[KEY_CURRENT_STEP] = RegistrationStep.REVIEW
        savedStateHandle[KEY_STEP_DATA] = RegistrationStepData()
        _currentError.value = null
    }

    /**
     * Main event handler for RegistrationFlowEvent
     */
    fun onRegistrationEvent(event: RegistrationFlowEvent) {
        Log.d("TournamentViewModel", "onRegistrationEvent: ${event::class.simpleName}")

        when (event) {
            is RegistrationFlowEvent.UpdateData -> {
                // ✅ CORRECTED: Use 'event.update' instead of 'event.transform'
                // to match the parameter name in RegistrationFlowEvent.UpdateData.
                // The explicit cast 'as RegistrationStepData' resolves the SavedStateHandle type safety issue.
                val updatedData = event.update(_stepData.value)
                savedStateHandle[KEY_STEP_DATA] = updatedData as RegistrationStepData
                _currentError.value = null
            }

            is RegistrationFlowEvent.Next -> {
                val validationError = validate()
                if (validationError != null) {
                    _currentError.value = validationError
                } else {
                    _currentError.value = null
                    moveToNextStep()
                }
            }

            is RegistrationFlowEvent.Previous -> {
                _currentError.value = null
                moveToPreviousStep()
            }

            is RegistrationFlowEvent.Reset -> {
                resetRegistrationFlow()
            }

            is RegistrationFlowEvent.Submit -> {
                val validationError = _stepData.value.validateAll()
                if (validationError != null) {
                    _currentError.value = validationError
                    return
                }
                _currentError.value = null
                performTournamentRegistration()
            }
        }
    }

    /**
     * Validates current step data
     */
    private fun validate(): String? {
        val currentData = _stepData.value
        val currentStepValue = _currentStep.value
        return currentData.validate(currentStepValue)
    }

    private fun moveToNextStep() {
        val orderedSteps = RegistrationStep.values()
        val currentStepValue = _currentStep.value
        val currentIndex = orderedSteps.indexOf(currentStepValue)

        if (currentIndex < orderedSteps.size - 1) {
            savedStateHandle[KEY_CURRENT_STEP] = orderedSteps[currentIndex + 1]
        }
    }

    private fun moveToPreviousStep() {
        val orderedSteps = RegistrationStep.values()
        val currentStepValue = _currentStep.value
        val currentIndex = orderedSteps.indexOf(currentStepValue)

        if (currentIndex > 0) {
            savedStateHandle[KEY_CURRENT_STEP] = orderedSteps[currentIndex - 1]
        }
    }


    /**
     * Performs the tournament registration using a secure Firestore Transaction.
     */
    private fun performTournamentRegistration() {
        val currentData = _stepData.value
        val userId = currentUserId
        val displayName = _userName.value // Get display name

        if (userId == null) {
            _currentError.value = "User not authenticated"
            _registrationState.value = Result.failure(Exception("User not authenticated"))
            return
        }

        if (currentData.tournamentId.isBlank()) {
            _currentError.value = "Tournament ID is required"
            _registrationState.value = Result.failure(Exception("Tournament ID is required"))
            return
        }

        viewModelScope.launch {
            _isRegistering.value = true
            _currentError.value = null
            _registrationState.value = null // Reset state

            // Define document references
            val tournamentDocRef = db.collection("tournaments").document(currentData.tournamentId)
            // Use a predictable ID (userId + tournamentId) to easily check for existence
            val registrationDocRef = db.collection("tournament_registrations").document("${userId}_${currentData.tournamentId}")
            val walletDocRef = db.collection("wallets").document(userId) // User's wallet

            try {
                // Run the entire registration as a single, atomic transaction
                db.runTransaction { transaction ->
                    // 1. Check for duplicate registration
                    val existingReg = transaction.get(registrationDocRef)
                    if (existingReg.exists()) {
                        throw FirebaseFirestoreException(
                            "Already registered", // Simple message
                            FirebaseFirestoreException.Code.ALREADY_EXISTS
                        )
                    }

                    // 2. Get tournament data and check if it's full
                    val tournamentSnapshot = transaction.get(tournamentDocRef)
                    if (!tournamentSnapshot.exists()) {
                        throw FirebaseFirestoreException(
                            "Tournament not found",
                            FirebaseFirestoreException.Code.NOT_FOUND
                        )
                    }

                    val currentTeams = tournamentSnapshot.getLong("registeredTeams") ?: 0
                    val maxTeams = tournamentSnapshot.getLong("maxTeams") ?: 0 // Get max teams
                    val tournamentEntryFee = tournamentSnapshot.getDouble("entryFee") ?: 0.0

                    if (currentTeams >= maxTeams) {
                        throw FirebaseFirestoreException(
                            "Tournament is full", // Simple message
                            FirebaseFirestoreException.Code.FAILED_PRECONDITION
                        )
                    }

                    // 3. Check user's wallet balance
                    if (tournamentEntryFee > 0) {
                        val walletSnapshot = transaction.get(walletDocRef)
                        if (!walletSnapshot.exists()) {
                            throw FirebaseFirestoreException(
                                "Wallet not found",
                                FirebaseFirestoreException.Code.NOT_FOUND
                            )
                        }
                        val userBalance = walletSnapshot.getDouble("balance") ?: 0.0
                        if (userBalance < tournamentEntryFee) {
                            throw FirebaseFirestoreException(
                                "Insufficient wallet balance", // Simple message
                                FirebaseFirestoreException.Code.FAILED_PRECONDITION
                            )
                        }

                        // 4. Deduct entry fee from wallet
                        transaction.update(walletDocRef, "balance", FieldValue.increment(-tournamentEntryFee))

                        // 5. Create a transaction log for the entry fee
                        val txLogRef = db.collection("transactions").document() // New random log ID
                        val txData = hashMapOf(
                            "userId" to userId,
                            "type" to "entry_fee",
                            "amount" to -tournamentEntryFee,
                            "tournamentId" to currentData.tournamentId,
                            "timestamp" to FieldValue.serverTimestamp(),
                            "status" to "completed" // Transaction is immediate
                        )
                        transaction.set(txLogRef, txData)
                    }

                    // 6. Create the new registration document
                    val newRegistrationData = hashMapOf(
                        "userId" to userId,
                        "tournamentId" to currentData.tournamentId,
                        "teamName" to currentData.teamName,
                        "playerIds" to currentData.playerIds, // Using playerIds as in your old code
                        "registeredAt" to FieldValue.serverTimestamp(),
                        "paymentStatus" to if (tournamentEntryFee > 0) "completed" else "not_required",
                        "username" to displayName // Store the user's name for easy lookup
                    )
                    transaction.set(registrationDocRef, newRegistrationData)

                    // 7. Update the tournament's team count
                    transaction.update(tournamentDocRef, "registeredTeams", FieldValue.increment(1))

                    // Transaction must return null on success
                    null
                }.await() // Wait for the transaction to complete

                // Transaction committed successfully!
                Log.d("TournamentViewModel", "Registration Transaction SUCCEEDED")
                _registrationState.value = Result.success(Unit)
                resetRegistrationFlow() // Clear the flow
                _lastProcessedTournamentId.value = currentData.tournamentId // Notify UI

                // ** CRITICAL FIX: Update the local set of registered IDs
                _registeredTournamentIds.update { it + currentData.tournamentId }

            } catch (e: Exception) {
                // Transaction failed
                Log.e("TournamentViewModel", "Registration transaction FAILED", e)
                val errorMessage = when ((e as? FirebaseFirestoreException)?.code) {
                    FirebaseFirestoreException.Code.ALREADY_EXISTS ->
                        "You are already registered for this tournament."
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION -> {
                        // Check for specific error messages
                        if (e.message?.contains("full") == true) {
                            "This tournament is already full."
                        } else if (e.message?.contains("Insufficient") == true) {
                            "Insufficient wallet balance."
                        } else {
                            e.message ?: "Registration failed. Please try again."
                        }
                    }
                    FirebaseFirestoreException.Code.NOT_FOUND -> {
                        if (e.message?.contains("Wallet") == true) {
                            "Wallet not found. Please contact support."
                        } else {
                            "Tournament not found. It may have been deleted."
                        }
                    }
                    else -> e.message ?: "An unknown error occurred."
                }
                _currentError.value = errorMessage
                _registrationState.value = Result.failure(e)
            } finally {
                _isRegistering.value = false
                _isRetrying.value = false
            }
        }
    }
    // --- END OF performTournamentRegistration FIX ---


    fun retryRegistration() {
        _currentError.value = null
        _retryCount.value = 0
        performTournamentRegistration()
    }

    private var countdownJob: Job? = null


    fun clearLastProcessedTournamentId() {
        _lastProcessedTournamentId.value = null
    }

    fun clearRefreshSuccess() {
        _refreshSuccess.value = false
    }

    fun clearRefreshError() {
        _refreshError.value = null
    }

    fun canRefresh(): Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime - lastRefreshTime > refreshDebounceTime
    }

    fun clearRegistrationError() {
        _registrationError.value = null
    }

    fun clearRegistrationLoading() {
        _isRegistering.value = false
    }

    // NEW: Clear Payout Status
    fun clearPayoutStatus() {
        _payoutStatus.value = null
    }

    fun getRegisteredTournaments() {
        viewModelScope.launch {
            try {
                _myTournamentsUiState.update { it.copy(isLoading = true, error = null) }
                val userId = currentUserId ?: ""
                if (userId.isBlank()) {
                    _myTournamentsUiState.update { it.copy(isLoading = false, error = "User not authenticated") }
                    return@launch
                }

                // Use the updated list from the repository
                val registeredTournamentIds = repository.getUserTournamentRegistrations(userId).first()
                if (registeredTournamentIds.isEmpty()) {
                    _myTournamentsUiState.update { it.copy(tournaments = emptyList(), isLoading = false) }
                    return@launch
                }

                val tournaments = registeredTournamentIds.mapNotNull { tournamentId ->
                    repository.getTournamentById(tournamentId)
                }.sortedBy { it.startTime }

                _myTournamentsUiState.update { it.copy(tournaments = tournaments, isLoading = false) }

            } catch (e: Exception) {
                _myTournamentsUiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load tournaments") }
            }
        }
    }

    fun refreshTournaments() {
        viewModelScope.launch {
            _isRefreshing.value = true
            getRegisteredTournaments()
            _isRefreshing.value = false
        }
    }

    private fun startCountdownTimer() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                updateAllCountdowns()
                delay(1000)
            }
        }
    }

    private fun updateAllCountdowns() {
        val currentState = state.value ?: return
        val newCountdowns = mutableMapOf<String, String>()

        currentState.tournaments.forEach { tournament ->
            // Use the model's computedStatus
            if (tournament.computedStatus in listOf(TournamentStatus.UPCOMING, TournamentStatus.STARTS_SOON, TournamentStatus.ROOM_OPEN, TournamentStatus.ONGOING)) {
                val countdown = calculateRemainingTime(tournament)
                if (countdown.isNotEmpty()) {
                    newCountdowns[tournament.id] = countdown
                }
            }
        }

        if (newCountdowns != currentState.countdowns) {
            setState(currentState.copy(countdowns = newCountdowns))
        }
    }

    private fun calculateRemainingTime(tournament: Tournament): String {
        val currentTime = System.currentTimeMillis()

        // Use the model's computedStatus to decide WHAT to count down to
        return when (tournament.computedStatus) {
            TournamentStatus.UPCOMING, TournamentStatus.STARTS_SOON -> {
                val timeDiff = tournament.startTime - currentTime
                if (timeDiff <= 0) "Starting..." else formatTimeRemaining(timeDiff)
            }
            TournamentStatus.ROOM_OPEN, TournamentStatus.ONGOING -> {
                val timeDiff = (tournament.completedAt ?: (tournament.startTime + 2 * 60 * 60 * 1000)) - currentTime // Assume 2hr default
                if (timeDiff <= 0) "Ending..." else formatTimeRemaining(timeDiff)
            }
            else -> ""
        }
    }

    private fun formatTimeRemaining(timeDiff: Long): String {
        val hours = timeDiff / (60 * 60 * 1000)
        val minutes = (timeDiff % (60 * 60 * 1000)) / (60 * 1000)
        val seconds = (timeDiff % (60 * 1000)) / 1000

        // ⭐ FIX: Replaced unresolved 'sprintf' with standard 'String.format'
        return when {
            hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, seconds)
            else -> String.format("%02d:%02d", minutes, seconds)
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }

    // =====================================================================
    // Main Tournament List Events
    // =====================================================================

    override fun handleEvent(event: TournamentEvent) {
        when (event) {
            is TournamentEvent.LoadTournaments -> loadTournaments()
            is TournamentEvent.FilterTournaments -> filterTournaments(event.filter)
            is TournamentEvent.CreateTournament -> createTournament(event.tournament)
            is TournamentEvent.RefreshTournaments -> refreshTournaments(event.force)
        }
    }

    /**
     * Corrected `loadTournaments` to combine the tournaments flow with the live
     * user registration IDs flow to get the latest registration status.
     */
    fun loadTournaments() {
        viewModelScope.launch {
            Log.d("TournamentViewModel", "Starting to load tournaments")
            super.setLoading(true)
            super.setError(null)
            try {
                // Combine the tournaments flow with the user registrations flow
                repository.getTournaments().combine(_registeredTournamentIds) { tournaments, registeredIds ->
                    Pair(tournaments, registeredIds)
                }.collect { (tournaments, registeredIds) ->
                    Log.d("TournamentViewModel", "Received ${tournaments.size} tournaments. ${registeredIds.size} registered.")

                    // Map the set of IDs to a Map<String, Boolean> for the state
                    val registrationMap = registeredIds.associateWith { true }

                    val currentState = state.value ?: TournamentState()
                    setState(currentState.copy(
                        tournaments = tournaments,
                        userRegistrations = registrationMap // Update the new field
                    ))
                    Log.d("TournamentViewModel", "Updated state with ${tournaments.size} tournaments")
                }
            } catch (e: Exception) {
                Log.e("TournamentViewModel", "Error loading tournaments", e)
                super.setError(e.message ?: "Failed to load tournaments")
            } finally {
                super.setLoading(false)
                Log.d("TournamentViewModel", "Finished loading tournaments")
            }
        }
    }

    private fun filterTournaments(filter: TournamentFilter) {
        viewModelScope.launch {
            super.setLoading(true)
            super.setError(null)
            try {
                // Wait for both tournaments and registrations to be available
                // NOTE: This uses the existing flow logic. It could be optimized, but is kept for consistency.
                repository.getTournaments().combine(_registeredTournamentIds) { tournaments, registeredIds ->
                    Pair(tournaments, registeredIds)
                }.collect { (tournaments, registeredIds) ->

                    val processedTournaments = tournaments

                    val filteredTournaments = when (filter) {
                        TournamentFilter.ALL -> processedTournaments
                        TournamentFilter.UPCOMING -> processedTournaments.filter {
                            it.computedStatus == TournamentStatus.UPCOMING || it.computedStatus == TournamentStatus.STARTS_SOON
                        }
                        TournamentFilter.ONGOING -> processedTournaments.filter {
                            it.computedStatus == TournamentStatus.ONGOING || it.computedStatus == TournamentStatus.ROOM_OPEN
                        }
                        TournamentFilter.COMPLETED -> processedTournaments.filter { it.computedStatus == TournamentStatus.COMPLETED }
                    }
                    val currentState = state.value ?: TournamentState()
                    setState(currentState.copy(
                        tournaments = filteredTournaments,
                        selectedFilter = filter,
                        // Update the registration map here too
                        userRegistrations = registeredIds.associateWith { true }
                    ))
                }
            } catch (e: Exception) {
                super.setError(e.message ?: "Failed to filter tournaments")
            } finally {
                super.setLoading(false)
            }
        }
    }

    private fun createTournament(tournament: Tournament) {
        viewModelScope.launch {
            super.setLoading(true)
            super.setError(null)
            try {
                repository.createTournament(tournament)
                    .onSuccess { loadTournaments() } // Reload list on success
                    .onFailure { super.setError(it.message ?: "Failed to create tournament") }
            } catch (e: Exception) {
                super.setError(e.message ?: "Failed to create tournament")
            } finally {
                super.setLoading(false)
            }
        }
    }

    /**
     * Corrected `refreshTournaments` to also refresh the user's registration IDs.
     */
    private fun refreshTournaments(force: Boolean) {
        if (!canRefresh() && !force) {
            Log.d("TournamentViewModel", "Refresh debounced")
            return
        }

        viewModelScope.launch {
            lastRefreshTime = System.currentTimeMillis()
            _isRefreshing.value = true
            _refreshError.value = null
            _refreshSuccess.value = false
            Log.d("TournamentViewModel", "Refresh state: isRefreshing = true")

            try {
                val startTime = System.currentTimeMillis()
                val minimumLoadingTime = 1000L

                // Force a fresh fetch (take 1) for tournaments
                val tournaments = repository.getTournaments().take(1).first()
                // Force a fresh fetch (take 1) for registrations
                val registeredIds = repository.getUserTournamentRegistrations(currentUserId ?: "").take(1).first()
                Log.d("TournamentViewModel", "Refresh received ${tournaments.size} tournaments and ${registeredIds.size} registrations.")

                val processedTournaments = tournaments

                val registrationMap = registeredIds.associateWith { true }
                _registeredTournamentIds.value = registeredIds.toSet() // Update the internal StateFlow

                val currentState = state.value ?: TournamentState()
                setState(currentState.copy(
                    tournaments = processedTournaments,
                    userRegistrations = registrationMap // Update the state
                ))

                val elapsedTime = System.currentTimeMillis() - startTime
                if (elapsedTime < minimumLoadingTime) {
                    delay(minimumLoadingTime - elapsedTime)
                }

                _refreshSuccess.value = true
            } catch (e: Exception) {
                Log.e("TournamentViewModel", "Error during refresh", e)
                _refreshError.value = e.message ?: "Failed to refresh tournaments"
            } finally {
                _isRefreshing.value = false
                // Auto-clear success/error messages
                delay(2000)
                _refreshSuccess.value = false
                _refreshError.value = null
            }
        }
    }

    fun getTournamentById(tournamentId: String) {
        Log.d("TournamentViewModel", "Getting tournament details for ID: $tournamentId")
        viewModelScope.launch {
            _isLoadingDetails.value = true
            _detailsError.value = null
            _selectedTournament.value = null

            try {
                val tournament = repository.getTournamentById(tournamentId)
                Log.d("TournamentViewModel", "Received tournament from repository: ${tournament?.name}")
                if (tournament != null) {
                    _selectedTournament.value = tournament
                } else {
                    _detailsError.value = "Tournament not found"
                }
            } catch (e: Exception) {
                Log.e("TournamentViewModel", "Error loading tournament details", e)
                _detailsError.value = "Error loading tournament details: ${e.message}"
            } finally {
                _isLoadingDetails.value = false
            }
        }
    }

    fun clearSelectedTournament() {
        _selectedTournament.value = null
        _detailsError.value = null
    }

    fun clearRegistrationState() {
        _registrationState.value = null
    }

    fun clearKycDialog() {
        _showKycRequiredDialog.value = false
    }

    // =====================================================================
    // 💡 NEW HELPER FUNCTIONS (Gemini Integration)
    // =====================================================================

    /**
     * Helper function to convert a content URI to a Bitmap object.
     * Must be called on a background thread.
     */
    private fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val contentResolver = context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        } catch (e: Exception) {
            Log.e("ImageUtils", "Error converting URI to Bitmap: ${e.message}")
            null
        }
    }

    private fun getAnalysisPrompt(): String {
        return """
        Analyze the provided end-of-match screenshot (e.g., BGMI, PUBG, etc.).
        You must extract the following four values accurately:
        1. The player's final RANK (e.g., '1' for WWCD, '2', '5', etc.).
        2. The player's total KILLS (a number).
        3. The MAX CAPACITY of the match (e.g., total number of players/teams shown).
        4. The name of the player whose result is shown (e.g., the in-game display name).

        Return ONLY the result as a raw JSON string. Do NOT include any additional text, explanation, or markdown formatting (e.g., no ```json ```).
        If a value cannot be found, use 0 for numbers or an empty string for the name.
        JSON keys must be 'rank', 'kills', 'maxCapacity', and 'playerName'.
        Example: {"rank": 1, "kills": 8, "maxCapacity": 60, "playerName": "GamerX_Pro"}
        """
    }

    /**
     * Performs the image analysis using the Gemini API.
     */
    private suspend fun analyzeImageWithGemini(context: Context, uri: Uri): AnalyzedResult {
        val bitmap = uriToBitmap(context, uri) ?: throw Exception("Failed to load image for analysis.")

        val promptText = getAnalysisPrompt()

        // 1. Build the Gemini Request
        val contents = content {
            image(bitmap)
            text(promptText)
        }

        // 2. Call the Gemini API
        val response = geminiModel.generateContent(contents)

        val jsonString = response.text?.trim()
            ?: throw Exception("Gemini returned no text response.")

        // 3. Parse the JSON string into your data class
        return try {
            // Use Gson to deserialize the JSON
            Gson().fromJson(jsonString, AnalyzedResult::class.java).also {
                if (it.rank == 0 && it.kills == 0 && it.playerName.isBlank()) {
                    throw Exception("Could not confidently extract rank, kills, or player name from the image.")
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiAnalysis", "Failed to parse JSON: $jsonString", e)
            throw Exception("Analysis failed. Please ensure the screenshot is clear and shows the final result screen.")
        }
    }

    // =====================================================================
    // ✅ MODIFIED: submitTournamentResult (with Gemini Integration)
    // =====================================================================

    /**
     * Uploads the screenshot to Firebase Storage, analyzes it with Gemini,
     * and records the result submission in Firestore.
     *
     * @param context The application context needed to resolve the Uri.
     * @param tournamentId The ID of the tournament.
     * @param screenshotUri The local Uri of the screenshot file.
     * @param onSuccess Callback to execute upon successful submission.
     * @param onFailure Callback to execute upon failure, with an error message.
     */
    fun submitTournamentResult(
        context: Context, // NEW PARAMETER
        tournamentId: String,
        screenshotUri: Uri,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val userId = currentUserId
        if (userId == null) {
            onFailure("User not authenticated.")
            return
        }

        viewModelScope.launch {
            try {
                // --- 1. PRE-READ DATA FOR AUDIT LOGGING ---
                val tournamentDocRef = db.collection("tournaments").document(tournamentId)
                val tournamentSnapshot = tournamentDocRef.get().await()

                val registrationDocRef = db.collection("tournament_registrations").document("${userId}_${tournamentId}")
                val registrationSnapshot = registrationDocRef.get().await()

                // Extract Audit Data
                @Suppress("UNCHECKED_CAST")
                val tournamentPrizeStructure = tournamentSnapshot.get("prizeStructure") as? Map<String, Double> ?: emptyMap()
                val tournamentMaxTeams = tournamentSnapshot.getLong("maxTeams") ?: 0
                val tournamentKillReward = tournamentSnapshot.getDouble("killReward") ?: 0.0 // FETCH THE KILL REWARD
                val registrationPlayerIds = registrationSnapshot.get("playerIds") as? List<String> ?: emptyList()

                // 2. ANALYZE IMAGE with Gemini
                val analyzedData = analyzeImageWithGemini(context, screenshotUri)
                Log.d("TournamentViewModel", "Gemini Analysis Success: Rank=${analyzedData.rank}, Kills=${analyzedData.kills} , Player=${analyzedData.playerName}")

                // NEW: Calculate Kill Prize for Audit
                val calculatedKillPrize = analyzedData.kills * tournamentKillReward

                // 3. Upload screenshot to Firebase Storage
                val imageFileName = UUID.randomUUID().toString() + ".jpg"
                // Path: tournament_proofs/{tournamentId}/{userId}/{random_uuid}.jpg
                val proofRef = storage.child("tournament_proofs/$tournamentId/$userId/$imageFileName")

                val downloadUri = proofRef.putFile(screenshotUri)
                    .continueWithTask { task ->
                        if (!task.isSuccessful) {
                            task.exception?.let { throw it }
                        }
                        proofRef.downloadUrl // Get the public URL for the image
                    }.await()

                // 4. Save the submission data (including analyzed results & audit data) to Firestore
                val resultData = hashMapOf(
                    "userId" to userId,
                    "tournamentId" to tournamentId,
                    "screenshotUrl" to downloadUri.toString(),
                    "submittedAt" to FieldValue.serverTimestamp(),
                    "status" to "Pending Verification", // Initial status for automated check

                    // Gemini-analyzed results
                    "analyzedRank" to analyzedData.rank,
                    "analyzedKills" to analyzedData.kills,
                    "analyzedMaxCapacity" to analyzedData.maxCapacity,
                    "analyzedPlayerName" to analyzedData.playerName,

                    // Audit Data (Collected as requested)
                    "auditMaxTeams" to tournamentMaxTeams,
                    "auditKillReward" to tournamentKillReward,
                    "auditPlayerIds" to registrationPlayerIds,
                    "auditPrizeStructure" to tournamentPrizeStructure,
                    // NEW: Store the calculated Kill Prize for auditing
                    "auditKillPrizeAmount" to calculatedKillPrize
                )

                db.collection("tournament_results")
                    .add(resultData)
                    .await()

                Log.d("TournamentViewModel", "Result submitted successfully with audit data for Tournament ID: $tournamentId")
                onSuccess()

                // 5. AUTOMATICALLY TRIGGER DISTRIBUTION
                triggerPrizeDistribution(tournamentId)

            } catch (e: Exception) {
                Log.e("TournamentViewModel", "Error submitting tournament result for $tournamentId", e)
                val errorMessage = when (e) {
                    is FirebaseFirestoreException -> "Database error: ${e.localizedMessage}"
                    // Catch the custom error from Gemini analysis
                    is IllegalStateException -> "Analysis failed: ${e.message}"
                    else -> "Submission failed: ${e.localizedMessage}"
                }
                onFailure(errorMessage)
            }
        }
    }

    // =====================================================================
    // 💰 MODIFIED: Automated Prize Distribution Logic (Zero-Admin)
    // =====================================================================

    /**
     * Executes the zero-admin, automatic prize distribution logic.
     * This version ONLY credits the kill prize (auditKillPrizeAmount).
     *
     * @param tournamentId The ID of the tournament to process.
     */
    fun triggerPrizeDistribution(
        tournamentId: String
    ) {
        val userId = currentUserId
        if (userId == null) {
            _payoutStatus.value = Result.failure(Exception("User not authenticated."))
            return
        }

        viewModelScope.launch {
            _payoutStatus.value = null // Reset status
            Log.d("PrizeDistribution", "Starting ZERO-ADMIN prize distribution check for $tournamentId (Kill Prize Only)")

            var submissionDocRef: com.google.firebase.firestore.DocumentReference? = null
            var userFriendlyError: String? = null // Hold the specific failure reason

            try {
                // 0. Fetch User Display Name (from the local StateFlow) - Now only for logging/future use
                // val expectedDisplayName = _userName.value.trim().lowercase() // Removed for temporary bypass

                // 1. Fetch the user's latest result submission
                val submissionQuery = db.collection("tournament_results")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("tournamentId", tournamentId)
                    .orderBy("submittedAt", Query.Direction.DESCENDING)
                    .limit(1)
                    .get().await()

                val submissionDoc = submissionQuery.documents.firstOrNull()
                    ?: throw Exception("No result submission found for this user/tournament.")

                submissionDocRef = submissionDoc.reference

                // --- Extract Data from Submission Document ---
                val analyzedKills = submissionDoc.getLong("analyzedKills")?.toInt() ?: 0
                val analyzedMaxCapacity = submissionDoc.getLong("analyzedMaxCapacity")?.toInt() ?: 0
                val analyzedPlayerName = submissionDoc.getString("analyzedPlayerName")?.trim()?.lowercase() ?: ""

                // The amount to credit, as calculated during submission
                val killPrizeToCredit = submissionDoc.getDouble("auditKillPrizeAmount")
                    ?: throw Exception("Kill Prize Amount Missing in Audit Log")

                // Audit data used for cross-checking
                val auditMaxTeams = submissionDoc.getLong("auditMaxTeams")?.toInt() ?: 0
                @Suppress("UNCHECKED_CAST")
                val auditPlayerIds = submissionDoc.get("auditPlayerIds") as? List<String> ?: emptyList()


                // 2. CROSS-CHECK/VERIFICATION LOGIC (RE-IMPLEMENTING CHECKS)

                // 2a. Check if prize has already been distributed (Idempotency)
                val status = submissionDoc.getString("status")
                if (status == "Prize Distributed") {
                    _payoutStatus.value = Result.success("Prize already distributed to wallet.")
                    return@launch
                }

                // ⭐ RE-IMPLEMENTED CHECK 1: Max Capacity Match
                if (analyzedMaxCapacity != auditMaxTeams) {
                    userFriendlyError = "Verification Failed: Match size discrepancy. Detected $analyzedMaxCapacity, Expected $auditMaxTeams."
                    throw Exception(userFriendlyError)
                }

                // ⭐ RE-IMPLEMENTED CHECK 2: Player ID Match
                val playerMatch = auditPlayerIds.any { it.trim().lowercase() == analyzedPlayerName }
                if (!playerMatch) {
                    userFriendlyError = "Verification Failed: Player name ('${analyzedPlayerName.uppercase()}') does not match registered player IDs."
                    throw Exception(userFriendlyError)
                }


                // 3. PRIZE CALCULATION (Simplified to only use Kill Prize)
                val totalPrizeAmount = killPrizeToCredit // Only the kill prize is credited

                // Log and update status if no prize is earned
                if (totalPrizeAmount <= 0.0) {
                    submissionDoc.reference.update("status", "Verified - No Prize").await()
                    _payoutStatus.value = Result.success("Result verified. You earned ${analyzedKills} kills, total prize: ${userCurrency.value} 0.00.")
                    return@launch
                }

                // 4. SECURE FUND TRANSFER (Use WalletRepository method)

                // Create map for prize audit log in submission doc
                val prizeDetails = mapOf(
                    "killPrizeCredited" to totalPrizeAmount, // Renamed for clarity
                    "totalCredited" to totalPrizeAmount
                )

                // IMPORTANT: Use the dedicated repository method for the atomic update
                val updateResult = walletRepository.incrementWithdrawableBalance(userId, totalPrizeAmount)

                updateResult.onSuccess {
                    // Update the result submission status AND log the calculated prizes
                    submissionDoc.reference.update(
                        "status", "Prize Distributed",
                        "payoutDetails" to prizeDetails // Log the breakdown of the prize
                    ).await()

                    // Success
                    _payoutStatus.value = Result.success("Success! Kill prize of **${userCurrency.value} $totalPrizeAmount** (${analyzedKills} Kills) automatically credited to your **Withdrawal Balance**.")
                }.onFailure { e ->
                    // ⭐ ACTION: Log the Repository error to the userFriendlyError variable
                    userFriendlyError = "Wallet update failed via Repository: ${e.message}"
                    Log.e("PrizeDistribution", "Wallet increment failed via Repository: ${e.message}", e)
                    // Re-throw the exception to fall into the main catch block (Step 5)
                    throw Exception("Wallet update failed: ${e.message}")
                }

            } catch (e: Exception) {
                // 5. ERROR LOGGING AND STATUS UPDATE

                // This block executes if any part of the try block (including the repository failure) threw an exception.

                // If userFriendlyError was set during the verification checks OR the onFailure block, use it.
                // Otherwise, translate general internal errors.
                val finalError = userFriendlyError ?: when (e.message) {
                    // The error message from the failed update is now guaranteed to be captured in finalError
                    // if it was set in the onFailure block above.
                    "Kill Prize Amount Missing in Audit Log" -> "Payout failed: The kill prize amount could not be found. Please contact support."
                    "Wallet not found." -> "Wallet error: Your wallet document is missing. Please contact support immediately."
                    // Catch Firebase Transaction failure message, which is usually generic
                    else -> e.message ?: "An unknown error occurred during verification."
                }

                // Construct the update map to log the failure reason
                val updateMap = hashMapOf<String, Any>(
                    "status" to "Verification Failed"
                )
                if (finalError.isNotBlank()) {
                    updateMap["verificationFailureReason"] = finalError
                }

                // Use the submissionDocRef to update the status and log the failure reason
                if (submissionDocRef != null) {
                    // ⭐ CRITICAL: The double failure happens here. We added a try-catch to prevent a crash
                    // but the underlying PERMISSION_DENIED on the tournament_results doc is the problem.
                    try {
                        submissionDocRef!!.update(updateMap).await()
                    } catch (updateE: Exception) {
                        Log.e("PrizeDistribution", "FATAL: Failed to write final failure status to DB: ${updateE.message}")
                    }
                }

                _payoutStatus.value = Result.failure(Exception(finalError))
            }
        }
    }
}