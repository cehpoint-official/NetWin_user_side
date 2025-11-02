package com.cehpoint.netwin.presentation.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment // KEEP THIS IMPORT FOR FILE CREATION IN KYCSCREEN
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.cehpoint.netwin.R
import com.cehpoint.netwin.data.model.PendingDeposit
import com.cehpoint.netwin.payments.PaymentGatewayFactory
import com.cehpoint.netwin.payments.PaymentGatewayManager
import com.cehpoint.netwin.data.model.Transaction
import com.cehpoint.netwin.data.model.TransactionStatus
import com.cehpoint.netwin.data.model.TransactionType
import com.cehpoint.netwin.data.model.UserDetails
import com.cehpoint.netwin.data.model.WithdrawalRequest
import com.cehpoint.netwin.presentation.components.StatusChip
import com.cehpoint.netwin.presentation.components.statusBarPadding
import com.cehpoint.netwin.presentation.navigation.ScreenRoutes
import com.cehpoint.netwin.presentation.theme.NetwinTokens
import com.cehpoint.netwin.presentation.viewmodels.AuthViewModel
import com.cehpoint.netwin.presentation.viewmodels.WalletViewModel
import com.cehpoint.netwin.ui.theme.DarkBackground
import com.cehpoint.netwin.ui.theme.DarkCard
import com.cehpoint.netwin.ui.theme.DarkSurface
import com.cehpoint.netwin.ui.theme.ErrorRed
import com.cehpoint.netwin.ui.theme.NetWinCyan
import com.cehpoint.netwin.ui.theme.NetWinPink
import com.cehpoint.netwin.ui.theme.NetWinPurple
import com.cehpoint.netwin.ui.theme.SuccessGreen
import com.cehpoint.netwin.ui.theme.WarningYellow
import com.cehpoint.netwin.utils.NGNTransactionUtils
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    navController: NavController,
    walletViewModel: WalletViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val walletBalance by walletViewModel.walletBalance.collectAsState()
    val transactions by walletViewModel.transactions.collectAsState()
    val pendingDeposits by walletViewModel.pendingDeposits.collectAsState()
    val isLoading by walletViewModel.isLoading.collectAsState()
    val error by walletViewModel.error.collectAsState()

    val currentUser by authViewModel.currentUser.collectAsState()
    val isAuthenticated by authViewModel.isAuthenticated.collectAsState()
    val withdrawableBalance by walletViewModel.withdrawableBalance.collectAsState(initial = 0.0)
    val bonusBalance by walletViewModel.bonusBalance.collectAsState(initial = 0.0)
    val withdrawalRequests by walletViewModel.withdrawalRequests.collectAsState(initial = emptyList())

    var showAmountSheet by remember { mutableStateOf(false) }
    var selectedAmount by rememberSaveable { mutableStateOf(0) }
    var manualAmount by rememberSaveable { mutableStateOf("") }
    var showWithdrawDialog by rememberSaveable { mutableStateOf(false) }
    var showNGNDepositDialog by rememberSaveable { mutableStateOf(false) }

    // States for QR Code dialog
    var showQrCodeDialog by remember { mutableStateOf(false) }
    var amountToPay by rememberSaveable { mutableStateOf(0) }

    // States for Payment Proof Dialog
    var showProofDialog by remember { mutableStateOf(false) }
    var isSubmittingProof by remember { mutableStateOf(false) }


    val presetAmounts = listOf(100, 200, 500)
    val presetNGNAmounts = listOf(1000, 2000, 5000)
    val amountSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var withdrawSuccess by rememberSaveable { mutableStateOf(false) }

    // Get user country and currency
    var userCountry by rememberSaveable { mutableStateOf("IN") }
    var userCurrency by rememberSaveable { mutableStateOf("INR") }

    var kycStatus by rememberSaveable { mutableStateOf<String?>(null) }

    // State to hold the fetched user name
    var currentUserName by rememberSaveable { mutableStateOf<String>("Unknown User") }

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    LaunchedEffect(currentUser) {
        currentUser?.let { user ->
            try {
                val userDoc = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.uid)
                    .get()
                    .await()
                val country = userDoc.getString("country") ?: "India"
                userCountry = country
                // Handle both country names and codes
                userCurrency = when {
                    country.equals("Nigeria", ignoreCase = true) || country.equals("NG", ignoreCase = true) -> "NGN"
                    country.equals("India", ignoreCase = true) || country.equals("IN", ignoreCase = true) -> "INR"
                    else -> "INR" // Default to INR
                }
                kycStatus = userDoc.getString("kycStatus") ?: "pending"

                // FIX IMPLEMENTATION (Step 1)
                currentUserName = userDoc.getString("name")
                    ?: userDoc.getString("username")
                            ?: user.displayName
                            ?: user.email?.substringBefore("@")
                            ?: "Unknown User"
                // END FIX

            } catch (e: Exception) {
                userCountry = "India"
                userCurrency = "INR"
                kycStatus = "pending"
                currentUserName = "Unknown User"
            }
        }
    }

    // Load wallet data when screen is first displayed or when user changes
    LaunchedEffect(currentUser, isAuthenticated) {
        if (isAuthenticated && !isLoading) {
            val userId = currentUser?.uid
            if (userId != null) {
                Log.d("WalletScreen", "Loading wallet data with Firebase user ID: $userId")
                Log.d("WalletScreen", "User country: $userCountry, Currency: $userCurrency")
                walletViewModel.loadWalletData(userId)
                walletViewModel.loadWithdrawalRequests(userId)

                // Check/Create wallet document
                coroutineScope.launch {
                    try {
                        val walletDoc = FirebaseFirestore.getInstance()
                            .collection("wallets")
                            .document(userId)
                            .get()
                            .await()
                        if (!walletDoc.exists()) {
                            Log.d("WalletScreen", "Wallet document does not exist, creating one...")
                            val walletData = mapOf(
                                "balance" to 0.0,
                                "withdrawableBalance" to 0.0,
                                "bonusBalance" to 0.0
                            )
                            FirebaseFirestore.getInstance()
                                .collection("wallets")
                                .document(userId)
                                .set(walletData)
                                .await()
                            Log.d("WalletScreen", "Wallet document created successfully")
                        }
                    } catch (e: Exception) {
                        Log.e("WalletScreen", "Error checking/creating wallet document: ${e.message}")
                    }
                }
            } else {
                Log.d("WalletScreen", "User authenticated but Firebase session lost, getting user ID from DataStore")
                val dataStoreUserId = authViewModel.getUserIdFromDataStore()
                if (dataStoreUserId != null) {
                    Log.d("WalletScreen", "Loading wallet data with DataStore user ID: $dataStoreUserId")
                    walletViewModel.loadWalletData(dataStoreUserId)
                    walletViewModel.loadWithdrawalRequests(dataStoreUserId)
                } else {
                    Log.e("WalletScreen", "Failed to get user ID from DataStore")
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .statusBarPadding()
        ) {
            data class KycBannerInfo(
                val bannerText: String,
                val buttonText: String?,
                val bannerColor: Color,
                val tintColor: Color
            )
            // == DYNAMIC KYC BANNER ==
            val kycStatusLower = kycStatus?.lowercase()
            if (kycStatusLower != "approved") {
                val (bannerText, buttonText, bannerColor, tintColor) = when (kycStatusLower) {
                    "pending" -> KycBannerInfo(
                        bannerText = "Your KYC application is pending review. Withdrawal is restricted.",
                        buttonText = null, // No button, just info
                        bannerColor = Color(0xFFFFF8E1), // WarningYellow light
                        tintColor = Color(0xFFF57C00)  // WarningYellow dark
                    )
                    "rejected" -> KycBannerInfo(
                        bannerText = "Your KYC was rejected. Please resubmit for withdrawal access.",
                        buttonText = "Resubmit",
                        bannerColor = Color(0xFFFDE0E0), // ErrorRed light
                        tintColor = Color(0xFFD32F2F)  // ErrorRed dark
                    )
                    else -> KycBannerInfo( // This covers null or any other status
                        bannerText = "Complete KYC to enable withdrawals.",
                        buttonText = "Start KYC",
                        bannerColor = Color(0xFFFFF8E1), // WarningYellow light
                        tintColor = Color(0xFFF57C00)  // WarningYellow dark
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bannerColor, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "KYC Status",
                            tint = tintColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            bannerText,
                            color = tintColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        buttonText?.let {
                            TextButton(
                                onClick = { navController.navigate(ScreenRoutes.KycScreen) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(it, color = tintColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = tintColor.copy(alpha = 0.3f))
            }
            // == END OF DYNAMIC KYC BANNER ==

            // Improved Top Bar
            ImprovedWalletTopBar(totalBalance = walletBalance, currency = userCurrency)

            if (!isAuthenticated) {
                // Not Authenticated State
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Please log in to view wallet",
                            color = Color.Gray,
                            modifier = Modifier.padding(16.dp)
                        )
                        Button(
                            onClick = { /* Navigate to login */ },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00BCD4)
                            )
                        ) {
                            Text("Login", color = Color.Black)
                        }
                    }
                }
            } else if (isLoading) {
                // Loading State
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF00BCD4))
                }
            } else if (error != null) {
                // Error State
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = error ?: "Unknown error",
                            color = Color.Red,
                            modifier = Modifier.padding(16.dp)
                        )
                        Button(
                            onClick = {
                                walletViewModel.clearError()
                                if (isAuthenticated && currentUser != null) {
                                    walletViewModel.loadWalletData(currentUser!!.uid)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00BCD4)
                            )
                        ) {
                            Text("Retry", color = Color.Black)
                        }
                    }
                }
            } else {
                // Main Content
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp), // Space for bottom nav
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Balance Card
                    item {
                        EnhancedBalanceCard(
                            totalBalance = walletBalance,
                            withdrawableBalance = withdrawableBalance,
                            currency = userCurrency,
                            onWithdrawClick = { showWithdrawDialog = true },
                            enabled = (kycStatus?.lowercase() == "approved")
                        )
                    }

                    // Quick Actions
                    item {
                        // --- KYC LOGIC FIX ---
                        val isKycApproved = (kycStatus?.lowercase() == "approved")
                        EnhancedQuickActions(
                            onDepositClick = {
                                if (userCountry.equals("Nigeria", ignoreCase = true) || userCountry.equals("NG", ignoreCase = true)) {
                                    showNGNDepositDialog = true
                                } else {
                                    showAmountSheet = true
                                }
                            },
                            onWithdrawClick = { showWithdrawDialog = true },
                            // Deposit is ALWAYS enabled
                            isDepositEnabled = true,
                            // Withdrawal requires KYC approval
                            isWithdrawEnabled = isKycApproved,
                        )
                        // --- END KYC LOGIC FIX ---
                    }

                    // Pending Deposits
                    if (pendingDeposits.isNotEmpty()) {
                        item {
                            Text(
                                text = "Pending Deposits",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        items(pendingDeposits) { deposit ->
                            PendingDepositItem(deposit, userCurrency)
                        }
                    }

                    // Withdrawal Requests
                    if (withdrawalRequests.isNotEmpty()) {
                        item {
                            Text(
                                text = "Withdrawal Requests",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        items(withdrawalRequests) { request ->
                            WithdrawalRequestItem(request = request, currency = userCurrency)
                        }
                    }

                    // Transaction History
                    item {
                        Text(
                            text = "Transaction History",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    if (transactions.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No transactions yet",
                                    color = Color.Gray,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    } else {
                        items(transactions) { transaction ->
                            EnhancedTransactionItem(transaction, userCurrency)
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS & BOTTOM SHEETS ---

    // 1. Indian UPI Amount Selection Sheet
    if (showAmountSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAmountSheet = false },
            sheetState = amountSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Select Amount", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    presetAmounts.forEach { amt ->
                        Button(
                            onClick = {
                                selectedAmount = amt
                                manualAmount = ""
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedAmount == amt) Color(0xFF00BCD4) else Color(0xFF2A2A2A)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("₹$amt", color = if (selectedAmount == amt) Color.Black else Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Or enter custom amount", color = Color.Gray, fontSize = 14.sp)
                OutlinedTextField(
                    value = manualAmount,
                    onValueChange = {
                        manualAmount = it.filter { c -> c.isDigit() }
                        selectedAmount = 0
                    },
                    label = { Text("Amount (₹)", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00BCD4),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFF00BCD4),
                        focusedLabelColor = Color(0xFF00BCD4),
                        unfocusedLabelColor = Color.Gray
                    )
                )
                Spacer(modifier = Modifier.height(24.dp))

                val currentAmount = if (selectedAmount > 0) selectedAmount else manualAmount.toIntOrNull() ?: 0
                val isAmountValid = currentAmount > 0

                Button(
                    onClick = {
                        val amount = if (selectedAmount > 0) selectedAmount else manualAmount.toIntOrNull() ?: 0
                        if (amount > 0) {
                            amountToPay = amount // Store the amount
                            coroutineScope.launch {
                                showAmountSheet = false
                            }
                            showQrCodeDialog = true // Open the QR dialog
                        } else {
                            Log.e("WalletScreen", "Invalid payment amount: $amount")
                        }
                    },
                    enabled = isAmountValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAmountValid) Color(0xFF00BCD4) else Color.Gray,
                        disabledContainerColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isAmountValid) "Proceed to Pay" else "Enter Amount",
                        color = if (isAmountValid) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { showAmountSheet = false }) {
                    Text("Cancel", color = Color(0xFF00BCD4))
                }
            }
        }
    }

    // 2. QR Code Payment Dialog
    if (showQrCodeDialog) {
        QrCodePaymentDialog(
            amount = amountToPay,
            onDismiss = { showQrCodeDialog = false },
            onPaid = {
                showQrCodeDialog = false
                showProofDialog = true // Open the NEW proof dialog
            }
        )
    }

    // 3. Payment Proof Submission Dialog
    if (showProofDialog) {
        val scope = rememberCoroutineScope()
        val userId = currentUser?.uid
        val userEmail = currentUser?.email ?: ""
        val userNameToUse = currentUserName

        PaymentProofDialog(
            amount = amountToPay,
            isLoading = isSubmittingProof,
            onDismiss = { showProofDialog = false },
            onSubmit = { refId, uri ->
                if (userId == null) {
                    scope.launch { snackbarHostState.showSnackbar("Error: User not logged in.") }
                    return@PaymentProofDialog
                }
                isSubmittingProof = true
                scope.launch {
                    try {
                        // 1. Upload Screenshot to Firebase Storage
                        val storageRef = FirebaseStorage.getInstance().reference
                        val imageFileName = "deposits/${userId}/${UUID.randomUUID()}"
                        val imageRef = storageRef.child(imageFileName)
                        val uploadTask = imageRef.putFile(uri).await()
                        val downloadUrl = uploadTask.storage.downloadUrl.await().toString()

                        // 2. Create PendingDeposit map (UPDATED WITH userEmail and userName)
                        val pendingDepositData = mapOf(
                            "userId" to userId,
                            "amount" to amountToPay.toDouble(),
                            "currency" to userCurrency,
                            "upiRefId" to refId,
                            "screenshotUrl" to downloadUrl,
                            "status" to "PENDING", // Status for admin approval
                            "createdAt" to Timestamp.now(),
                            "userUpiId" to "", // Add other fields as needed
                            "userEmail" to userEmail,
                            "userName" to userNameToUse // <-- NOW USES THE FETCHED NAME
                        )

                        // 3. Save to Firestore "pending_deposits" collection
                        val firestore = FirebaseFirestore.getInstance()
                        firestore.collection("pending_deposits").add(pendingDepositData).await()

                        // 4. Handle UI update
                        isSubmittingProof = false
                        showProofDialog = false
                        snackbarHostState.showSnackbar("Proof submitted successfully! Awaiting approval.")

                        // 5. Manually refresh pending deposits list
                        walletViewModel.loadWalletData(userId)

                    } catch (e: Exception) {
                        isSubmittingProof = false
                        Log.e("WalletScreen", "Proof submission failed", e)
                        snackbarHostState.showSnackbar("Error: ${e.message}")
                    }
                }
            }
        )
    }


    // 4. Nigerian Deposit Dialog
    if (showNGNDepositDialog) {
        NGNDepositDialog(
            onDismiss = { showNGNDepositDialog = false },
            onDeposit = { amount, paymentMethod, bankDetails, screenshotUrl ->
                if (isAuthenticated && currentUser != null) {
                    Log.d("WalletScreen", "Creating NGN deposit: $amount via $paymentMethod")
                    // TODO: Implement actual NGN payment integration
                }
                showNGNDepositDialog = false
            }
        )
    }

    // 5. Withdraw Dialog
    if (showWithdrawDialog) {
        WithdrawDialog(
            maxAmount = withdrawableBalance,
            isLoading = isLoading,
            currency = userCurrency,
            userCountry = userCountry,
            onDismiss = { showWithdrawDialog = false },
            onWithdraw = { amount, paymentDetails ->
                if (isAuthenticated && currentUser != null) {
                    val userDetails = UserDetails(
                        email = currentUser!!.email ?: "",
                        name = currentUser!!.displayName ?: "",
                        username = currentUser!!.email ?: "",
                        userId = currentUser!!.uid
                    )
                    val request = WithdrawalRequest(
                        userId = currentUser!!.uid,
                        amount = amount,
                        currency = userCurrency,
                        upiId = if (userCountry.equals("India", ignoreCase = true) || userCountry.equals("IN", ignoreCase = true)) paymentDetails else "",
                        userDetails = userDetails,
                        paymentMethod = if (userCountry.equals("Nigeria", ignoreCase = true) || userCountry.equals("NG", ignoreCase = true)) {
                            NGNTransactionUtils.getPaymentMethodForCountry("NG", paymentDetails)
                        } else {
                            NGNTransactionUtils.getPaymentMethodForCountry("IN", "UPI")
                        },
                        bankName = if (userCountry.equals("Nigeria", ignoreCase = true) || userCountry.equals("NG", ignoreCase = true)) paymentDetails else null,
                        userCountry = userCountry
                    )
                    walletViewModel.createWithdrawalRequest(
                        userId = request.userId,
                        amount = request.amount,
                        paymentMethod = request.paymentMethod,
                        bankName = request.bankName,
                        accountNumber = request.accountNumber,
                        accountName = request.accountName
                    )
                    withdrawSuccess = true
                } else if (isAuthenticated && currentUser == null) {
                    Log.d("WalletScreen", "User authenticated but Firebase session lost during withdrawal")
                }
                showWithdrawDialog = false
            }
        )
    }

    // Show withdrawal success/error Snackbar
    LaunchedEffect(withdrawSuccess, error) {
        if (withdrawSuccess && error == null) {
            snackbarHostState.showSnackbar("Withdrawal request submitted successfully!")
            withdrawSuccess = false
        } else if (error != null) {
            error?.let { snackbarHostState.showSnackbar(it) }
            walletViewModel.clearError()
        }
    }

    // Snackbar Host
    Box(Modifier.fillMaxSize()) {
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

// --- Other Composable Functions (Unchanged or minor style changes) ---

/**
 * Dialog to show static QR Code and UPI ID for payment.
 */
@Composable
fun QrCodePaymentDialog(
    amount: Int,
    onDismiss: () -> Unit,
    onPaid: () -> Unit
) {
    val context = LocalContext.current
    val upiId = "abhishekjha031@oksbi"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E), // DarkCard color
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "Scan to Pay",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Please pay: ₹$amount",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                // --- QR Code Image ---
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.qr), // <-- YOUR QR IMAGE
                        contentDescription = "UPI QR Code",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                // --- End of QR Code ---

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Scan with any UPI app or pay to:",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                // UPI ID with copy button
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            upiId,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy UPI ID",
                            tint = NetWinCyan,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("UPI ID", upiId)
                                    clipboard.setPrimaryClip(clip)
                                    Toast
                                        .makeText(context, "UPI ID copied!", Toast.LENGTH_SHORT)
                                        .show()
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onPaid,
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I have paid", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", color = NetWinCyan)
            }
        }
    )
}

