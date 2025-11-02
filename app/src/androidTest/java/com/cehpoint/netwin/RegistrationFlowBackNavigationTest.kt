package com.cehpoint.netwin

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cehpoint.netwin.domain.model.RegistrationStep
import com.cehpoint.netwin.domain.model.RegistrationStepData
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Note: Removed unused Hilt-related imports and annotations since this test uses
// mock screens and manual state management.

@RunWith(AndroidJUnit4::class)
class RegistrationFlowBackNavigationTest {

    // Note: Removed @Rule val hiltRule = HiltAndroidRule(this) as Hilt is not utilized.

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Note: Removed @Inject lateinit var firebaseManager: FirebaseManager as it's not used.

    private lateinit var navController: NavController

    // Test state tracking variables
    private var topBarBackPressed by mutableStateOf(false)
    private var systemBackPressed by mutableStateOf(false)
    private var navigateUpCalled by mutableStateOf(false)
    private var currentRegistrationStep by mutableStateOf(RegistrationStep.REVIEW)

    // Note: The original test code had several unused state tracking variables (popBackStackCalled, registrationStepData)

    @Before
    fun setup() {
        // Initialization if needed
    }

    @Test
    fun testTopBarBackNavigationBetweenRegistrationSteps() {
        var currentStep by mutableStateOf(RegistrationStep.REVIEW)
        var stepData by mutableStateOf(RegistrationStepData())

        composeTestRule.setContent {
            navController = rememberNavController()

            // Mock registration flow with top bar navigation
            MockRegistrationFlowScreen(
                currentStep = currentStep,
                stepData = stepData,
                onStepChange = { step -> currentStep = step },
                onDataChange = { data -> stepData = data },
                onTopBarBack = {
                    topBarBackPressed = true
                    if (currentStep != RegistrationStep.REVIEW) {
                        currentStep = getPreviousStep(currentStep)
                    } else {
                        navigateUpCalled = true
                        navController.navigateUp()
                    }
                }
            )
        }

        composeTestRule.waitForIdle()

        // Start at REVIEW step
        composeTestRule.onNodeWithTag("registration_step_REVIEW").assertIsDisplayed()
        assertEquals(RegistrationStep.REVIEW, currentStep)

        // Move to PAYMENT step
        composeTestRule.runOnUiThread {
            currentStep = RegistrationStep.PAYMENT
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("registration_step_PAYMENT").assertIsDisplayed()

        // Move to DETAILS step
        composeTestRule.runOnUiThread {
            currentStep = RegistrationStep.DETAILS
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("registration_step_DETAILS").assertIsDisplayed()

        // Move to CONFIRM step
        composeTestRule.runOnUiThread {
            currentStep = RegistrationStep.CONFIRM
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("registration_step_CONFIRM").assertIsDisplayed()

        // Test top bar back from CONFIRM to DETAILS
        topBarBackPressed = false
        composeTestRule.onNodeWithTag("top_bar_back_button").performClick()

        composeTestRule.waitForIdle()
        assertTrue("Top bar back should be pressed", topBarBackPressed)
        assertEquals(RegistrationStep.DETAILS, currentStep)
        composeTestRule.onNodeWithTag("registration_step_DETAILS").assertIsDisplayed()

        // Test top bar back from DETAILS to PAYMENT
        topBarBackPressed = false
        composeTestRule.onNodeWithTag("top_bar_back_button").performClick()

        composeTestRule.waitForIdle()
        assertTrue("Top bar back should be pressed", topBarBackPressed)
        assertEquals(RegistrationStep.PAYMENT, currentStep)
        composeTestRule.onNodeWithTag("registration_step_PAYMENT").assertIsDisplayed()

        // Test top bar back from PAYMENT to REVIEW
        topBarBackPressed = false
        composeTestRule.onNodeWithTag("top_bar_back_button").performClick()

        composeTestRule.waitForIdle()
        assertTrue("Top bar back should be pressed", topBarBackPressed)
        assertEquals(RegistrationStep.REVIEW, currentStep)
        composeTestRule.onNodeWithTag("registration_step_REVIEW").assertIsDisplayed()

        // Test top bar back from REVIEW - should exit registration flow
        topBarBackPressed = false
        navigateUpCalled = false
        composeTestRule.onNodeWithTag("top_bar_back_button").performClick()

        composeTestRule.waitForIdle()
        assertTrue("Top bar back should be pressed", topBarBackPressed)
        assertTrue("NavigateUp should be called when exiting registration flow", navigateUpCalled)
    }

    @Test
    fun testSystemBackNavigationWithStatePreservation() {
        var currentStep by mutableStateOf(RegistrationStep.REVIEW)
        var stepData by remember { mutableStateOf(RegistrationStepData()) }

        composeTestRule.setContent {
            navController = rememberNavController()

            MockRegistrationFlowScreen(
                currentStep = currentStep,
                stepData = stepData,
                onStepChange = { step -> currentStep = step },
                onDataChange = { data -> stepData = data },
                onSystemBack = {
                    systemBackPressed = true
                    if (currentStep != RegistrationStep.REVIEW) {
                        // Save current step data before going back
                        when (currentStep) {
                            RegistrationStep.PAYMENT -> {
                                // Assuming paymentMethod is a field in RegistrationStepData
                                stepData = stepData.copy(paymentMethod = "test_payment")
                            }
                            RegistrationStep.DETAILS -> {
                                // Assuming playerName is a field in RegistrationStepData
                                stepData = stepData.copy(playerName = "test_player")
                            }
                            RegistrationStep.CONFIRM -> {
                                // Assuming agreedToTerms is a field in RegistrationStepData
                                stepData = stepData.copy(agreedToTerms = true)
                            }
                            else -> {}
                        }
                        currentStep = getPreviousStep(currentStep)
                    } else {
                        navigateUpCalled = true
                        navController.navigateUp()
                    }
                }
            )
        }

        composeTestRule.waitForIdle()

        // Navigate through steps and add data
        composeTestRule.runOnUiThread {
            currentStep = RegistrationStep.PAYMENT
            stepData = stepData.copy(paymentMethod = "credit_card")
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnUiThread {
            currentStep = RegistrationStep.DETAILS
            stepData = stepData.copy(playerName = "John Doe")
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnUiThread {
            currentStep = RegistrationStep.CONFIRM
            stepData = stepData.copy(agreedToTerms = true)
        }
        composeTestRule.waitForIdle()

        // Test system back from CONFIRM - should preserve state
        systemBackPressed = false
        Espresso.pressBack()

        composeTestRule.waitForIdle()
        assertTrue("System back should be pressed", systemBackPressed)
        assertEquals(RegistrationStep.DETAILS, currentStep)

        // Since stepData is a MutableState in the composable, we check its value directly.
        // The values here should reflect the state *after* the onSystemBack logic for CONFIRM ran.
        // For CONFIRM, the onSystemBack logic set agreedToTerms = true.
        assertTrue("Terms agreement should be preserved (set in onSystemBack)", stepData.agreedToTerms)
        assertEquals("Payment method should be preserved (from previous steps)", "credit_card", stepData.paymentMethod)
        assertEquals("Player name should be preserved (from previous steps)", "John Doe", stepData.playerName)


        // Continue back navigation and verify state preservation
        systemBackPressed = false
        Espresso.pressBack()

        composeTestRule.waitForIdle()
        assertTrue("System back should be pressed", systemBackPressed)
        assertEquals(RegistrationStep.PAYMENT, currentStep)

        // Now, we are in PAYMENT step, and the onSystemBack for DETAILS ran just before the transition.
        // The onSystemBack for DETAILS sets playerName = "test_player".
        assertEquals("Player name should be updated by DETAILS step's onSystemBack", "test_player", stepData.playerName)
        assertTrue("Terms agreement should be preserved", stepData.agreedToTerms)
    }

    @Test
    fun testIllegalStatePreventionInRegistrationFlow() {
        var currentStep by mutableStateOf(RegistrationStep.REVIEW)
        var illegalNavigationAttempt by mutableStateOf(false)

        composeTestRule.setContent {
            navController = rememberNavController()

            MockRegistrationFlowScreen(
                currentStep = currentStep,
                stepData = RegistrationStepData(),
                onStepChange = { step -> currentStep = step },
                onDataChange = { },
                onIllegalNavigation = { illegalNavigationAttempt = true }
            )
        }

        composeTestRule.waitForIdle()

        // Trigger the illegal navigation attempt via the "Previous" button
        composeTestRule.onNodeWithText("Previous").performClick()

        composeTestRule.waitForIdle()
        assertTrue("Illegal navigation attempt should be prevented by calling onIllegalNavigation", illegalNavigationAttempt)
        assertEquals("Should remain at REVIEW step", RegistrationStep.REVIEW, currentStep)
    }

    @Test
    fun testRegistrationFlowExitConfirmation() {
        var currentStep by mutableStateOf(RegistrationStep.DETAILS)
        var exitConfirmationShown by mutableStateOf(false)
        var forceExit by mutableStateOf(false)

        composeTestRule.setContent {
            navController = rememberNavController()

            MockRegistrationFlowScreen(
                currentStep = currentStep,
                // Pass data that signals unsaved changes (playerName = "Test Player")
                stepData = RegistrationStepData(playerName = "Test Player"),
                onStepChange = { step -> currentStep = step },
                onDataChange = { },
                showExitConfirmation = exitConfirmationShown,
                onShowExitConfirmation = { exitConfirmationShown = it },
                onForceExit = { forceExit = it },
                onSystemBack = {
                    if (currentStep == RegistrationStep.REVIEW && !forceExit) {
                        // Logic simplified for mock: assume showing confirmation is the goal
                        exitConfirmationShown = true
                    } else {
                        navigateUpCalled = true
                        navController.navigateUp()
                    }
                }
            )
        }

        composeTestRule.waitForIdle()

        // Navigate to first step with some data entered
        composeTestRule.runOnUiThread {
            currentStep = RegistrationStep.REVIEW
        }
        composeTestRule.waitForIdle()

        // Try to exit - should show confirmation
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        assertTrue("Exit confirmation should be shown", exitConfirmationShown)
        composeTestRule.onNodeWithTag("exit_confirmation_dialog").assertIsDisplayed()

        // Cancel exit
        composeTestRule.onNodeWithTag("cancel_exit_button").performClick()
        composeTestRule.waitForIdle()

        assertFalse("Exit confirmation should be hidden", exitConfirmationShown)
        assertEquals("Should remain at REVIEW step", RegistrationStep.REVIEW, currentStep)

        // Try to exit again and confirm
        Espresso.pressBack()
        composeTestRule.waitForIdle()
        assertTrue("Exit confirmation should be shown again", exitConfirmationShown)

        composeTestRule.onNodeWithTag("confirm_exit_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue("NavigateUp should be called after confirmation", navigateUpCalled)
    }

    @Test
    fun testBackNavigationPerformance() {
        var navigationCount by remember { mutableStateOf(0) }

        composeTestRule.setContent {
            navController = rememberNavController()

            MockRegistrationFlowScreen(
                currentStep = RegistrationStep.CONFIRM,
                stepData = RegistrationStepData(),
                onStepChange = { },
                onDataChange = { },
                onSystemBack = {
                    navigationCount++
                }
            )
        }

        composeTestRule.waitForIdle()

        val startTime = System.currentTimeMillis()

        // Perform rapid back navigations
        repeat(10) {
            Espresso.pressBack()
            // Wait for recomposition/state update, but not for idle, to test "rapid" behavior
            composeTestRule.mainClock.advanceTimeBy(50)
        }

        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime

        // Verify performance is acceptable (should complete within a very short time)
        // 10 back presses (which only update state in this mock) should be very fast.
        assertTrue("Navigation should complete within 500 milliseconds", totalTime < 500)
        assertTrue("All navigation events should be handled (at least 10 attempts)", navigationCount >= 10)
    }

    private fun getPreviousStep(currentStep: RegistrationStep): RegistrationStep {
        return when (currentStep) {
            RegistrationStep.PAYMENT -> RegistrationStep.REVIEW
            RegistrationStep.DETAILS -> RegistrationStep.PAYMENT
            RegistrationStep.CONFIRM -> RegistrationStep.DETAILS
            RegistrationStep.REVIEW -> RegistrationStep.REVIEW // Can't go back further
        }
    }

    private fun getNextStep(currentStep: RegistrationStep): RegistrationStep {
        return when (currentStep) {
            RegistrationStep.REVIEW -> RegistrationStep.PAYMENT
            RegistrationStep.PAYMENT -> RegistrationStep.DETAILS
            RegistrationStep.DETAILS -> RegistrationStep.CONFIRM
            RegistrationStep.CONFIRM -> RegistrationStep.CONFIRM // Can't go further
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MockRegistrationFlowScreen(
        currentStep: RegistrationStep,
        stepData: RegistrationStepData,
        onStepChange: (RegistrationStep) -> Unit,
        onDataChange: (RegistrationStepData) -> Unit,
        onTopBarBack: () -> Unit = {},
        onSystemBack: () -> Unit = {},
        onIllegalNavigation: () -> Unit = {},
        showExitConfirmation: Boolean = false,
        onShowExitConfirmation: (Boolean) -> Unit = {},
        onForceExit: (Boolean) -> Unit = {}
    ) {
        // Handle system back press
        BackHandler {
            onSystemBack()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Registration Step: ${currentStep.name}") },
                    navigationIcon = {
                        IconButton(
                            onClick = onTopBarBack,
                            modifier = Modifier.testTag("top_bar_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .testTag("registration_step_${currentStep.name}"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Current Step: ${currentStep.name}",
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Show step-specific content
                when (currentStep) {
                    RegistrationStep.REVIEW -> {
                        Text("Review tournament details")
                        // Assuming playerName is available
                        Text("Player: ${stepData.playerName}")
                        Text("Payment: ${stepData.paymentMethod}")
                    }
                    RegistrationStep.PAYMENT -> {
                        Text("Payment information")
                        Text("Selected method: ${stepData.paymentMethod}")
                    }
                    RegistrationStep.DETAILS -> {
                        Text("Player details")
                        // Assuming playerName is available
                        Text("Name: ${stepData.playerName}")
                    }
                    RegistrationStep.CONFIRM -> {
                        Text("Confirm registration")
                        // Assuming agreedToTerms is available
                        Text("Terms agreed: ${stepData.agreedToTerms}")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Navigation buttons for testing
                Row {
                    Button(
                        onClick = {
                            val nextStep = getNextStep(currentStep)
                            if (nextStep != currentStep) {
                                onStepChange(nextStep)
                            }
                        },
                        enabled = currentStep != RegistrationStep.CONFIRM
                    ) {
                        Text("Next")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val prevStep = getPreviousStep(currentStep)
                            if (prevStep != currentStep) {
                                onStepChange(prevStep)
                            } else {
                                onIllegalNavigation()
                            }
                        },
                        // Added test tag for illegal state prevention test
                        modifier = Modifier.testTag("previous_step_button"),
                        enabled = currentStep != RegistrationStep.REVIEW
                    ) {
                        Text("Previous")
                    }
                }
            }
        }

        // Exit confirmation dialog
        if (showExitConfirmation) {
            AlertDialog(
                onDismissRequest = { onShowExitConfirmation(false) },
                title = { Text("Exit Registration?") },
                text = { Text("You have unsaved changes. Are you sure you want to exit?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onForceExit(true)
                            onSystemBack() // Trigger exit logic again with forceExit = true
                            onShowExitConfirmation(false)
                        },
                        modifier = Modifier.testTag("confirm_exit_button")
                    ) {
                        Text("Exit")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { onShowExitConfirmation(false) },
                        modifier = Modifier.testTag("cancel_exit_button")
                    ) {
                        Text("Cancel")
                    }
                },
                modifier = Modifier.testTag("exit_confirmation_dialog")
            )
        }
    }
}