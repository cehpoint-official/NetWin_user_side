package com.cehpoint.netwin

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.cehpoint.netwin.data.remote.FirebaseManager
import com.cehpoint.netwin.presentation.navigation.NavGraph
import com.cehpoint.netwin.ui.theme.NetWinTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.compose.ui.Modifier
import com.cehpoint.netwin.presentation.viewmodels.AuthViewModel
import com.google.firebase.auth.FirebaseAuth

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var firebaseManager: FirebaseManager

    private val viewModel by viewModels<AuthViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            val shouldKeepSplash = !viewModel.isSplashShow.value
            Log.d("MainActivity", "Splash screen condition check: shouldKeepSplash = $shouldKeepSplash, isSplashShow = ${viewModel.isSplashShow.value}")
            shouldKeepSplash
        }

        Log.d("MainActivity", "=== MainActivity onCreate STARTED ===")
        Log.d("MainActivity", "MainActivity - savedInstanceState: $savedInstanceState")

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        super.onCreate(savedInstanceState)

        // Handle potential deep link immediately on app launch (intent is nullable here)
        handleIntent(intent)

        Log.d("MainActivity", "MainActivity - super.onCreate completed")
        Log.d("MainActivity", "MainActivity - firebaseManager: $firebaseManager")

        setContent {
            Log.d("MainActivity", "MainActivity - setContent started")
            NetWinTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPaddin ->
                    NavGraph(firebaseManager = firebaseManager)
                }
            }
            Log.d("MainActivity", "MainActivity - setContent completed")
        }

        Log.d("MainActivity", "=== MainActivity onCreate COMPLETED ===")
    }

    // ⭐ CORRECTED IMPLEMENTATION for newer ComponentActivity:
    // Uses non-nullable Intent and still correctly routes to the nullable handler.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Passes the non-nullable intent to handleIntent, which can receive Intent?
        // This is the primary trigger for automatic navigation when the app is resumed by the link.
        handleIntent(intent)
    }

    /**
     * Checks the incoming intent for a deep link (like the Firebase verification link).
     * This function contains the core logic for automatically moving the user forward.
     */
    private fun handleIntent(intent: Intent?) {
        // Check if this intent is a deep link
        if (intent?.action == Intent.ACTION_VIEW) {
            val deepLinkUri = intent.data

            // Check if the URI matches the Firebase action code URL pattern
            // This pattern is used for email verification links
            if (deepLinkUri != null && deepLinkUri.lastPathSegment == "action") {
                Log.d("MainActivity", "Deep link intercepted: Firebase action code detected. Triggering automatic status reload.")

                val user = FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    // ⭐ AUTOMATIC NAVIGATION TRIGGER:
                    // Reloads the user's status, which VerificationPendingScreen observes.
                    viewModel.reloadUser()
                } else {
                    Log.w("MainActivity", "Deep link received but no current user found. Attempting to recheck auth state.")
                    viewModel.recheckAuthState()
                }
            } else {
                Log.d("MainActivity", "Intent received, but not a recognized deep link for verification.")
            }
        }
    }


    override fun onStart() {
        super.onStart()
        Log.d("MainActivity", "=== MainActivity onStart ===")
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "=== MainActivity onResume ===")
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "=== MainActivity onPause ===")
    }

    override fun onStop() {
        super.onStop()
        Log.d("MainActivity", "=== MainActivity onStop ===")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "=== MainActivity onDestroy ===")
    }
}