/**
 * NEW: Dialog to collect payment proof (Ref ID and Screenshot).
 */
@Composable
fun PaymentProofDialog(
    amount: Int,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (upiRefId: String, screenshotUri: Uri) -> Unit
) {
    var upiRefId by rememberSaveable { mutableStateOf("") }
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Image picker launcher
    val screenshotPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        screenshotUri = uri
        error = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E), // DarkCard color
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "Submit Payment Proof",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "For Amount: ₹$amount",
                    color = Color.Gray,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 1. UPI Reference ID
                OutlinedTextField(
                    value = upiRefId,
                    onValueChange = {
                        upiRefId = it
                        error = null
                    },
                    label = { Text("UPI Reference ID", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00BCD4),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFF00BCD4),
                        focusedLabelColor = Color(0xFF00BCD4),
                        unfocusedLabelColor = Color.Gray
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 2. Screenshot Upload
                Button(
                    onClick = { screenshotPickerLauncher.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A3A)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (screenshotUri == null) Icons.Default.Upload else Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (screenshotUri == null) "Upload Screenshot" else "Change Screenshot",
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 3. Screenshot Preview
                screenshotUri?.let { uri ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Screenshot Preview:", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3A3A)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(uri),
                            contentDescription = "Screenshot Preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // 4. Error Message
                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        error ?: "",
                        color = ErrorRed,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (upiRefId.isBlank()) {
                        error = "Please enter the UPI Reference ID"
                        return@Button
                    }
                    if (screenshotUri == null) {
                        error = "Please upload a payment screenshot"
                        return@Button
                    }
                    onSubmit(upiRefId, screenshotUri!!)
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Text("Submit Proof", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", color = NetWinCyan)
            }
        }
    )
}


