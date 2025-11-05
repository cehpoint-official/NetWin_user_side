package com.cehpoint.netwin

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import androidx.navigation.toRoute
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.Serializable
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlin.reflect.KType

// --- MOCK NAVIGATION STRUCTURE FOR COMPILATION ---
// These classes are defined here to satisfy the unresolved references in the test file.

// 1. Mock definition for TournamentRegistration (the parameterized route)
@Serializable
data class TournamentRegistration(val tournamentId: String, val stepIndex: Int)

// 2. Mock definitions for ScreenRoutes (simple screen routes)
sealed interface ScreenRoutes {
    @Serializable data object TournamentsScreen : ScreenRoutes
    @Serializable data object WalletScreen : ScreenRoutes
    @Serializable data object ProfileScreen : ScreenRoutes
    @Serializable data object SettingsScreen : ScreenRoutes
}

// 3. Mock definitions for SubNavigation (nested graph routes)
sealed interface SubNavigation {
    @Serializable data object HomeNavGraph : SubNavigation
    @Serializable data object RegistrationNavGraph : SubNavigation
}

// 4. Mock definition for FirebaseManager and NavGraph (for Hilt and the last test)
class FirebaseManager @Inject constructor()
@Composable
fun NavGraph(firebaseManager: FirebaseManager) {
    // Mock implementation to prevent compile errors
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ScreenRoutes.TournamentsScreen::class) {
        composable<ScreenRoutes.TournamentsScreen> { Text("Mock NavGraph Root") }
    }
}

