package com.cehpoint.netwin.presentation.navigation

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.cehpoint.netwin.data.remote.FirebaseManager
import com.cehpoint.netwin.presentation.screens.*
import com.cehpoint.netwin.presentation.viewmodels.AuthViewModel
import com.cehpoint.netwin.presentation.viewmodels.ProfileViewModel
import kotlinx.coroutines.delay

@Composable
fun NavGraph(firebaseManager: FirebaseManager) {
    Log.d("NavGraph", "=== NavGraph COMPOSABLE STARTED ===")

    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val isAuthenticated by authViewModel.isAuthenticated.collectAsState()
    val isAuthStateInitialized by authViewModel.isAuthStateInitialized.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val shouldRecheckProfile by profileViewModel.shouldRecheckProfile.collectAsState()

    Log.d("NavGraph", "NavGraph - isAuthenticated: $isAuthenticated")
    Log.d("NavGraph", "NavGraph - isAuthStateInitialized: $isAuthStateInitialized")
    Log.d("NavGraph", "NavGraph - shouldRecheckProfile: $shouldRecheckProfile")

    // Simple state management
    var profileComplete by rememberSaveable { mutableStateOf<Boolean?>(null) }
    // NEW STATE: Check if user is authenticated AND verified
    val isVerified = currentUser?.isEmailVerified ?: false

    Log.d("NavGraph", "NavGraph - profileComplete: $profileComplete")

    val items = listOf(
        bottomNavigationItem(name = "Tournaments", icon = Icons.Outlined.EmojiEvents),
        bottomNavigationItem(name = "My Tournaments", icon = Icons.Outlined.SportsCricket),
        bottomNavigationItem(name = "Wallet", icon = Icons.Outlined.AccountBalanceWallet),
        bottomNavigationItem(name = "Alerts", icon = Icons.Outlined.AddAlert),
        bottomNavigationItem(name = "More", icon = Icons.Outlined.Menu)
    )

    val currentDestinationAsState = navController.currentBackStackEntryAsState()
    // The current destination route is often retrieved from the destination itself for string routes
    val currentDestination = currentDestinationAsState.value?.destination?.route
    val shouldShowBottomBar = remember { mutableStateOf(true) }

    // Debug logging for route detection
    LaunchedEffect(currentDestination) {
        Log.d("NavGraph", "Current destination: $currentDestination")
    }

    // ⭐ Define canonical route names for reliable comparison
    val VERIFICATION_PENDING_ROUTE = "com.cehpoint.netwin.presentation.navigation.ScreenRoutes.VerificationPendingScreen"
    val LOGIN_ROUTE = "com.cehpoint.netwin.presentation.navigation.ScreenRoutes.LoginScreen"
    val REGISTER_ROUTE = "com.cehpoint.netwin.presentation.navigation.ScreenRoutes.RegisterScreen"


    // Single LaunchedEffect to handle all auth and profile logic
    LaunchedEffect(isAuthenticated, isAuthStateInitialized, isVerified, shouldRecheckProfile) {
        Log.d("NavGraph", "=== LaunchedEffect TRIGGERED ===")
        Log.d("NavGraph", "LaunchedEffect - isAuthenticated: $isAuthenticated")
        Log.d("NavGraph", "LaunchedEffect - isAuthStateInitialized: $isAuthStateInitialized")
        Log.d("NavGraph", "LaunchedEffect - isVerified: $isVerified")
        Log.d("NavGraph", "LaunchedEffect - shouldRecheckProfile: $shouldRecheckProfile")

        if (!isAuthStateInitialized) {
            Log.d("NavGraph", "LaunchedEffect - Auth state not initialized yet, waiting...")
            return@LaunchedEffect
        }

        if (!isAuthenticated) {
            Log.d("NavGraph", "LaunchedEffect - User not authenticated, resetting profile complete")
            profileComplete = null

            // ⭐ FIX APPLIED HERE:
            // Check if the current destination is NOT one of the explicit Auth Screens (Login, Register, VerificationPending)
            val isCurrentlyOnAuthScreen = currentDestination?.contains("LoginScreen") == true ||
                    currentDestination?.contains("RegisterScreen") == true ||
                    currentDestination?.contains("VerificationPendingScreen") == true

            if (currentDestination != null && !isCurrentlyOnAuthScreen) {

                // If the user is unauthenticated and we are on a non-Auth screen, navigate to the Auth graph.
                navController.navigate(SubNavigation.AuthNavGraph) {

                    // Use SubNavigation.AuthNavGraph as the anchor type for popUpTo
                    popUpTo(SubNavigation.AuthNavGraph) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
            return@LaunchedEffect
        }

        // ⭐ FIX: If authenticated but NOT verified, navigate to pending screen
        // Use the defined canonical route names for comparison
        if (isAuthenticated && !isVerified &&
            currentDestination != VERIFICATION_PENDING_ROUTE) {

            Log.d("NavGraph", "LaunchedEffect - User authenticated but NOT verified, navigating to VerificationPendingScreen")
            navController.navigate(ScreenRoutes.VerificationPendingScreen) {
                // FIX: Use the serializable object directly for popUpTo
                popUpTo(ScreenRoutes.LoginScreen) { inclusive = false }
                launchSingleTop = true
            }
            return@LaunchedEffect
        }

        // If authenticated AND verified, proceed to check profile completeness
        if (isAuthenticated && isVerified) {
            if (profileComplete == null || shouldRecheckProfile) {
                Log.d("NavGraph", "LaunchedEffect - Checking profile completeness")

                delay(100)

                profileViewModel.isProfileCompleteAsync { complete ->
                    profileComplete = complete
                    Log.d("NavGraph", "LaunchedEffect - Profile completeness result: $complete")

                    if (complete == false) {
                        Log.d("NavGraph", "LaunchedEffect - Profile incomplete, navigating to ProfileSetupScreen")
                        navController.navigate(ScreenRoutes.ProfileSetupScreen) {
                            // FIX: Use the serializable object directly for popUpTo
                            popUpTo(SubNavigation.HomeNavGraph) { inclusive = true }
                            launchSingleTop = true
                        }
                    } else if (currentDestination == VERIFICATION_PENDING_ROUTE ||
                        currentDestination == LOGIN_ROUTE ||
                        currentDestination == REGISTER_ROUTE) {
                        // Profile is complete and we are stuck on an Auth screen, navigate home.
                        navController.navigate(SubNavigation.HomeNavGraph) {
                            // FIX: Use the serializable object directly for popUpTo
                            popUpTo(SubNavigation.AuthNavGraph) { inclusive = true }
                            launchSingleTop = true
                        }
                    }

                    if (shouldRecheckProfile) {
                        Log.d("NavGraph", "LaunchedEffect - Resetting recheck profile flag")
                        profileViewModel.resetRecheckProfile()
                    }
                }
            } else {
                Log.d("NavGraph", "LaunchedEffect - Profile already checked, no need to recheck")
            }
        }
    }

    // Handle bottom bar visibility
    LaunchedEffect(currentDestination, isVerified) {
        val hideBottomBarRoutes = listOf(
            "TournamentDetails",
            "ProfileSetupScreen",
            "KycScreen",
            "VictoryPass",
            // Use the simple name string for comparison here, which is usually included in the route string
            "VerificationPendingScreen"
        )
        shouldShowBottomBar.value = hideBottomBarRoutes.none { route ->
            currentDestination?.contains(route) == true
        } && isAuthenticated && isVerified
    }

    Box {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                Log.d("NavGraph", "Bottom bar visibility check:")
                Log.d("NavGraph", "  - shouldShowBottomBar: ${shouldShowBottomBar.value}")
                Log.d("NavGraph", "  - isAuthenticated: $isAuthenticated")
                Log.d("NavGraph", "  - isVerified: $isVerified")
                Log.d("NavGraph", "  - Will show bottom bar: ${shouldShowBottomBar.value && isAuthenticated && isVerified}")

                if (shouldShowBottomBar.value && isAuthenticated && isVerified) {
                    NavigationBar(
                        containerColor = Color.Black,
                        tonalElevation = 0.dp,
                        windowInsets = NavigationBarDefaults.windowInsets,
                        modifier = Modifier
                            .background(Color.Black)
                            .height(70.dp)
                    ) {
                        items.forEachIndexed { index, bottomNavigationItem ->
                            val isSelected = when (index) {
                                // Checking containment is fine for string-based route matching
                                0 -> currentDestination?.contains("TournamentsScreen") == true && !currentDestination.contains("MyTournamentsScreen")
                                1 -> currentDestination?.contains("MyTournamentsScreen") == true
                                2 -> currentDestination?.contains("WalletScreen") == true
                                3 -> currentDestination?.contains("AlertsScreen") == true
                                4 -> currentDestination?.contains("MoreScreen") == true
                                else -> false
                            }

                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    when (index) {
                                        // FIX: Use the serializable object directly for popUpTo
                                        0 -> navController.navigate(ScreenRoutes.TournamentsScreen) {
                                            popUpTo(ScreenRoutes.TournamentsScreen) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                        1 -> navController.navigate(ScreenRoutes.MyTournamentsScreen) {
                                            popUpTo(ScreenRoutes.MyTournamentsScreen) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                        2 -> navController.navigate(ScreenRoutes.WalletScreen) {
                                            popUpTo(ScreenRoutes.WalletScreen) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                        3 -> navController.navigate(ScreenRoutes.AlertsScreen) {
                                            popUpTo(ScreenRoutes.AlertsScreen) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                        4 -> navController.navigate(ScreenRoutes.MoreScreen) {
                                            popUpTo(ScreenRoutes.MoreScreen) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                icon = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(70.dp)
                                    ) {
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(40.dp)
                                                    .align(Alignment.TopCenter)
                                                    .background(
                                                        brush = Brush.verticalGradient(
                                                            colors = listOf(
                                                                Color.Cyan.copy(alpha = 0.5f),
                                                                Color.Cyan.copy(alpha = 0.2f),
                                                                Color.Transparent
                                                            )
                                                        )
                                                    )
                                            )


                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopCenter)
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .background(
                                                        color = Color.Cyan,
                                                        shape = RoundedCornerShape(2.dp)
                                                    )
                                            )
                                        }

                                        Icon(
                                            imageVector = bottomNavigationItem.icon,
                                            contentDescription = bottomNavigationItem.name,
                                            tint = if (isSelected) Color.Cyan else Color.White,
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(24.dp)
                                        )
                                    }
                                },

                                alwaysShowLabel = false,

                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            if (!isAuthStateInitialized) {
                // Loading state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.Cyan,
                        modifier = Modifier.size(48.dp)
                    )
                }
            } else {
                val startDestination = when {
                    !isAuthenticated -> {
                        Log.d("NavGraph", "NavHost - Start destination: AuthNavGraph (not authenticated)")
                        SubNavigation.AuthNavGraph
                    }
                    else -> {
                        Log.d("NavGraph", "NavHost - Start destination: HomeNavGraph (authenticated)")
                        SubNavigation.HomeNavGraph
                    }
                }

                Log.d("NavGraph", "NavHost - Final start destination: $startDestination")

                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    Modifier.padding(innerPadding)
                ) {
                    navigation<SubNavigation.AuthNavGraph>(startDestination = ScreenRoutes.LoginScreen) {
                        composable<ScreenRoutes.LoginScreen> {
                            LoginScreenUI(
                                navController = navController,
                                firebaseManager = firebaseManager,
                            )
                        }
                        composable<ScreenRoutes.RegisterScreen> {
                            RegisterScreenUI(
                                navController = navController,
                            )
                        }
                        // NEW ROUTE: Verification Pending Screen
                        composable<ScreenRoutes.VerificationPendingScreen> {
                            VerificationPendingScreen(navController = navController)
                        }
                    }

                    navigation<SubNavigation.HomeNavGraph>(startDestination = ScreenRoutes.TournamentsScreen) {
                        composable<ScreenRoutes.TournamentsScreen> {
                            LegacyTournamentsScreenUI(
                                navController = navController,
                            )
                        }

                        composable<ScreenRoutes.MyTournamentsScreen> {
                            MyTournamentsScreen(
                                onNavigateToTournaments = {
                                    // FIX: Use the serializable object directly for popUpTo
                                    navController.navigate(ScreenRoutes.TournamentsScreen) {
                                        popUpTo(ScreenRoutes.TournamentsScreen) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToVictoryPass = { tournamentId ->
                                    navController.navigate(Screen.VictoryPass.createRoute(tournamentId)) {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToTournamentDetails = { tournamentId ->
                                    navController.navigate(Screen.TournamentDetails.createRoute(tournamentId)) {
                                        launchSingleTop = true
                                    }
                                },
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable<ScreenRoutes.WalletScreen> {
                            WalletScreen(navController = navController)
                        }
                        composable<ScreenRoutes.TransactionHistoryScreen> {
                            TransactionHistoryScreen()
                        }
                        composable<ScreenRoutes.PaymentProofScreen> { backStackEntry ->
                            val paymentProofScreen: ScreenRoutes.PaymentProofScreen = backStackEntry.toRoute()
                            EnhancedPaymentProofScreen(
                                currency = paymentProofScreen.currency,
                                amount = paymentProofScreen.amount,
                                paymentMethod = com.cehpoint.netwin.data.model.PaymentMethod.UPI,
                                upiAppPackage = paymentProofScreen.upiAppPackage,
                                onSubmitProof = { proof ->
                                    navController.popBackStack()
                                },
                                onDismiss = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable<ScreenRoutes.AlertsScreen> {
                            AlertsScreenUI(navController = navController)
                        }
                        composable<ScreenRoutes.MoreScreen> {
                            MoreScreenUI(navController = navController)
                        }
                        composable<ScreenRoutes.ProfileScreen> {
                            ProfileScreenUI(navController = navController)
                        }
                        composable<ScreenRoutes.KycScreen> {
                            KycScreen(navController = navController)
                        }
                        composable<ScreenRoutes.ProfileSetupScreen> {
                            ProfileSetupScreenUI(navController = navController)
                        }

                        // Tournament Details Screen
                        composable(
                            route = Screen.TournamentDetails.route,
                            arguments = listOf(
                                navArgument("tournamentId") {
                                    type = androidx.navigation.NavType.StringType
                                }
                            )
                        ) { backStackEntry ->
                            val tournamentId = backStackEntry.arguments?.getString("tournamentId")
                            if (tournamentId != null) {
                                TournamentDetailsScreenUI(
                                    tournamentId = tournamentId,
                                    navController = navController
                                )
                            }
                        }

                        // Victory Pass Screen
                        composable(
                            route = Screen.VictoryPass.route,
                            arguments = listOf(navArgument("tournamentId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val tournamentId = backStackEntry.arguments?.getString("tournamentId") ?: return@composable
                            VictoryPassScreen(
                                tournamentId = tournamentId,
                                onBackClick = {
                                    // FIX: Use the serializable object directly for popUpTo
                                    navController.navigate(ScreenRoutes.TournamentsScreen) {
                                        popUpTo(ScreenRoutes.TournamentsScreen) {
                                            inclusive = true
                                        }
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToRules = { /* TODO: Navigate to rules */ }
                            )
                        }


                        // Add this nested navigation inside your HomeNavGraph
                        navigation<SubNavigation.RegistrationNavGraph>(
                            startDestination = ScreenRoutes.TournamentRegistration("", 1)

                        ) {
                            composable<ScreenRoutes.TournamentRegistration> { backStackEntry ->
                                val args = backStackEntry.toRoute<ScreenRoutes.TournamentRegistration>()
                                RegistrationFlowScreen(
                                    tournamentId = args.tournamentId,
                                    stepIndex = args.stepIndex,
                                    navController = navController
                                )
                            }
                        }

                    }
                }
            }
        }
    }
}

data class bottomNavigationItem(val name: String, val icon: ImageVector)
