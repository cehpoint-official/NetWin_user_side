package com.cehpoint.netwin

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.compose.ComposeNavigator
import com.cehpoint.netwin.data.local.DataStoreManager
import com.cehpoint.netwin.data.remote.FirebaseManager
import com.cehpoint.netwin.domain.model.Tournament
import com.cehpoint.netwin.domain.repository.TournamentRepository
import com.cehpoint.netwin.domain.repository.UserRepository
import com.cehpoint.netwin.domain.repository.WalletRepository
import com.cehpoint.netwin.presentation.events.RegistrationFlowEvent
import com.cehpoint.netwin.presentation.screens.RegistrationFlowScreen
import com.cehpoint.netwin.presentation.viewmodels.TournamentViewModel
import com.cehpoint.netwin.utils.NetworkStateMonitor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.* // **Crucial import for mock(), whenever(), and any()**

/**
 * UI test for registration flow error badge behavior
 * Tests that error messages only appear on the current step and not on other steps
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RegistrationFlowErrorBadgeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var mockTournamentRepository: TournamentRepository
    private lateinit var mockUserRepository: UserRepository
    private lateinit var mockWalletRepository: WalletRepository
    private lateinit var mockDataStoreManager: DataStoreManager
    private lateinit var mockFirebaseManager: FirebaseManager
    private lateinit var mockFirebaseAuth: FirebaseAuth
    private lateinit var mockFirebaseUser: FirebaseUser
    private lateinit var mockNetworkStateMonitor: NetworkStateMonitor
    private lateinit var navController: TestNavHostController
    private lateinit var viewModel: TournamentViewModel

    private val testTournament = Tournament(
        id = "test-tournament-123",
        name = "Test Tournament",
        entryFee = 100.0,
        prizePool = 1000.0,
        maxTeams = 16,
        gameType = "PUBG",
        matchType = "Squad"
    )

    @Before
    fun setup() = runTest { // **Using runTest for setup to handle suspend calls**
        hiltRule.inject()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        navController = TestNavHostController(context)
        navController.navigatorProvider.addNavigator(ComposeNavigator())

        setupMocks()
        // ViewModel is created inside the test context to ensure coroutine test scope
        // is available for internal ViewModel operations, especially if init blocks call suspend funs.

        // We use the suspending version of getTournamentById in the mock setup, so we need runTest/runBlocking.
        whenever(mockTournamentRepository.getTournamentById("test-tournament-123")).thenReturn(testTournament)

        viewModel = TournamentViewModel(
            repository = mockTournamentRepository,
            firebaseManager = mockFirebaseManager,
            userRepository = mockUserRepository,
            walletRepository = mockWalletRepository,
            dataStoreManager = mockDataStoreManager,
            savedStateHandle = SavedStateHandle(),
            networkStateMonitor = mockNetworkStateMonitor
        )
    }

    private fun setupMocks() {
        mockTournamentRepository = mock()
        mockUserRepository = mock()
        mockWalletRepository = mock()
        mockDataStoreManager = mock()
        mockFirebaseManager = mock()
        mockFirebaseAuth = mock()
        mockFirebaseUser = mock()
        mockNetworkStateMonitor = mock()

        whenever(mockFirebaseManager.auth).thenReturn(mockFirebaseAuth)
        whenever(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        whenever(mockFirebaseUser.uid).thenReturn("test-user-123")
        whenever(mockWalletRepository.getWalletBalance(any())).thenReturn(flowOf(1000.0))
        whenever(mockDataStoreManager.userName).thenReturn(flowOf("TestUser"))
        whenever(mockNetworkStateMonitor.observeNetworkState()).thenReturn(flowOf(true))
        whenever(mockNetworkStateMonitor.isNetworkAvailable()).thenReturn(true)
        // Note: The suspending call mock for getTournamentById is moved to the @Before block
        // where we can use runTest.
    }

    // Removed createViewModel since it's now in @Before

    @Test
    fun testNoErrorBadgeOnStep1_ReviewStep() = runTest {
        // Setup: Start with valid tournament data but invalid details for later steps
        // The viewModel already has the mocks set up from @Before

        composeTestRule.setContent {
            RegistrationFlowScreen(
                tournamentId = "test-tournament-123",
                stepIndex = 1,
                navController = navController,
                viewModel = viewModel
            )
        }

        // Wait for composition and data loading
        composeTestRule.waitForIdle()

        // Verify we're on REVIEW step (Step 1)
        composeTestRule.onNodeWithText("Review Tournament").assertIsDisplayed()

        // Verify no error badge is shown on Step 1 with valid tournament ID
        composeTestRule.onNodeWithTag("error_card", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("In-game ID is required").assertDoesNotExist()
        composeTestRule.onNodeWithText("Team name is required").assertDoesNotExist()

        // Verify continue button is available and clickable
        composeTestRule.onNodeWithText("Continue to Payment").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue to Payment").assertIsEnabled()
    }

    @Test
    fun testErrorBadgeAppearsOnStep3_WhenFieldEmpty() = runTest {
        // Setup: Start with invalid details data
        composeTestRule.setContent {
            RegistrationFlowScreen(
                tournamentId = "test-tournament-123",
                stepIndex = 3,
                navController = navController,
                viewModel = viewModel
            )
        }

        // Wait for composition and data loading
        composeTestRule.waitForIdle()

        // Navigate to Step 3 (DETAILS) by simulating step progression
        viewModel.onRegistrationEvent(RegistrationFlowEvent.UpdateData {
            copy(tournamentId = "test-tournament-123")
        })

        // Move to PAYMENT step (Index 2)
        viewModel.onRegistrationEvent(RegistrationFlowEvent.Next)
        composeTestRule.waitForIdle()

        // Move to DETAILS step (Index 3)
        viewModel.onRegistrationEvent(RegistrationFlowEvent.Next)
        composeTestRule.waitForIdle()

        // Verify we're on DETAILS step (Step 3)
        composeTestRule.onNodeWithText("Game Details").assertIsDisplayed()

        // Try to advance with empty required fields
        composeTestRule.onNodeWithText("Review & Submit").performClick()
        composeTestRule.waitForIdle()

        // Verify error badge appears on Step 3 when required field is empty
        composeTestRule.onNodeWithContentDescription("Error").assertIsDisplayed()
        composeTestRule.onNodeWithText("In-game ID is required").assertIsDisplayed()

        // Verify we remain on the DETAILS step due to validation failure
        composeTestRule.onNodeWithText("Game Details").assertIsDisplayed()
    }

    @Test
    fun testErrorBadgeOnlyOnCurrentStep_NotOnOtherSteps() = runTest {
        composeTestRule.setContent {
            RegistrationFlowScreen(
                tournamentId = "test-tournament-123",
                stepIndex = 1,
                navController = navController,
                viewModel = viewModel
            )
        }

        composeTestRule.waitForIdle()

        // Setup invalid data that would fail on DETAILS step
        viewModel.onRegistrationEvent(RegistrationFlowEvent.UpdateData {
            copy(
                tournamentId = "test-tournament-123",
                //inGameId = "", // Invalid - handled by validation logic
                teamName = "", // Invalid
                paymentMethod = "wallet"
            )
        })

        // Navigate through steps to reach DETAILS
        viewModel.onRegistrationEvent(RegistrationFlowEvent.Next) // To PAYMENT
        composeTestRule.waitForIdle()

        viewModel.onRegistrationEvent(RegistrationFlowEvent.Next) // To DETAILS
        composeTestRule.waitForIdle()

        // Verify we're on DETAILS step
        composeTestRule.onNodeWithText("Game Details").assertIsDisplayed()

        // Try to advance with invalid data - should trigger validation error
        composeTestRule.onNodeWithText("Review & Submit").performClick()
        composeTestRule.waitForIdle()

        // Verify error is displayed on current step (DETAILS)
        composeTestRule.onNodeWithContentDescription("Error").assertIsDisplayed()
        composeTestRule.onNodeWithText("In-game ID is required").assertIsDisplayed()

        // Go back to previous step (PAYMENT)
        // Note: The UI element for "Back" button might need a specific identifier (like testTag or contentDescription)
        // If "Back" doesn't work, ensure your actual back button in RegistrationFlowScreen uses this contentDescription.
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()

        // Verify we're back on PAYMENT step
        composeTestRule.onNodeWithText("Select Payment Method").assertIsDisplayed()

        // Verify error badge does NOT appear on PAYMENT step
        composeTestRule.onNodeWithContentDescription("Error").assertDoesNotExist()
        composeTestRule.onNodeWithText("In-game ID is required").assertDoesNotExist()

        // Go back to REVIEW step
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()

        // Verify we're back on REVIEW step
        composeTestRule.onNodeWithText("Review Tournament").assertIsDisplayed()

        // Verify error badge does NOT appear on REVIEW step
        composeTestRule.onNodeWithContentDescription("Error").assertDoesNotExist()
        composeTestRule.onNodeWithText("In-game ID is required").assertDoesNotExist()
    }

    @Test
    fun testErrorBadgeClearsWhenDataFixed() = runTest {
        composeTestRule.setContent {
            RegistrationFlowScreen(
                tournamentId = "test-tournament-123",
                stepIndex = 1,
                navController = navController,
                viewModel = viewModel
            )
        }

        composeTestRule.waitForIdle()

        // Setup data and navigate to DETAILS step
        viewModel.onRegistrationEvent(RegistrationFlowEvent.UpdateData {
            copy(tournamentId = "test-tournament-123", paymentMethod = "wallet")
        })

        // Navigate to DETAILS step
        viewModel.onRegistrationEvent(RegistrationFlowEvent.Next) // To PAYMENT
        composeTestRule.waitForIdle()
        viewModel.onRegistrationEvent(RegistrationFlowEvent.Next) // To DETAILS
        composeTestRule.waitForIdle()

        // Try to advance with empty fields - should show error
        composeTestRule.onNodeWithText("Review & Submit").performClick()
        composeTestRule.waitForIdle()

        // Verify error is shown
        composeTestRule.onNodeWithContentDescription("Error").assertIsDisplayed()
        composeTestRule.onNodeWithText("In-game ID is required").assertIsDisplayed()

        // Fill in the required field
        composeTestRule.onNodeWithText("In-Game ID").performTextInput("ValidPlayer123")
        composeTestRule.waitForIdle()

        // Error should clear immediately when data is updated (this behavior depends on your ViewModel logic)
        composeTestRule.onNodeWithContentDescription("Error").assertDoesNotExist()
        composeTestRule.onNodeWithText("In-game ID is required").assertDoesNotExist()
    }

    @Test
    fun testStepSpecificValidationOnly() = runTest {
        composeTestRule.setContent {
            RegistrationFlowScreen(
                tournamentId = "test-tournament-123",
                stepIndex = 2,
                navController = navController,
                viewModel = viewModel
            )
        }

        composeTestRule.waitForIdle()

        // Navigate to PAYMENT step with invalid data for other steps
        viewModel.onRegistrationEvent(RegistrationFlowEvent.UpdateData {
            copy(
                tournamentId = "test-tournament-123",
               // Invalid for DETAILS but not relevant for PAYMENT
                teamName = "", // Invalid for DETAILS but not relevant for PAYMENT
                paymentMethod = "wallet", // Valid for PAYMENT
                termsAccepted = false // Invalid for CONFIRM but not relevant for PAYMENT
            )
        })

        viewModel.onRegistrationEvent(RegistrationFlowEvent.Next) // To PAYMENT (which is the current step, but triggers validation)
        composeTestRule.waitForIdle()

        // Verify we're on PAYMENT step
        composeTestRule.onNodeWithText("Select Payment Method").assertIsDisplayed()

        // Try to advance - should succeed because PAYMENT data is valid
        composeTestRule.onNodeWithText("Continue to Details").performClick()
        composeTestRule.waitForIdle()

        // Should successfully move to DETAILS step without showing errors
        // about other step validation failures
        composeTestRule.onNodeWithText("Game Details").assertIsDisplayed()

        // Verify no error from previous step validation
        composeTestRule.onNodeWithContentDescription("Error").assertDoesNotExist()
    }

    @Test
    fun testProgressIndicatorReflectsCurrentStep() = runTest {
        composeTestRule.setContent {
            RegistrationFlowScreen(
                tournamentId = "test-tournament-123",
                stepIndex = 1,
                navController = navController,
                viewModel = viewModel
            )
        }

        composeTestRule.waitForIdle()

        // Check that step 1 is highlighted in progress indicator
        composeTestRule.onNodeWithText("1").assertIsDisplayed()

        // Setup valid data for navigation
        viewModel.onRegistrationEvent(RegistrationFlowEvent.UpdateData {
            copy(
                tournamentId = "test-tournament-123",
                paymentMethod = "wallet",
                //inGameId = "ValidPlayer123",
                teamName = "ValidTeam",
                termsAccepted = true
            )
        })

        // Navigate to next step
        viewModel.onRegistrationEvent(RegistrationFlowEvent.Next)
        composeTestRule.waitForIdle()

        // Verify progress indicator shows step 2 as current
        composeTestRule.onNodeWithText("2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Select Payment Method").assertIsDisplayed()

        // Navigate to step 3
        viewModel.onRegistrationEvent(RegistrationFlowEvent.Next)
        composeTestRule.waitForIdle()

        // Verify progress indicator shows step 3 as current
        composeTestRule.onNodeWithText("3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Game Details").assertIsDisplayed()
    }
}