@Composable
private fun PendingDepositItem(deposit: PendingDeposit, currency: String) {
    Log.d("PendingDepositItem", "Rendering deposit: ${deposit.upiRefId}, amount: ${deposit.amount}, status: ${deposit.status}")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFC107)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Pending Deposit",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "UPI Ref: ${deposit.upiRefId}",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Your UPI ID: ${deposit.userUpiId}",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    if (!deposit.screenshotUrl.isNullOrBlank()) {
                        Text(
                            text = "Screenshot uploaded",
                            color = Color(0xFF00BCD4),
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = formatDate(deposit.createdAt),
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = NGNTransactionUtils.formatAmount(deposit.amount, currency),
                    color = Color(0xFFFFC107),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = deposit.status.name.lowercase().replaceFirstChar { it.uppercaseChar() },
                    color = Color(0xFFFFC107),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun NGNDepositDialog(
    onDismiss: () -> Unit,
    onDeposit: (Double, String, String?, String?) -> Unit
) {
    var selectedAmount by remember { mutableStateOf(0) }
    var manualAmount by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var paystackLink by remember { mutableStateOf("") }
    var paystackReference by remember { mutableStateOf("") }
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val presetAmounts = listOf(1000, 2000, 5000, 10000)

    // Image picker launcher
    val screenshotPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        screenshotUri = uri
    }

    // Fetch Paystack link from Firestore when dialog is shown
    LaunchedEffect(Unit) {
        val firestore = FirebaseFirestore.getInstance()
        val walletConfigRef = firestore.collection("admin_config").document("wallet_config")
        try {
            val doc = walletConfigRef.get().await()
            val ngnConfig = doc.get("NGN") as? Map<*, *>
            paystackLink = ngnConfig?.get("paymentLink") as? String ?: ""
        } catch (_: Exception) {
            paystackLink = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(20.dp),
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00BCD4)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Add Money to Wallet",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Select amount and pay with Paystack",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Amount Selection Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = Color(0xFF00BCD4),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Select Amount",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        // Preset amounts
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.height(80.dp)
                        ) {
                            items(presetAmounts) { amt ->
                                Button(
                                    onClick = {
                                        selectedAmount = amt
                                        manualAmount = ""
                                        error = null
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedAmount == amt) Color(0xFF00BCD4) else Color(0xFF3A3A3A)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "₦$amt",
                                        color = if (selectedAmount == amt) Color.Black else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Or enter custom amount",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = manualAmount,
                            onValueChange = {
                                manualAmount = it.filter { c -> c.isDigit() }
                                selectedAmount = 0
                                error = null
                            },
                            label = { Text("Amount (₦)", color = Color.Gray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00BCD4),
                                unfocusedBorderColor = Color.Gray,
                                cursorColor = Color(0xFF00BCD4),
                                focusedLabelColor = Color(0xFF00BCD4),
                                unfocusedLabelColor = Color.Gray,
                                focusedContainerColor = Color(0xFF3A3A3A),
                                unfocusedContainerColor = Color(0xFF3A3A3A)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // Paystack Payment Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Payment,
                                contentDescription = null,
                                tint = Color(0xFF00BCD4),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Paystack Payment",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            "Pay securely with Paystack",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (paystackLink.isNotBlank()) {
                            Button(
                                onClick = { uriHandler.openUri(paystackLink) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Paystack Payment Page", color = Color.Black, fontWeight = FontWeight.Medium)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "After payment, enter your transaction reference and upload a screenshot.",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = paystackReference,
                                onValueChange = { paystackReference = it },
                                label = { Text("Transaction Reference", color = Color.Gray) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00BCD4),
                                    unfocusedBorderColor = Color.Gray,
                                    cursorColor = Color(0xFF00BCD4),
                                    focusedLabelColor = Color(0xFF00BCD4),
                                    unfocusedLabelColor = Color.Gray,
                                    focusedContainerColor = Color(0xFF3A3A3A),
                                    unfocusedContainerColor = Color(0xFF3A3A3A)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = { screenshotPickerLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A3A)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    if (screenshotUri == null) Icons.Default.Upload else Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (screenshotUri == null) "Upload Screenshot" else "Change Screenshot",
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            screenshotUri?.let { uri ->
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3A3A)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(uri),
                                        contentDescription = "Screenshot Preview",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        } else {
                            Text(
                                "Unable to load Paystack link. Please try again later.",
                                color = Color.Red,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (error != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2A2A)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                error ?: "",
                                color = Color.Red,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = if (selectedAmount > 0) selectedAmount else manualAmount.toIntOrNull() ?: 0
                    if (amount < 100) {
                        error = "Minimum amount is ₦100"
                        return@Button
                    }
                    if (amount > 1000000) {
                        error = "Maximum amount is ₦1,000,000"
                        return@Button
                    }
                    if (paystackReference.isBlank()) {
                        error = "Please enter your Paystack transaction reference"
                        return@Button
                    }
                    if (screenshotUri == null) {
                        error = "Please upload a screenshot of your payment"
                        return@Button
                    }
                    onDeposit(
                        amount.toDouble(),
                        "PAYSTACK",
                        paystackReference,
                        screenshotUri?.toString()
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Proceed with Payment", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF00BCD4))
            ) {
                Text("Cancel", fontWeight = FontWeight.Medium)
            }
        }
    )
}

@Composable
fun WithdrawDialog(
    maxAmount: Double,
    isLoading: Boolean,
    currency: String,
    userCountry: String,
    onDismiss: () -> Unit,
    onWithdraw: (Double, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var paymentDetails by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val isNigerian = userCountry.equals("Nigeria", ignoreCase = true) || userCountry.equals("NG", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Withdraw Funds") },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it.filter { c -> c.isDigit() || c == '.' }
                        error = null
                    },
                    label = { Text("Amount (${NGNTransactionUtils.getCurrencySymbol(currency)})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00BCD4),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFF00BCD4),
                        focusedLabelColor = Color(0xFF00BCD4),
                        unfocusedLabelColor = Color.Gray
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = paymentDetails,
                    onValueChange = {
                        paymentDetails = it
                        error = null
                    },
                    label = {
                        Text(
                            if (isNigerian) "Bank Name (e.g., GTB, ZENITH)" else "Your UPI ID",
                            color = Color.Gray
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00BCD4),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFF00BCD4),
                        focusedLabelColor = Color(0xFF00BCD4),
                        unfocusedLabelColor = Color.Gray
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Max withdrawable: ${NGNTransactionUtils.formatAmount(maxAmount, currency)}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                if (error != null) {
                    Text(error ?: "", color = Color.Red, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt <= 0.0) {
                        error = "Enter a valid amount"
                        return@Button
                    }
                    if (amt > maxAmount) {
                        error = "Amount exceeds withdrawable balance"
                        return@Button
                    }
                    if (isNigerian) {
                        if (paymentDetails.isBlank()) {
                            error = "Please enter bank name"
                            return@Button
                        }
                    } else {
                        if (paymentDetails.isBlank() || !paymentDetails.contains("@")) {
                            error = "Enter a valid UPI ID"
                            return@Button
                        }
                    }
                    onWithdraw(amt, paymentDetails)
                    amount = ""
                    paymentDetails = ""
                },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Text("Withdraw")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun WithdrawalRequestItem(request: WithdrawalRequest, currency: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF232323)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    NGNTransactionUtils.formatAmount(request.amount, currency),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    if (request.userCountry.equals("Nigeria", ignoreCase = true) || request.userCountry.equals("NG", ignoreCase = true)) (request.bankName ?: "Bank Transfer") else request.upiId,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                StatusChip(request.status)
                if (!request.rejectionReason.isNullOrBlank()) {
                    Text("Reason: ${request.rejectionReason}", color = Color.Red, fontSize = 12.sp)
                }
            }
            Text(
                text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(request.createdAt)),
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

private fun formatDate(timestamp: Timestamp?): String {
    if (timestamp == null) return "Unknown"
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return sdf.format(timestamp.toDate())
}

// Enhanced Components for MVP-Ready Wallet Screen

@Composable
private fun ImprovedWalletTopBar(totalBalance: Double, currency: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Wallet",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Surface (
            color = DarkSurface,
            shape = RoundedCornerShape(12.dp),
            border =  BorderStroke(
                1.dp,
                Brush.horizontalGradient(listOf(NetWinPurple, NetWinPink, NetWinCyan))
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    NGNTransactionUtils.formatAmountTidy(totalBalance, currency),
                    modifier = Modifier.padding(start = 6.dp),
                    color = NetwinTokens.TextPrimary,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun EnhancedBalanceCard(
    totalBalance: Double,
    withdrawableBalance: Double,
    currency: String,
    onWithdrawClick: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkCard
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Total Balance
                EsportsBalanceTab(
                    label = "Available Balance",
                    amount = NGNTransactionUtils.formatAmountTidy(totalBalance, currency),
                    color = NetWinCyan,
                    isSelected = true,
                    modifier = Modifier.weight(1f)
                )

                // Withdrawable Balance
                EsportsBalanceTab(
                    label = "Withdrawable",
                    amount = NGNTransactionUtils.formatAmountTidy(withdrawableBalance, currency),
                    color = NetWinPurple,
                    isSelected = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// --- MODIFIED COMPOSABLE ---
@Composable
private fun EnhancedQuickActions(
    onDepositClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    isDepositEnabled: Boolean,
    isWithdrawEnabled: Boolean, // Now controls only withdrawal
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Quick Actions",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Add Cash - Enabled by isDepositEnabled (which is set to true)
            EsportsActionButton(
                icon = Icons.Default.Add,
                label = "Add Cash",
                onClick = onDepositClick,
                enabled = isDepositEnabled,
                backgroundColor = SuccessGreen,
                modifier = Modifier.weight(1f)
            )

            // Withdraw - Controlled by isWithdrawEnabled (KYC status)
            EsportsActionButton(
                icon = Icons.Default.GetApp,
                label = "Withdraw",
                onClick = onWithdrawClick,
                enabled = isWithdrawEnabled,
                backgroundColor = ErrorRed,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
// --- END MODIFIED COMPOSABLE ---

@Composable
private fun EnhancedTransactionItem(transaction: Transaction, currency: String) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Transaction Icon
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = when (transaction.type) {
                                        TransactionType.DEPOSIT, TransactionType.UPI_DEPOSIT -> listOf(SuccessGreen, SuccessGreen.copy(alpha = 0.7f))
                                        TransactionType.WITHDRAWAL, TransactionType.UPI_WITHDRAWAL -> listOf(ErrorRed, ErrorRed.copy(alpha = 0.7f))
                                        TransactionType.TOURNAMENT_ENTRY -> listOf(NetWinPurple, NetWinPink)
                                        TransactionType.TOURNAMENT_WINNING -> listOf(WarningYellow, WarningYellow.copy(alpha = 0.7f))
                                        else -> listOf(Color.Gray, Color.Gray.copy(alpha = 0.7f))
                                    }
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (transaction.type) {
                                TransactionType.DEPOSIT, TransactionType.UPI_DEPOSIT -> Icons.Default.Add
                                TransactionType.WITHDRAWAL, TransactionType.UPI_WITHDRAWAL -> Icons.Default.Remove
                                TransactionType.TOURNAMENT_ENTRY -> Icons.Default.PlayArrow
                                TransactionType.TOURNAMENT_WINNING -> Icons.Default.EmojiEvents
                                else -> Icons.Default.Info
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {

                        // --- THIS IS THE CHANGE ---
                        val descriptionText = if (
                            transaction.type in listOf(TransactionType.DEPOSIT, TransactionType.UPI_DEPOSIT) &&
                            transaction.status == TransactionStatus.COMPLETED
                        ) {
                            "Amount Credited" // Override description for completed deposits
                        } else {
                            transaction.description // Use the default description for all other cases
                        }

                        Text(
                            text = descriptionText, // Use the dynamically set text
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = if (isExpanded) Int.MAX_VALUE else 1
                        )
                        // --- END OF CHANGE ---

                        Text(
                            text = formatTransactionDate(transaction.createdAt),
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    // Amount
                    Text(
                        text = "${if (transaction.type in listOf(TransactionType.DEPOSIT, TransactionType.UPI_DEPOSIT, TransactionType.TOURNAMENT_WINNING)) "+" else "-"}${NGNTransactionUtils.formatAmount(transaction.amount, currency)}",
                        color = when (transaction.type) {
                            TransactionType.DEPOSIT, TransactionType.UPI_DEPOSIT, TransactionType.TOURNAMENT_WINNING -> SuccessGreen
                            TransactionType.WITHDRAWAL, TransactionType.UPI_WITHDRAWAL, TransactionType.TOURNAMENT_ENTRY -> ErrorRed
                            else -> Color.Gray
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Status Chip
                    Box(
                        modifier = Modifier
                            .background(
                                color = when (transaction.status) {
                                    TransactionStatus.COMPLETED -> SuccessGreen.copy(alpha = 0.2f)
                                    TransactionStatus.PENDING -> WarningYellow.copy(alpha = 0.2f)
                                    TransactionStatus.FAILED -> ErrorRed.copy(alpha = 0.2f)
                                    else -> Color.Gray.copy(alpha = 0.2f)
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = transaction.status.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = when (transaction.status) {
                                TransactionStatus.COMPLETED -> SuccessGreen
                                TransactionStatus.PENDING -> WarningYellow
                                TransactionStatus.FAILED -> ErrorRed
                                else -> Color.Gray
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Expand/Collapse Icon
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expanded Details
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Divider(color = Color.Gray.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Transaction Details",
                        color = NetWinCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    TransactionDetailRow("Type", transaction.type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() })
                    TransactionDetailRow("Amount", NGNTransactionUtils.formatAmount(transaction.amount, currency))
                    TransactionDetailRow("Status", transaction.status.name.lowercase().replaceFirstChar { it.uppercase() })
                    TransactionDetailRow("Date", formatTransactionDate(transaction.createdAt))
                }
            }
        }
    }
}

@Composable
private fun TransactionDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTransactionDate(timestamp: Timestamp?): String {
    if (timestamp == null) return "Unknown"
    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    return sdf.format(timestamp.toDate())
}

// New Esports-Style Components

@Composable
private fun EsportsBalanceTab(
    label: String,
    amount: String,
    color: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) DarkCard else DarkSurface
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(
            1.dp,
            Brush.horizontalGradient(listOf(NetWinPurple, NetWinPink, NetWinCyan))
        ) else BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = amount,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (isSelected) {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = NetWinCyan,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun EsportsActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            disabledContainerColor = Color.Gray.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatEsportsDate(timestamp: Timestamp?): String {
    if (timestamp == null) return "Unknown"
    val sdf = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
    return sdf.format(timestamp.toDate())
}
