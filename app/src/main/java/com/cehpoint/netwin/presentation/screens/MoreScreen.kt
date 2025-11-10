package com.cehpoint.netwin.presentation.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.cehpoint.netwin.R
import com.cehpoint.netwin.domain.model.User
import com.cehpoint.netwin.presentation.navigation.ScreenRoutes
import com.cehpoint.netwin.presentation.viewmodels.MoreViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreenUI(navController: NavController, viewModel: MoreViewModel = hiltViewModel()) {
    val user by viewModel.user.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    // New flow for showing report status
    val reportStatus by viewModel.reportStatus.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog states
    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showReportIssueDialog by remember { mutableStateOf(false) }

    // Effect to show Snackbar when reportStatus changes
    LaunchedEffect(reportStatus) {
        reportStatus?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) } // Host for showing success/error messages
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Image
            Image(
                painter = painterResource(id = R.drawable.login_screen),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Dark overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            )

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.Cyan)
                    }
                }

                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = error ?: "Unknown error",
                            color = Color.Red
                        )
                    }
                }

                user != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Profile Section
                        ProfileSection(
                            user = user!!,
                            onEditProfile = { navController.navigate(ScreenRoutes.ProfileScreen) }
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Account & Settings
                        SectionTitle("Account & Settings")
                        MoreItem(
                            icon = Icons.Outlined.Person,
                            title = "Edit Profile",
                            onClick = { navController.navigate(ScreenRoutes.ProfileScreen) }
                        )
                        MoreItem(
                            icon = Icons.Outlined.PrivacyTip,
                            title = "KYC Verification",
                            onClick = { navController.navigate(ScreenRoutes.KycScreen) }
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Support & Help
                        SectionTitle("Support & Help")
                        MoreItem(
                            icon = Icons.Outlined.Support,
                            title = "Contact Support",
                            onClick = { /* TODO: Add support email */ }
                        )
                        MoreItem(
                            icon = Icons.Outlined.Report,
                            title = "Report an Issue",
                            onClick = { showReportIssueDialog = true }
                        )
                        MoreItem(
                            icon = Icons.Outlined.Description,
                            title = "Terms & Conditions",
                            onClick = { showTermsDialog = true }
                        )
                        MoreItem(
                            icon = Icons.Outlined.PrivacyTip,
                            title = "Privacy Policy",
                            onClick = { showPrivacyDialog = true }
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Social & Community
                        SectionTitle("Social & Community")
                        MoreItem(
                            icon = Icons.Outlined.PersonAdd,
                            title = "Invite Friends",
                            onClick = {
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Join me on NetWin! Download the app now."
                                    )
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                            }
                        )
                        MoreItem(
                            icon = Icons.Outlined.Share,
                            title = "Share App",
                            onClick = {
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Check out NetWin - The Ultimate eSports & Fantasy Gaming Platform!"
                                    )
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                            }
                        )
                        MoreItem(
                            icon = Icons.Outlined.Forum,
                            title = "Join Discord",
                            onClick = {
                                val intent =
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/netwin"))
                                context.startActivity(intent)
                            }
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // App Information
                        SectionTitle("App Information")
                        MoreItem(
                            icon = Icons.Outlined.Info,
                            title = "About Us",
                            onClick = { showAboutDialog = true }
                        )
                        MoreItem(
                            icon = Icons.Outlined.Star,
                            title = "Rate App",
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("market://details?id=${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        )
                        MoreItem(
                            icon = Icons.AutoMirrored.Outlined.Logout,
                            title = "Logout",
                            onClick = { viewModel.logout() }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Version 1.0.0",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }

        // =====================
        // Dialogs
        // =====================
        if (showTermsDialog) TermsDialog { showTermsDialog = false }
        if (showPrivacyDialog) PrivacyDialog { showPrivacyDialog = false }
        if (showAboutDialog) AboutUsDialog { showAboutDialog = false }

        // Report Issue Dialog
        if (showReportIssueDialog) ReportIssueDialog(
            onClose = { showReportIssueDialog = false },
            onSubmit = { report, imageUrl ->
                viewModel.submitIssueReport(report, imageUrl)
                showReportIssueDialog = false
            }
        )
    }
}

// ==============================================================================
// ReportIssueDialog, TermsDialog, PrivacyDialog, AboutUsDialog,
// ProfileSection, SectionTitle, MoreItem Composable functions follow below...
// (No changes to the content of these functions from the previous response)
// ==============================================================================

@Composable
private fun ReportIssueDialog(
    onClose: () -> Unit,
    onSubmit: (report: String, imageUrl: String?) -> Unit
) {
    var issueReport by remember { mutableStateOf("") }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val isSubmitEnabled = issueReport.isNotBlank()

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                text = "Report an Issue",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = issueReport,
                    onValueChange = { issueReport = it },
                    label = { Text("Describe the issue in detail", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.DarkGray,
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.5f),
                        focusedBorderColor = Color.Cyan,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedLabelColor = Color.Cyan,
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp),
                    maxLines = 10
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Attachment Button (Simulated for this pure Compose environment)
                Button(
                    onClick = {
                        // In a real app, this would trigger an ActivityResultLauncher
                        // for picking an image, and you would handle the result
                        // and update attachedImageUri.
                        android.widget.Toast.makeText(context, "Image Picker Placeholder: Implement Activity Result API for file picking.", android.widget.Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00CED1).copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AttachFile,
                        contentDescription = "Attach Image",
                        tint = Color.Cyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (attachedImageUri == null) "Attach Screenshot (Optional)" else "Image Attached (Simulated)",
                        color = Color.Cyan
                    )
                }

                if (attachedImageUri != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Attachment: ${attachedImageUri!!.lastPathSegment}", color = Color.Gray, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(issueReport, attachedImageUri?.toString())
                },
                enabled = isSubmitEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
            ) {
                Text("Submit Report", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Cancel", color = Color.White) }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}

@Composable
private fun TermsDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Close", color = Color.Cyan) } },
        title = {
            Text(
                text = "NetWin Terms & Conditions",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .height(350.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = """
Welcome to NetWin, your premier eSports and fantasy gaming destination!

By accessing or using NetWin, you agree to the following Terms & Conditions. Please read carefully before participating.

1. **Eligibility**
    - You must be 18 years or older to play paid contests.
    - Participation is subject to local laws regarding online gaming and fantasy sports.

2. **Account & Verification**
    - Users must register with valid credentials.
    - NetWin reserves the right to verify identity through KYC before withdrawals or participation in cash contests.

3. **Gameplay Rules**
    - NetWin hosts fantasy and eSports-based contests.
    - All team selections and outcomes are based on official game statistics and fair play.
    - Any use of unauthorized tools, bots, or manipulation will result in immediate suspension.

4. **Deposits & Withdrawals**
    - Deposits can be made through authorized payment gateways only.
    - Withdrawals are processed after KYC verification within 24–72 hours.

5. **Fair Play & Conduct**
    - Users must maintain integrity and refrain from collusion, cheating, or multiple fake accounts.
    - NetWin reserves the right to suspend or terminate accounts violating fair play policies.

6. **Liability**
    - NetWin shall not be liable for losses due to gameplay, connectivity issues, or technical disruptions.
    - All decisions regarding winnings and disputes are final.

7. **Responsible Gaming**
    - NetWin encourages responsible play. Play within limits.

Thank you for being part of NetWin — where skill meets strategy!
                    """.trimIndent(),
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}

@Composable
private fun PrivacyDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Close", color = Color.Cyan) } },
        title = {
            Text(
                text = "NetWin Privacy Policy",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .height(350.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = """
NetWin values your privacy and is committed to protecting your personal data. 

1. **Information We Collect**
    - Personal details, KYC verification, and payment data.
    - Gameplay analytics and device details.

2. **Use of Information**
    - To process payments and personalize your experience.
    - To communicate promotions and security updates.

3. **Data Protection**
    - All sensitive data is encrypted and secured.
    - NetWin never sells or misuses user data.

4. **Third Parties**
    - Data shared only with verified services (e.g., payment gateways).

5. **User Rights**
    - You can update or delete data anytime via settings or by emailing **support@netwin.com**.

NetWin believes in trust, transparency, and responsible gaming.
                    """.trimIndent(),
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}

@Composable
private fun AboutUsDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Close", color = Color.Cyan) } },
        title = {
            Text(
                text = "About NetWin",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .height(350.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = """
Welcome to **NetWin** — India's fastest-growing eSports and fantasy gaming platform!

At NetWin, our mission is to redefine competitive gaming by combining the thrill of fantasy strategy with the excitement of live eSports tournaments.

🎮 **What We Do**
    - Host fantasy contests for popular games like BGMI, Valorant, Free Fire, and more.
    - Enable players to create teams, compete in real-time, and win exciting rewards.

💡 **Our Vision**
    - To empower gamers to turn their passion into potential.
    - To promote fair play, transparency, and responsible gaming across the eSports ecosystem.

🤝 **Our Promise**
    - 100% fair contests verified with real match data.
    - Safe and secure payment processes.
    - Dedicated 24/7 support to assist every player.

NetWin isn’t just a platform — it’s a gaming community built for skill, strategy, and victory.

Join us. Play smarter. Win bigger. 🏆
                    """.trimIndent(),
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}

@Composable
private fun ProfileSection(
    user: User,
    onEditProfile: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(onClick = onEditProfile),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E).copy(alpha = 0.8f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.username,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun MoreItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E).copy(alpha = 0.8f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.Cyan,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
