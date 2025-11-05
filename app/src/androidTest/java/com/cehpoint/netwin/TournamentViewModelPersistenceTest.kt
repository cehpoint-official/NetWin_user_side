package com.cehpoint.netwin

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cehpoint.netwin.data.local.DataStoreManager
import com.cehpoint.netwin.data.remote.FirebaseManager
// ⭐️ FIX: Import the actual domain models instead of redeclaring them
import com.cehpoint.netwin.domain.model.RegistrationStep
import com.cehpoint.netwin.domain.model.RegistrationStepData
import com.cehpoint.netwin.domain.repository.TournamentRepository
import com.cehpoint.netwin.domain.repository.UserRepository
import com.cehpoint.netwin.domain.repository.WalletRepository
import com.cehpoint.netwin.presentation.events.RegistrationFlowEvent
import com.cehpoint.netwin.presentation.viewmodels.TournamentViewModel
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
// ⭐️ FIX: Import NetworkStateMonitor from the core:utils module package
import com.cehpoint.netwin.utils.NetworkStateMonitor
// Removed redundant local definitions and type alias

/**
 * Instrumentation tests for TournamentViewModel state persistence using SavedStateHandle.
 * These tests simulate process death and recreation to ensure registration flow state is preserved.
 */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
// ❌ FIX: Remove the unused constructor parameter 'currentData: Any' from the test class
class ProcessDeathPersistenceTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    // Mock dependencies
    private lateinit var mockRepository: TournamentRepository
    private lateinit var mockFirebaseManager: FirebaseManager
    private lateinit var mockUserRepository: UserRepository
    private lateinit var mockWalletRepository: WalletRepository
    private lateinit var mockDataStoreManager: DataStoreManager
    // ⭐️ FIX: Use the actual class type from the imported package
    private lateinit var mockNetworkStateMonitor: NetworkStateMonitor

    @Before
    fun setUp() {
        hiltRule.inject()
        mockRepository = mockk(relaxed = true)
        mockFirebaseManager = mockk(relaxed = true)
        mockUserRepository = mockk(relaxed = true)
        mockWalletRepository = mockk(relaxed = true)
        mockDataStoreManager = mockk(relaxed = true)
        // ⭐️ FIX: Mock the actual class
        mockNetworkStateMonitor = mockk<NetworkStateMonitor>(relaxed = true)
    }

    @Test
    fun testCurrentStepPersistenceAfterProcessDeath() = runTest {

        // First instance: Create ViewModel and set state
        val firstSavedStateHandle = SavedStateHandle()
        val firstViewModel = TournamentViewModel(
            repository = mockRepository,
            firebaseManager = mockFirebaseManager,
            userRepository = mockUserRepository,
            walletRepository = mockWalletRepository,
            dataStoreManager = mockDataStoreManager,
            networkStateMonitor = mockNetworkStateMonitor,
            savedStateHandle = firstSavedStateHandle
        )

        // Initial state should be REVIEW
        assertThat(firstViewModel.currentStep.value).isEqualTo(RegistrationStep.REVIEW)

        // Simulate state change (the ViewModel would typically save this state)
        // Note: Using the actual RegistrationStep class from domain.model
        firstSavedStateHandle["current_step"] = RegistrationStep.PAYMENT

        // Second instance: Create new ViewModel with the saved state (simulating process recreation)
        val secondSavedStateHandle = SavedStateHandle().apply {
            set("current_step", RegistrationStep.PAYMENT)
        }

        val secondViewModel = TournamentViewModel(
            repository = mockRepository,
            firebaseManager = mockFirebaseManager,
            userRepository = mockUserRepository,
            walletRepository = mockWalletRepository,
            dataStoreManager = mockDataStoreManager,
            networkStateMonitor = mockNetworkStateMonitor,
            savedStateHandle = secondSavedStateHandle
        )

        // Verify that the step was restored
        assertThat(secondViewModel.currentStep.value).isEqualTo(RegistrationStep.PAYMENT)
    }

    @Test
    fun testStepDataPersistenceAfterProcessDeath() = runTest {
        // Create test data
        val testStepData = RegistrationStepData(
            teamName = "TestTeam",
            paymentMethod = "wallet",
            termsAccepted = true,
            tournamentId = "tournament-123",
            // ❌ FIX: Removed 'inGameId' and 'playerName' as they are not in the constructor.
            // Use 'playerIds' to set the player data.
            playerIds = listOf("PlayerName1"),
        )

        // First instance: Create ViewModel and update step data
        val firstSavedStateHandle = SavedStateHandle()
        val firstViewModel = TournamentViewModel(
            repository = mockRepository,
            firebaseManager = mockFirebaseManager,
            userRepository = mockUserRepository,
            walletRepository = mockWalletRepository,
            dataStoreManager = mockDataStoreManager,
            networkStateMonitor = mockNetworkStateMonitor,
            savedStateHandle = firstSavedStateHandle
        )

        // Update step data using the event handler
        firstViewModel.onRegistrationEvent(
            RegistrationFlowEvent.UpdateData { testStepData }
        )

        // Simulate ViewModel writing to SavedStateHandle (what the ViewModel's inner logic does)
        firstSavedStateHandle["step_data"] = firstViewModel.stepData.value


        // Second instance: Create new ViewModel with the saved state
        val secondSavedStateHandle = SavedStateHandle().apply {
            // Restore the state key-value pair that was persisted by the first ViewModel
            set("step_data", firstSavedStateHandle.get<RegistrationStepData>("step_data"))
        }

        val secondViewModel = TournamentViewModel(
            repository = mockRepository,
            firebaseManager = mockFirebaseManager,
            userRepository = mockUserRepository,
            walletRepository = mockWalletRepository,
            dataStoreManager = mockDataStoreManager,
            networkStateMonitor = mockNetworkStateMonitor,
            savedStateHandle = secondSavedStateHandle
        )

        // Verify that the step data was restored
        assertThat(secondViewModel.stepData.value.teamName).isEqualTo("TestTeam")
        assertThat(secondViewModel.stepData.value.tournamentId).isEqualTo("tournament-123")
        assertThat(secondViewModel.stepData.value.termsAccepted).isTrue()
    }

    @Test
    fun testCompleteRegistrationFlowStateAfterProcessDeath() = runTest {
        // Create test data
        val testStepData = RegistrationStepData(
            teamName = "TeamAwesome",
            paymentMethod = "wallet",
            termsAccepted = false,
            tournamentId = "epic-tournament",
            // ❌ FIX: Removed 'inGameId' and 'playerName' as they are not in the constructor.
            playerIds = listOf("EpicGamerTag") // Ensure all required fields are present
        )

        // First instance: Create ViewModel and simulate user interaction
        val firstSavedStateHandle = SavedStateHandle()
        val firstViewModel = TournamentViewModel(
            repository = mockRepository,
            firebaseManager = mockFirebaseManager,
            userRepository = mockUserRepository,
            walletRepository = mockWalletRepository,
            dataStoreManager = mockDataStoreManager,
            networkStateMonitor = mockNetworkStateMonitor,
            savedStateHandle = firstSavedStateHandle
        )

        // Simulate user progressing through registration and state being saved
        firstViewModel.onRegistrationEvent(RegistrationFlowEvent.UpdateData { testStepData })

        // Simulate the ViewModel saving its state before process death
        firstSavedStateHandle["current_step"] = RegistrationStep.DETAILS
        firstSavedStateHandle["step_data"] = firstViewModel.stepData.value


        // Second instance: Simulate process death and recreation
        val secondSavedStateHandle = SavedStateHandle().apply {
            // Restore state from the persisted keys
            set("current_step", firstSavedStateHandle.get<RegistrationStep>("current_step"))
            set("step_data", firstSavedStateHandle.get<RegistrationStepData>("step_data"))
        }

        val secondViewModel = TournamentViewModel(
            repository = mockRepository,
            firebaseManager = mockFirebaseManager,
            userRepository = mockUserRepository,
            walletRepository = mockWalletRepository,
            dataStoreManager = mockDataStoreManager,
            networkStateMonitor = mockNetworkStateMonitor,
            savedStateHandle = secondSavedStateHandle
        )

        // Verify complete state restoration
        assertThat(secondViewModel.currentStep.value).isEqualTo(RegistrationStep.DETAILS)
        assertThat(secondViewModel.stepData.value.teamName).isEqualTo("TeamAwesome")
        assertThat(secondViewModel.stepData.value.tournamentId).isEqualTo("epic-tournament")

        // Continue with the flow to verify it still works after restoration
        secondViewModel.onRegistrationEvent(
            // Use the parameter name 'it' for the receiver in the lambda context
            RegistrationFlowEvent.UpdateData { copy(termsAccepted = true) }
        )

        // The ViewModel's internal logic would change the step and save it. We simulate the save.
        secondSavedStateHandle["current_step"] = RegistrationStep.CONFIRM

        // Verify next step logic (by checking the restored/updated state)
        assertThat(secondViewModel.stepData.value.termsAccepted).isTrue()
    }

    @Test
    fun testDefaultStateWhenNoSavedStateExists() = runTest {
        // Create ViewModel with empty SavedStateHandle (no saved state)
        val emptySavedStateHandle = SavedStateHandle()
        val viewModel = TournamentViewModel(
            repository = mockRepository,
            firebaseManager = mockFirebaseManager,
            userRepository = mockUserRepository,
            walletRepository = mockWalletRepository,
            dataStoreManager = mockDataStoreManager,
            networkStateMonitor = mockNetworkStateMonitor,
            savedStateHandle = emptySavedStateHandle
        )

        // Verify default state
        assertThat(viewModel.currentStep.value).isEqualTo(RegistrationStep.REVIEW)
        assertThat(viewModel.stepData.value.teamName).isEmpty()
        assertThat(viewModel.stepData.value.tournamentId).isEmpty()
        assertThat(viewModel.stepData.value.termsAccepted).isFalse()
    }
}