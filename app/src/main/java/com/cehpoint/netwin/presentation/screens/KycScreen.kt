package com.cehpoint.netwin.presentation.screens

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.cehpoint.netwin.R
import com.cehpoint.netwin.data.model.DocumentType
import com.cehpoint.netwin.data.model.KycDocument
import com.cehpoint.netwin.domain.repository.KycImageType
import com.cehpoint.netwin.presentation.viewmodels.KycViewModel
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KycScreen(
    navController: NavController,
    kycViewModel: KycViewModel = hiltViewModel()
) {
    val uiState by kycViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    var documentType by remember { mutableStateOf(DocumentType.PAN) }
    var documentNumber by remember { mutableStateOf("") }
    var documentNumberError by remember { mutableStateOf<String?>(null) }
    var imageError by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }
    var showSelfieDialog by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }

    // Launchers
    val frontImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { kycViewModel.uploadImage(userId, KycImageType.FRONT, it) }
    }
    val backImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { kycViewModel.uploadImage(userId, KycImageType.BACK, it) }
    }

    // Camera & Selfie
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }
    val selfieCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) {
            kycViewModel.uploadImage(userId, KycImageType.SELFIE, cameraImageUri!!)
        }
    }
    val selfieGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { kycViewModel.uploadImage(userId, KycImageType.SELFIE, it) }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val photoFile = createImageFile()
            val photoUri = FileProvider.getUriForFile(context, context.packageName + ".provider", photoFile)
            cameraImageUri = photoUri
            selfieCameraLauncher.launch(photoUri)
            showSelfieDialog = false
        } else Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
    }

    // Observe Firestore
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) kycViewModel.observeKyc(userId)
    }

    val kycDoc = uiState.kycDocument
    val isApproved = kycDoc?.status?.name == "APPROVED"
    val isPending = kycDoc?.status?.name == "PENDING"
    val isRejected = kycDoc?.status?.name == "REJECTED"
    val isFormEnabled = !isApproved && !isPending

    // Validation
    fun validate(): Boolean {
        documentNumberError = null
        imageError = null
        if (documentNumber.isBlank()) {
            documentNumberError = "Enter document number."
            return false
        }
        if (uiState.frontImageUrl.isBlank()) {
            imageError = "Front image required."
            return false
        }
        if (documentType == DocumentType.AADHAR && uiState.backImageUrl.isBlank()) {
            imageError = "Back image required for Aadhaar."
            return false
        }
        if (uiState.selfieUrl.isBlank()) {
            imageError = "Selfie required."
            return false
        }
        return true
    }

    // --- UI ---
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.login_screen),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                        }
                    },
                    title = { Text("KYC Verification", color = Color.White, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status Card
                if (kycDoc != null) {
                    KycStatusCard(isApproved, isPending, isRejected, kycDoc.rejectionReason)
                    if (isApproved) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { showDetailsSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
                        ) {
                            Text("View KYC Details", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }

                // KYC Form Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E).copy(alpha = 0.9f)),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(Modifier.padding(24.dp)) {
                        // Document Type
                        var expanded by remember { mutableStateOf(false) }
                        Text("Document Type", color = Color.Cyan, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { expanded = true },
                            enabled = isFormEnabled,
                            border = BorderStroke(1.dp, Color.Cyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text(documentType.name)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(Color(0xFF2C2C2C))
                        ) {
                            DocumentType.values().forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name, color = Color.White) },
                                    onClick = {
                                        documentType = type
                                        expanded = false
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Document Number
                        OutlinedTextField(
                            value = documentNumber,
                            onValueChange = { documentNumber = it },
                            label = { Text("Document Number") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isFormEnabled,
                            isError = documentNumberError != null,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.Cyan,
                                unfocusedBorderColor = Color.Gray,
                                cursorColor = Color.Cyan
                            )
                        )
                        if (documentNumberError != null)
                            Text(documentNumberError!!, color = Color.Red, fontSize = MaterialTheme.typography.bodySmall.fontSize)

                        Spacer(Modifier.height(20.dp))

                        // Image Pickers
                        Text("Upload Documents", color = Color.Cyan, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            ImagePicker(label = "Front", imageUrl = uiState.frontImageUrl) {
                                if (isFormEnabled) frontImageLauncher.launch("image/*")
                            }
                            ImagePicker(label = "Back", imageUrl = uiState.backImageUrl) {
                                if (isFormEnabled) backImageLauncher.launch("image/*")
                            }
                            ImagePicker(label = "Selfie", imageUrl = uiState.selfieUrl) {
                                if (isFormEnabled) showSelfieDialog = true
                            }
                        }
                        if (imageError != null)
                            Text(imageError!!, color = Color.Red, fontSize = MaterialTheme.typography.bodySmall.fontSize)

                        Spacer(Modifier.height(24.dp))

                        // Submit Button
                        Button(
                            onClick = {
                                if (!validate()) return@Button
                                val doc = KycDocument(
                                    userId = userId,
                                    documentType = documentType,
                                    documentNumber = documentNumber,
                                    frontImageUrl = uiState.frontImageUrl,
                                    backImageUrl = uiState.backImageUrl,
                                    selfieUrl = uiState.selfieUrl
                                )
                                kycViewModel.submitKyc(doc)
                                showSuccess = true
                            },
                            enabled = isFormEnabled && !uiState.isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
                        ) {
                            if (uiState.isLoading)
                                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                            else
                                Text("Submit KYC", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        if (showSuccess && uiState.error == null && !uiState.isLoading) {
                            Spacer(Modifier.height(8.dp))
                            Text("✅ KYC submitted successfully!", color = Color.Green)
                        }
                        uiState.error?.let {
                            Spacer(Modifier.height(8.dp))
                            Text("Error: $it", color = Color.Red)
                        }
                    }
                }

                Spacer(Modifier.height(60.dp))
            }
        }
    }

    // View KYC Details Modal
    if (showDetailsSheet && kycDoc != null) {
        ModalBottomSheet(onDismissRequest = { showDetailsSheet = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("KYC Details", color = Color.White, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.titleLarge.fontSize)
                Spacer(Modifier.height(16.dp))
                Text("Status: ✅ Approved", color = Color(0xFF00C853), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Document Type: ${kycDoc.documentType.name}", color = Color.Cyan)
                Text("Document Number: ${kycDoc.documentNumber}", color = Color.Cyan)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    DetailImagePreview(label = "Front", url = kycDoc.frontImageUrl)
                    DetailImagePreview(label = "Back", url = kycDoc.backImageUrl)
                    DetailImagePreview(label = "Selfie", url = kycDoc.selfieUrl)
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = { showDetailsSheet = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)) {
                    Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Selfie Picker Dialog
    if (showSelfieDialog) {
        AlertDialog(
            onDismissRequest = { showSelfieDialog = false },
            title = { Text("Select Selfie Source", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Button(onClick = {
                        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            val photoFile = createImageFile()
                            val photoUri = FileProvider.getUriForFile(
                                context, context.packageName + ".provider", photoFile
                            )
                            cameraImageUri = photoUri
                            selfieCameraLauncher.launch(photoUri)
                            showSelfieDialog = false
                        } else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("📷 Take Photo")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        selfieGalleryLauncher.launch("image/*")
                        showSelfieDialog = false
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("🖼️ Choose from Gallery")
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
fun KycStatusCard(isApproved: Boolean, isPending: Boolean, isRejected: Boolean, rejectionReason: String?) {
    val (color, message) = when {
        isApproved -> Color(0xFF00C853) to "✅ Your KYC is Approved!"
        isPending -> Color(0xFFFFC107) to "⏳ Your KYC is Pending Review."
        isRejected -> Color(0xFFD50000) to ("❌ Rejected: ${rejectionReason ?: "Please re-submit."}")
        else -> Color.Gray to "KYC Not Submitted"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E).copy(alpha = 0.9f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
            Text(message, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ImagePicker(label: String, imageUrl: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .border(BorderStroke(1.dp, Color.Cyan), RoundedCornerShape(12.dp))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl.isNotBlank()) {
                Image(
                    painter = rememberAsyncImagePainter(imageUrl),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(label, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.Gray, fontSize = MaterialTheme.typography.bodySmall.fontSize)
    }
}

@Composable
fun DetailImagePreview(label: String, url: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .border(BorderStroke(1.dp, Color.Cyan), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (url.isNotBlank()) {
                Image(
                    painter = rememberAsyncImagePainter(url),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(label, color = Color.White)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.Gray, fontSize = MaterialTheme.typography.bodySmall.fontSize)
    }
}