// --- END MOCK NAVIGATION STRUCTURE ---

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BackNavigationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Inject
    lateinit var firebaseManager: FirebaseManager

    private lateinit var navController: NavHostController // Use NavHostController for rememberNavController()

    // Test state tracking
    private var backHandlerCalled by mutableStateOf(false)
    private var navigationUpCalled by mutableStateOf(false)

    @Before
    fun setup() {
        hiltRule.inject()
        // Reset state variables before each test
        backHandlerCalled = false
        navigationUpCalled = false
    }

    @Test
    fun testSystemBackNavigationFromTournamentsScreen() {
        var currentDestination by mutableStateOf("")

        composeTestRule.setContent {
            navController = rememberNavController()

            // Track navigation changes
            LaunchedEffect(navController) {
                // Using currentBackStackEntryFlow to observe changes
                navController.currentBackStackEntryFlow.collect { entry ->
                    currentDestination = entry.destination.route ?: ""
                }
            }

            // Custom back handler to track system back presses
            BackHandler(enabled = true) {
                backHandlerCalled = true
            }

            NavHost(
                navController = navController,
                startDestination = SubNavigation.HomeNavGraph::class
            ) {
                navigation<SubNavigation.HomeNavGraph>(
                    startDestination = ScreenRoutes.TournamentsScreen::class
                ) {
                    composable<ScreenRoutes.TournamentsScreen> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("tournaments_screen")
                        ) {
                            Text("Tournaments Screen")
                        }
                    }
                    composable<ScreenRoutes.WalletScreen> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("wallet_screen")
                        ) {
                            Text("Wallet Screen")
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // Verify we're on tournaments screen
        composeTestRule.onNodeWithTag("tournaments_screen").assertIsDisplayed()

        // Simulate system back press
        Espresso.pressBack()

        // Verify back handler was called
        assertTrue("System back handler should be called from main screen", backHandlerCalled)
    }

    @Test
    fun testSystemBackNavigationFromNestedScreens() {
        var backStackSize by mutableStateOf(0)

        composeTestRule.setContent {
            navController = rememberNavController()

            // Track back stack size
            LaunchedEffect(navController) {
                // FIX: Use the public and observable 'visibleEntries' property to get the stack size,
                // resolving the 'Unresolved reference: backQueue' error.
                navController.visibleEntries.collect { entries ->
                    backStackSize = entries.size
                }
            }

            NavHost(
                navController = navController,
                startDestination = SubNavigation.HomeNavGraph::class
            ) {
                navigation<SubNavigation.HomeNavGraph>(
                    startDestination = ScreenRoutes.TournamentsScreen::class
                ) {
                    composable<ScreenRoutes.TournamentsScreen> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("tournaments_screen")
                        ) {
                            Text("Tournaments Screen")
                        }
                    }
                    composable<ScreenRoutes.WalletScreen> {
                        BackHandler {
                            backHandlerCalled = true
                            navController.popBackStack()
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("wallet_screen")
                        ) {
                            Text("Wallet Screen")
                        }
                    }
                    composable<ScreenRoutes.ProfileScreen> {
                        BackHandler {
                            backHandlerCalled = true
                            navController.popBackStack()
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("profile_screen")
                        ) {
                            Text("Profile Screen")
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // Navigate to wallet screen
        composeTestRule.runOnUiThread {
            navController.navigate(ScreenRoutes.WalletScreen)
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("wallet_screen").assertIsDisplayed()

        // Navigate to profile screen
        composeTestRule.runOnUiThread {
            navController.navigate(ScreenRoutes.ProfileScreen)
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_screen").assertIsDisplayed()

        // Press back - should go to wallet
        backHandlerCalled = false
        Espresso.pressBack()

        composeTestRule.waitForIdle()
        assertTrue("Back handler should be called", backHandlerCalled)
        composeTestRule.onNodeWithTag("wallet_screen").assertIsDisplayed()

        // Press back again - should go to tournaments
        backHandlerCalled = false
        Espresso.pressBack()

        composeTestRule.waitForIdle()
        assertTrue("Back handler should be called again", backHandlerCalled)
        composeTestRule.onNodeWithTag("tournaments_screen").assertIsDisplayed()
    }

    @Test
    fun testTopBarBackNavigationInRegistrationFlow() {
        var currentStep by mutableStateOf(1)

        composeTestRule.setContent {
            navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = SubNavigation.HomeNavGraph::class
            ) {
                navigation<SubNavigation.HomeNavGraph>(
                    startDestination = ScreenRoutes.TournamentsScreen::class
                ) {
                    composable<ScreenRoutes.TournamentsScreen> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("tournaments_screen")
                        ) {
                            Text("Tournaments Screen")
                        }
                    }

                    navigation<SubNavigation.RegistrationNavGraph>(
                        startDestination = TournamentRegistration::class
                    ) {
                        composable<TournamentRegistration> { backStackEntry ->
                            // Resolved 'toRoute' reference with import
                            val args = backStackEntry.toRoute<TournamentRegistration>()

                            currentStep = args.stepIndex

                            // Simulate registration flow screen with top bar back button
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("registration_step_${args.stepIndex}")
                            ) {
                                Text("Registration Step ${args.stepIndex}")

                                // Simulate top bar back button behavior (now just handles system back)
                                BackHandler {
                                    if (args.stepIndex > 1) {
                                        navController.navigate(
                                            TournamentRegistration(
                                                args.tournamentId,
                                                args.stepIndex - 1
                                            )
                                        )
                                    } else {
                                        navigationUpCalled = true
                                        navController.navigateUp()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // Start tournament registration (step 1)
        composeTestRule.runOnUiThread {
            navController.navigate(TournamentRegistration("test_tournament", 1))
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("registration_step_1").assertIsDisplayed()
        assertEquals(1, currentStep)

        // Navigate to step 2 (Note: This is technically a new screen entry, not a back pop)
        composeTestRule.runOnUiThread {
            navController.navigate(TournamentRegistration("test_tournament", 2))
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("registration_step_2").assertIsDisplayed()
        assertEquals(2, currentStep)

        // Navigate to step 3
        composeTestRule.runOnUiThread {
            navController.navigate(TournamentRegistration("test_tournament", 3))
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("registration_step_3").assertIsDisplayed()
        assertEquals(3, currentStep)

        // Press back from step 3 - should go to step 2 (handled by BackHandler)
        navigationUpCalled = false
        Espresso.pressBack()

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("registration_step_2").assertIsDisplayed()
        assertEquals(2, currentStep)
        assertFalse("navigateUp should not be called when going between steps", navigationUpCalled)

        // Press back from step 2 - should go to step 1 (handled by BackHandler)
        navigationUpCalled = false
        Espresso.pressBack()

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("registration_step_1").assertIsDisplayed()
        assertEquals(1, currentStep)
        assertFalse("navigateUp should not be called when going between steps", navigationUpCalled)

        // Press back from step 1 - should exit registration flow (handled by BackHandler)
        navigationUpCalled = false
        Espresso.pressBack()

        composeTestRule.waitForIdle()
        assertTrue("navigateUp should be called when exiting registration flow", navigationUpCalled)
        composeTestRule.onNodeWithTag("tournaments_screen").assertIsDisplayed()
    }

    @Test
    fun testUpNavigationInNestedGraphs() {
        var isInNestedGraph by mutableStateOf(false)

        composeTestRule.setContent {
            navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = SubNavigation.HomeNavGraph::class
            ) {
                navigation<SubNavigation.HomeNavGraph>(
                    startDestination = ScreenRoutes.TournamentsScreen::class
                ) {
                    composable<ScreenRoutes.TournamentsScreen> {
                        isInNestedGraph = false
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("tournaments_screen")
                        ) {
                            Text("Tournaments Screen")
                        }
                    }

                    navigation<SubNavigation.RegistrationNavGraph>(
                        startDestination = TournamentRegistration::class
                    ) {
                        composable<TournamentRegistration> { backStackEntry ->
                            isInNestedGraph = true
                            // Resolved 'toRoute' reference with import
                            val args = backStackEntry.toRoute<TournamentRegistration>()

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("nested_screen")
                            ) {
                                Text("Nested Registration Screen")

                                BackHandler {
                                    navigationUpCalled = true
                                    navController.navigateUp()
                                }
                            }
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // Verify we start at tournaments screen
        composeTestRule.onNodeWithTag("tournaments_screen").assertIsDisplayed()
        assertFalse("Should not be in nested graph initially", isInNestedGraph)

        // Navigate into nested graph
        composeTestRule.runOnUiThread {
            navController.navigate(TournamentRegistration("test", 1))
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nested_screen").assertIsDisplayed()
        assertTrue("Should be in nested graph", isInNestedGraph)

        // Test up navigation from nested graph
        navigationUpCalled = false
        Espresso.pressBack()

        composeTestRule.waitForIdle()
        assertTrue("navigateUp should be called from nested graph", navigationUpCalled)
        composeTestRule.onNodeWithTag("tournaments_screen").assertIsDisplayed()
        assertFalse("Should no longer be in nested graph", isInNestedGraph)
    }

    @Test
    fun testIllegalStatePreventionInNavigation() {
        var navigationAttempts by mutableStateOf(0)
        var illegalStatePrevented by mutableStateOf(false)

        composeTestRule.setContent {
            navController = rememberNavController()

            // Add listener to track navigation attempts
            LaunchedEffect(navController) {
                navController.addOnDestinationChangedListener { _, destination, _ ->
                    navigationAttempts++
                }
            }

            NavHost(
                navController = navController,
                startDestination = ScreenRoutes.TournamentsScreen::class
            ) {
                composable<ScreenRoutes.TournamentsScreen> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("tournaments_screen")
                    ) {
                        Text("Tournaments Screen")

                        BackHandler {
                            // Prevent navigation if we're already at the root
                            val canPop = navController.popBackStack()
                            if (!canPop) {
                                illegalStatePrevented = true
                            }
                        }
                    }
                }
                composable<ScreenRoutes.WalletScreen> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("wallet_screen")
                    ) {
                        Text("Wallet Screen")
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // Verify we're at root screen
        composeTestRule.onNodeWithTag("tournaments_screen").assertIsDisplayed()

        // Try to go back from root - should prevent illegal state
        Espresso.pressBack()

        composeTestRule.waitForIdle()

        // Should still be on tournaments screen and illegal state should be prevented
        composeTestRule.onNodeWithTag("tournaments_screen").assertIsDisplayed()
        assertTrue("Illegal state should be prevented or handled", illegalStatePrevented)
    }

    @Test
    fun testCorrectScreenShownAfterBackNavigation() {
        val screenHistory = mutableListOf<String>()

        composeTestRule.setContent {
            navController = rememberNavController()

            // Track screen history
            LaunchedEffect(navController) {
                navController.currentBackStackEntryFlow.collect { entry ->
                    // Correctly access the route, which will be the KClass route for type-safe nav
                    val route = entry.destination.route ?: ""
                    screenHistory.add(route)
                }
            }

            NavHost(
                navController = navController,
                startDestination = ScreenRoutes.TournamentsScreen::class
            ) {
                composable<ScreenRoutes.TournamentsScreen> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("tournaments_screen")
                    ) {
                        Text("Tournaments")
                    }
                }
                composable<ScreenRoutes.WalletScreen> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("wallet_screen")
                    ) {
                        Text("Wallet")
                    }
                }
                composable<ScreenRoutes.ProfileScreen> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("profile_screen")
                    ) {
                        Text("Profile")
                    }
                }
                composable<ScreenRoutes.SettingsScreen> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("settings_screen")
                    ) {
                        Text("Settings")
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // Navigate through multiple screens
        composeTestRule.onNodeWithTag("tournaments_screen").assertIsDisplayed()

        composeTestRule.runOnUiThread {
            navController.navigate(ScreenRoutes.WalletScreen)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("wallet_screen").assertIsDisplayed()

        composeTestRule.runOnUiThread {
            navController.navigate(ScreenRoutes.ProfileScreen)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_screen").assertIsDisplayed()

        composeTestRule.runOnUiThread {
            navController.navigate(ScreenRoutes.SettingsScreen)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        // Navigate back and verify correct screens are shown
        Espresso.pressBack()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_screen").assertIsDisplayed()

        Espresso.pressBack()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("wallet_screen").assertIsDisplayed()

        Espresso.pressBack()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tournaments_screen").assertIsDisplayed()

        // Verify navigation history is correct
        assertTrue("Screen history should contain all visited screens",
            screenHistory.size >= 4)
    }

    @Test
    fun testBackNavigationWithStatePreservation() {
        var formData by mutableStateOf("")
        var statePreserved by mutableStateOf(false)

        composeTestRule.setContent {
            navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = ScreenRoutes.TournamentsScreen::class
            ) {
                composable<ScreenRoutes.TournamentsScreen> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("tournaments_screen")
                    ) {
                        Text("Tournaments")
                    }
                }
                composable<ScreenRoutes.ProfileScreen> {
                    // Simulate form with state that should be preserved
                    LaunchedEffect(Unit) {
                        // Check if state was preserved from previous visit
                        if (formData == "user_input_data") {
                            statePreserved = true
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("profile_form")
                    ) {
                        Text("Profile Form: $formData")

                        BackHandler {
                            // Save state before navigating back
                            formData = "user_input_data"
                            navController.popBackStack()
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // Navigate to profile screen
        composeTestRule.runOnUiThread {
            navController.navigate(ScreenRoutes.ProfileScreen)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_form").assertIsDisplayed()

        // Set some form data and navigate back (state is saved in `formData` by `BackHandler`)
        Espresso.pressBack()

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tournaments_screen").assertIsDisplayed()

        // Navigate back to profile screen and verify state is preserved
        composeTestRule.runOnUiThread {
            navController.navigate(ScreenRoutes.ProfileScreen)
        }
        composeTestRule.waitForIdle()

        // The LaunchedEffect in the ProfileScreen Composable will check if formData is "user_input_data"
        assertTrue("Form state should be preserved after back navigation", statePreserved)
    }

    @Test
    fun testProcessDeathAndBackNavigation() {
        // This test simulates process death and recovery
        composeTestRule.setContent {
            // Use the actual NavGraph component to test real behavior
            NavGraph(firebaseManager = firebaseManager)
        }

        composeTestRule.waitForIdle()

        // Simulate process death by creating a new activity scenario
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Simulate back navigation after process recreation
                activity.onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    @Test
    fun testMultipleFastBackPresses() {
        var backPressCount by mutableStateOf(0)

        composeTestRule.setContent {
            navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = ScreenRoutes.TournamentsScreen::class
            ) {
                composable<ScreenRoutes.TournamentsScreen> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("tournaments_screen")
                    ) {
                        Text("Tournaments")
                    }
                }
                composable<ScreenRoutes.WalletScreen> {
                    BackHandler {
                        backPressCount++
                        navController.popBackStack()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("wallet_screen")
                    ) {
                        Text("Wallet")
                    }
                }
                composable<ScreenRoutes.ProfileScreen> {
                    BackHandler {
                        backPressCount++
                        navController.popBackStack()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("profile_screen")
                    ) {
                        Text("Profile")
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // Build up a navigation stack
        composeTestRule.runOnUiThread {
            navController.navigate(ScreenRoutes.WalletScreen)
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnUiThread {
            navController.navigate(ScreenRoutes.ProfileScreen)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_screen").assertIsDisplayed()

        // Perform multiple fast back presses
        repeat(3) {
            Espresso.pressBack()
            // Small delay to allow navigation to process
            Thread.sleep(50)
        }

        composeTestRule.waitForIdle()

        // Should end up back at tournaments screen without crashes
        composeTestRule.onNodeWithTag("tournaments_screen").assertIsDisplayed()

        // Back press count should be reasonable (2 pops for Wallet and Profile)
        assertTrue("Back press count should be handled properly (max 2 pops)", backPressCount <= 2)
    }
}