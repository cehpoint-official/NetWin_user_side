package com.cehpoint.netwin

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cehpoint.netwin.presentation.screens.TournamentDetailsScreenUI
import com.cehpoint.netwin.presentation.viewmodels.TournamentViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.mockk
import io.mockk.every
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito // Explicitly import Mockito for safer timeout verification
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.io.Serializable

// === FIX 1: Import REAL Domain Models ===
// These classes are now imported from the domain layer,
// as implied by the error message.
import com.cehpoint.netwin.domain.model.Tournament
import com.cehpoint.netwin.domain.model.TournamentStatus
// =======================================


@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class TournamentDetailsUIFallbackTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    private lateinit var mockViewModel: TournamentViewModel
    private lateinit var mockNavController: NavController

    // Mock StateFlows (using MutableStateFlow for control)
    private val selectedTournamentFlow: MutableStateFlow<Tournament?> = MutableStateFlow<Tournament?>(null)
    private val isLoadingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)

    @Before
    fun setup() {
        hiltRule.inject()
        // Use MockK for ViewModel as it's better for Flow properties
        mockViewModel = mockk(relaxed = true)
        // Use Mockito-Kotlin for NavController as it's easier to verify navigation calls
        mockNavController = mock()

        // FIX (Previous Step): Explicitly cast MutableStateFlow to StateFlow
        // The ViewModel defines these properties as StateFlow, so we must cast the
        // MutableStateFlow instances to match the return type.
        every { mockViewModel.selectedTournament } returns selectedTournamentFlow as StateFlow<Tournament?>
        every { mockViewModel.isLoadingDetails } returns isLoadingFlow as StateFlow<Boolean>
        every { mockViewModel.detailsError } returns errorFlow as StateFlow<String?>
    }

    @Test
    fun testLoadingStateDisplaysProgressIndicator() {
        // Setup loading state
        isLoadingFlow.value = true
        selectedTournamentFlow.value = null
        errorFlow.value = null

        composeTestRule.setContent {
            TournamentDetailsScreenUI(
                tournamentId = "test123",
                navController = mockNavController,
                viewModel = mockViewModel
            )
        }

        // Verify loading indicator is displayed
        composeTestRule.onNodeWithContentDescription("Loading")
            .assertIsDisplayed()

        // Verify other states are not displayed
        composeTestRule.onNodeWithText("Unknown error")
            .assertDoesNotExist()
        composeTestRule.onNodeWithText("No tournament data available")
            .assertDoesNotExist()
    }

    @Test
    fun testErrorStateDisplaysErrorMessage() {
        val errorMessage = "Network connection failed"

        // Setup error state
        isLoadingFlow.value = false
        selectedTournamentFlow.value = null
        errorFlow.value = errorMessage

        composeTestRule.setContent {
            TournamentDetailsScreenUI(
                tournamentId = "test123",
                navController = mockNavController,
                viewModel = mockViewModel
            )
        }

        // Verify error message is displayed
        composeTestRule.onNodeWithText(errorMessage)
            .assertIsDisplayed()

        // Verify loading indicator is not displayed
        composeTestRule.onNodeWithContentDescription("Loading")
            .assertDoesNotExist()
    }

    @Test
    fun testNullTournamentDisplaysFallbackMessage() {
        // Setup null tournament state (no error, not loading)
        isLoadingFlow.value = false
        selectedTournamentFlow.value = null
        errorFlow.value = null

        composeTestRule.setContent {
            TournamentDetailsScreenUI(
                tournamentId = "test123",
                navController = mockNavController,
                viewModel = mockViewModel
            )
        }

        // Verify fallback message is displayed
        composeTestRule.onNodeWithText("No tournament data available")
            .assertIsDisplayed()

        // Verify other states are not displayed
        composeTestRule.onNodeWithContentDescription("Loading")
            .assertDoesNotExist()
    }

    @Test
    fun testSuccessfulTournamentDataDisplay() {
        // FIX: Corrected constructor parameters to match the expected 'Tournament' data class
        val tournament = Tournament(
            id = "test123",
            name = "Test Tournament",
            description = "Test Description",
            status = TournamentStatus.UPCOMING.name, // FIX: status now expects a String (the enum value)
            entryFee = 10.0,
            prizePool = 100.0,
            maxTeams = 16, // FIX: Renamed from maxParticipants
            registeredTeams = 5, // FIX: Renamed from registeredParticipants
            startTime = System.currentTimeMillis() + 3600000,
            // Removed endTime, gameMode, imageUrl parameters
            rules = listOf("Standard tournament rules apply")
        )

        // Setup success state
        isLoadingFlow.value = false
        selectedTournamentFlow.value = tournament
        errorFlow.value = null

        composeTestRule.setContent {
            TournamentDetailsScreenUI(
                tournamentId = "test123",
                navController = mockNavController,
                viewModel = mockViewModel
            )
        }

        // Verify tournament content is displayed
        composeTestRule.onNodeWithText("Test Tournament")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Test Description")
            .assertIsDisplayed()

        // Verify fallback states are not displayed
        composeTestRule.onNodeWithContentDescription("Loading")
            .assertDoesNotExist()
        composeTestRule.onNodeWithText("No tournament data available")
            .assertDoesNotExist()
    }

    @Test
    fun testSlowNetworkScenario() {
        // Start with loading state
        isLoadingFlow.value = true
        selectedTournamentFlow.value = null
        errorFlow.value = null

        composeTestRule.setContent {
            TournamentDetailsScreenUI(
                tournamentId = "test123",
                navController = mockNavController,
                viewModel = mockViewModel
            )
        }

        // Verify loading is shown initially
        composeTestRule.onNodeWithContentDescription("Loading")
            .assertIsDisplayed()

        // Simulate a brief delay
        Thread.sleep(100)

        composeTestRule.onNodeWithContentDescription("Loading")
            .assertIsDisplayed()

        // Finally complete with tournament data
        // FIX: Corrected constructor parameters to match the expected 'Tournament' data class
        val tournament = Tournament(
            id = "test123",
            name = "Loaded Tournament",
            description = "Finally loaded",
            status = TournamentStatus.UPCOMING.name, // FIX: status now expects a String (the enum value)
            entryFee = 10.0,
            prizePool = 100.0,
            maxTeams = 16, // FIX: Renamed from maxParticipants
            registeredTeams = 5, // FIX: Renamed from registeredParticipants
            startTime = System.currentTimeMillis() + 3600000,
            // Removed endTime, gameMode, imageUrl parameters
            rules = listOf("Standard tournament rules apply")
        )

        // Update states to show tournament loaded
        isLoadingFlow.value = false
        selectedTournamentFlow.value = tournament

        // Verify tournament is now displayed
        composeTestRule.onNodeWithText("Loaded Tournament")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Loading")
            .assertDoesNotExist()
    }

    @Test
    fun testTransitionFromLoadingToError() {
        // Start with loading
        isLoadingFlow.value = true
        selectedTournamentFlow.value = null
        errorFlow.value = null

        composeTestRule.setContent {
            TournamentDetailsScreenUI(
                tournamentId = "test123",
                navController = mockNavController,
                viewModel = mockViewModel
            )
        }

        // Verify loading state
        composeTestRule.onNodeWithContentDescription("Loading")
            .assertIsDisplayed()

        // Transition to error state
        isLoadingFlow.value = false
        errorFlow.value = "Failed to load tournament"

        // Verify error state
        composeTestRule.onNodeWithText("Failed to load tournament")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Loading")
            .assertDoesNotExist()
    }

    @Test
    fun testBackButtonFunctionality() {
        // Setup any state
        isLoadingFlow.value = false
        selectedTournamentFlow.value = null
        errorFlow.value = null

        composeTestRule.setContent {
            TournamentDetailsScreenUI(
                tournamentId = "test123",
                navController = mockNavController,
                viewModel = mockViewModel
            )
        }

        // Verify back button exists and is clickable
        composeTestRule.onNodeWithContentDescription("Back")
            .assertIsDisplayed()
            .performClick()

        // Use Mockito-Kotlin verify with the explicit Mockito.timeout mode
        verify(mockNavController, Mockito.timeout(1000)).navigateUp()
    }
}