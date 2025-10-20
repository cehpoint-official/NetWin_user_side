package com.cehpoint.netwin.presentation.screens

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.cehpoint.netwin.R // Assumes login_screen.jpg is in res/drawable
import com.cehpoint.netwin.data.model.DocumentType
import com.cehpoint.netwin.data.model.KycDocument
import com.cehpoint.netwin.domain.repository.KycImageType
import com.cehpoint.netwin.presentation.components.StatusChip
import com.cehpoint.netwin.presentation.viewmodels.KycViewModel
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KycScreen(
    navController: NavController,
    kycViewModel: KycViewModel = hiltViewModel()
) {
    val uiState by kycViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // State for form fields
    var documentType by remember { mutableStateOf(DocumentType.PAN) }
    var documentNumber by remember { mutableStateOf("") }
    var documentNumberError by remember { mutableStateOf<String?>(null) }
    var imageError by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }
    val supportedCountries = listOf("IN", "NG") // Add more as needed
    val countryNames = mapOf("IN" to "India", "NG" to "Nigeria")
    var countryDropdownExpanded by remember { mutableStateOf(false) }
    var country by remember { mutableStateOf("") }

    // Image pickers
    val frontImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { kycViewModel.uploadImage(userId, KycImageType.FRONT, it) }
    }
    val backImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { kycViewModel.uploadImage(userId, KycImageType.BACK, it) }
    }

    // Selfie picker
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var showSelfieDialog by remember { mutableStateOf(false) }
    fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        // --- THIS IS THE FIX (Typo corrected) ---
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    val selfieCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) {
            kycViewModel.uploadImage(userId, KycImageType.SELFIE, cameraImageUri!!)
        }
    }
    val selfieGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { kycViewModel.uploadImage(userId, KycImageType.SELFIE, it) }
    }

    // Camera permission launcher
    val cameraPermission = android.Manifest.permission.CAMERA
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val photoFile = createImageFile()
            val photoUri = FileProvider.getUriForFile(
                context,
                context.packageName + ".provider",
                photoFile
            )
            cameraImageUri = photoUri
            selfieCameraLauncher.launch(photoUri)
            showSelfieDialog = false
        } else {
            Toast.makeText(context, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    // Observe KYC status
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) kycViewModel.observeKyc(userId)
    }

    // Sync remote KYC data with local form state
    LaunchedEffect(uiState.kycDocument) {
        uiState.kycDocument?.let { doc ->
            documentType = doc.documentType
            documentNumber = doc.documentNumber
            // --- This remains commented out. Uncomment after adding 'country' to your KycDocument data class ---
            // country = doc.country
        }
    }

    // Validation logic
    fun validate(): Boolean {
        documentNumberError = null
        imageError = null
        if (documentType == DocumentType.PAN) {
            if (documentNumber.length != 10) {
                documentNumberError = "PAN must be 10 characters."
                return false
            }
        } else if (documentType == DocumentType.AADHAR) {
            if (documentNumber.length != 12 || documentNumber.any { !it.isDigit() }) {
                documentNumberError = "Aadhaar must be 12 digits."
                return false
            }
        }
        if (uiState.frontImageUrl.isBlank()) {
            imageError = "Front image is required."
            return false
        }
        if (documentType == DocumentType.AADHAR && uiState.backImageUrl.isBlank()) {
            imageError = "Back image is required for Aadhaar."
            return false
        }
        if (uiState.selfieUrl.isBlank()) {
            imageError = "Selfie is required."
            return false
        }
        if (country.isBlank()) {
            imageError = "Country is required."
            return false
        }
        return true
    }

    // --- UI Colors ---
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color.Cyan,
        unfocusedBorderColor = Color.Gray,
        cursorColor = Color.Cyan,
        focusedLabelColor = Color.Cyan,
        unfocusedLabelColor = Color.White,
        // --- THIS IS THE FIX (Icon colors) ---
        focusedTrailingIconColor = Color.White,
        unfocusedTrailingIconColor = Color.White
    )

    // --- Form Enable/Disable Logic ---
    val kycDoc = uiState.kycDocument
    val kycStatus = kycDoc?.status?.name
    val isApproved = kycStatus == "APPROVED"
    val isPending = kycStatus == "PENDING"
    val isRejected = kycStatus == "REJECTED"
    val isFormEnabled = !isApproved && !isPending

    Box(modifier = Modifier.fillMaxSize()) {
        // --- UI ENHANCEMENT: Background Image ---
        Image(
            painter = painterResource(id = R.drawable.login_screen), // Using your image
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // --- UI ENHANCEMENT: Dark Overlay ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xCC000000), Color(0xCC000000)) // 80% black
                    )
                )
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    title = { Text("KYC Verification", color = Color.White) },
                    // --- UI ENHANCEMENT: Transparent TopBar ---
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    modifier = Modifier.shadow(0.dp) // Remove shadow
                )
            },
            // --- UI ENHANCEMENT: Transparent Scaffold ---
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()), // Make form scrollable
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Spacer(Modifier.height(16.dp))

                // --- Improved KYC Status Display ---
                kycDoc?.let {
                    StatusChip(it.status.name)
                    Spacer(Modifier.height(16.dp))
                    when {
                        isApproved -> Text(
                            "Your KYC is approved. No further action is needed.",
                            color = Color.Green
                        )
                        isPending -> Text(
                            "Your KYC is pending review. Fields are locked.",
                            color = Color(0xFFF0E68C) // Khaki/Yellow
                        )
                        isRejected -> Text(
                            it.rejectionReason ?: "Your KYC was rejected. Please resubmit.",
                            color = Color.Red
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Document Type Dropdown
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        enabled = isFormEnabled,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(Color.Gray, Color.Gray)))
                    ) {
                        Text(documentType.name)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(Color(0xFF2C2C2C)) // Dark background
                    ) {
                        DocumentType.values().forEach { type ->
                            if (type == DocumentType.AADHAR && country.uppercase() != "IN") return@forEach
                            DropdownMenuItem(
                                text = { Text(type.name, color = Color.White) },
                                onClick = {
                                    documentType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Document Number
                OutlinedTextField(
                    value = documentNumber,
                    onValueChange = { documentNumber = it; documentNumberError = null },
                    label = { Text("Document Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isFormEnabled,
                    isError = documentNumberError != null,
                    colors = textFieldColors // Thematic colors
                )
                if (documentNumberError != null) {
                    Text(documentNumberError ?: "", color = Color.Red, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                }
                Spacer(Modifier.height(16.dp))

                // Country
                ExposedDropdownMenuBox(
                    expanded = countryDropdownExpanded,
                    onExpandedChange = { if (isFormEnabled) countryDropdownExpanded = !countryDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = countryNames[country] ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Country") },
                        // --- THIS IS THE FIX (Removed 'tint') ---
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        singleLine = true,
                        enabled = isFormEnabled,
                        colors = textFieldColors // This now correctly tints the icon
                    )
                    ExposedDropdownMenu(
                        expanded = countryDropdownExpanded,
                        onDismissRequest = { countryDropdownExpanded = false },
                        modifier = Modifier.background(Color(0xFF2C2C2C)) // Dark background
                    ) {
                        supportedCountries.forEach { code ->
                            DropdownMenuItem(
                                text = { Text(countryNames[code] ?: code, color = Color.White) },
                                onClick = {
                                    country = code
                                    countryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Image Pickers
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ImagePickerButton(
                        label = "Front Image",
                        imageUrl = uiState.frontImageUrl,
                        onClick = { frontImageLauncher.launch("image/*") },
                        helperText = "JPG, PNG",
                        enabled = isFormEnabled
                    )
                    Spacer(Modifier.width(8.dp))
                    ImagePickerButton(
                        label = "Back Image",
                        imageUrl = uiState.backImageUrl,
                        onClick = { backImageLauncher.launch("image/*") },
                        helperText = if (documentType == DocumentType.AADHAR) "Required" else "Optional",
                        enabled = isFormEnabled
                    )
                    Spacer(Modifier.width(8.dp))
                    ImagePickerButton(
                        label = "Selfie",
                        imageUrl = uiState.selfieUrl,
                        onClick = { showSelfieDialog = true },
                        helperText = "Face must be visible",
                        enabled = isFormEnabled
                    )
                }
                if (imageError != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(imageError ?: "", color = Color.Red, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                }
                Spacer(Modifier.height(24.dp))

                // Submit Button
                Button(
                    onClick = {
                        if (userId.isEmpty()) {
                            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (!validate()) return@Button
                        val newKycDoc = KycDocument(
                            userId = userId,
                            documentType = documentType,
                            documentNumber = documentNumber,
                            frontImageUrl = uiState.frontImageUrl,
                            backImageUrl = uiState.backImageUrl,
                            selfieUrl = uiState.selfieUrl, // selfieUrl is included
                            // --- This remains commented out. Add 'country' to your KycDocument.kt file to use it. ---
                            // country = country,
                        )
                        kycViewModel.submitKyc(newKycDoc)
                        showSuccess = true
                    },
                    enabled = !uiState.isLoading && isFormEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    // --- UI ENHANCEMENT: Thematic Button ---
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.Cyan
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp, brush = Brush.horizontalGradient(listOf(Color.Cyan, Color.Cyan)))
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(color = Color.Cyan, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Submit KYC", color = Color.Cyan)
                    }
                }
                if (showSuccess && uiState.error == null && !uiState.isLoading) {
                    Spacer(Modifier.height(8.dp))
                    Text("KYC submitted successfully!", color = Color.Green)
                }
                // Error
                uiState.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("Error: $it", color = Color.Red)
                }

                // Clear form fields after successful submission
                LaunchedEffect(showSuccess, uiState.error, uiState.isLoading) {
                    if (showSuccess && uiState.error == null && !uiState.isLoading) {
                        documentType = DocumentType.PAN
                        documentNumber = ""
                        documentNumberError = null
                        imageError = null
                        kycViewModel.resetImages()
                    }
                }
            }
        }
    }

    // Selfie picker dialog
    if (showSelfieDialog) {
        AlertDialog(
            onDismissRequest = { showSelfieDialog = false },
            title = { Text("Select Selfie Source") },
            text = {
                Column {
                    Button(onClick = {
                        if (ContextCompat.checkSelfPermission(context, cameraPermission) == PackageManager.PERMISSION_GRANTED) {
                            val photoFile = createImageFile()
                            val photoUri = FileProvider.getUriForFile(
                                context,
                                context.packageName + ".provider",
                                photoFile
                            )
                            cameraImageUri = photoUri
                            selfieCameraLauncher.launch(photoUri)
                            showSelfieDialog = false
                        } else {
                            cameraPermissionLauncher.launch(cameraPermission)
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Take Photo")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        selfieGalleryLauncher.launch("image/*")
                        showSelfieDialog = false
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Choose from Gallery")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSelfieDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ImagePickerButton(
    label: String,
    imageUrl: String,
    onClick: () -> Unit,
    helperText: String = "",
    enabled: Boolean = true
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                // --- UI ENHANCEMENT: Thematic Picker ---
                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl.isNotBlank()) {
                Image(
                    painter = rememberAsyncImagePainter(imageUrl),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(80.dp)
                )
            } else {
                Text(label, color = Color.White, modifier = Modifier.padding(8.dp))
            }
        }
        if (helperText.isNotBlank()) {
            Text(helperText, color = Color.Gray, fontSize = MaterialTheme.typography.bodySmall.fontSize)
        }
    }
}