package com.cehpoint.netwin.presentation.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.cehpoint.netwin.R
import com.cehpoint.netwin.presentation.viewmodels.ProfileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreenUI(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    var editName by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                title = {
                    Text(
                        "Profile",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xCC181A20)),
                modifier = Modifier.shadow(8.dp)
            )
        },
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->

        Box(modifier = Modifier.fillMaxSize()) {
            // Background image
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
                    .background(Color.Black.copy(alpha = 0.65f))
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
                        Text("Error: $error", color = Color.Red)
                    }
                }

                user != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Profile Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1E1E1E).copy(alpha = 0.9f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Profile Image
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    GradientProfilePicture(
                                        imageUrl = user!!.profilePictureUrl,
                                        size = 110.dp,
                                        contentDescription = "Profile picture of ${user!!.displayName}",
                                        onClick = { /* Add image picker */ }
                                    )
                                    FloatingActionButton(
                                        onClick = { /* Add image picker */ },
                                        containerColor = Color(0xFF1E1E1E),
                                        contentColor = Color.Cyan,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .offset(y = (-6).dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(22.dp))

                                // Name Field
                                AnimatedNameField(
                                    name = user!!.displayName,
                                    editMode = editName,
                                    newName = newName,
                                    onNameChange = { newName = it },
                                    onEditClick = {
                                        newName = user!!.displayName
                                        editName = true
                                    },
                                    onSave = {
                                        if (newName.isNotBlank()) {
                                            viewModel.updateUserField("displayName", newName)
                                            editName = false
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Profile updated")
                                            }
                                        }
                                    },
                                    onCancel = { editName = false }
                                )

                                if (user!!.username.isNotBlank()) {
                                    Text(
                                        text = "@${user!!.username}",
                                        color = Color.Cyan,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                                Text(
                                    user!!.email ?: "No Email",
                                    color = Color.Gray,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        // Version Info
                        Text(
                            text = "App Version 1.0.0",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )

                        Spacer(modifier = Modifier.height(80.dp)) // Spacer before edit profile section

                        // Action Buttons at the bottom
                        ActionButtons(
                            isEditing = editName,
                            onEdit = { editName = true },
                            onSave = {
                                if (newName.isNotBlank()) {
                                    viewModel.updateUserField("displayName", newName)
                                    editName = false
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Profile updated")
                                    }
                                }
                            },
                            onCancel = { editName = false }
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No user data available.", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun GradientProfilePicture(
    imageUrl: String,
    size: Dp,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Transparent)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(Color(0xFF00E5FF), Color(0xFFAA00FF), Color(0xFF00E5FF))
                ),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Box(
            modifier = Modifier
                .padding(6.dp)
                .clip(CircleShape)
                .background(Color.DarkGray)
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl.isNotBlank()) {
                AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.size(size * 0.6f)
                )
            }
        }
    }
}

@Composable
private fun AnimatedNameField(
    name: String,
    editMode: Boolean,
    newName: String,
    onNameChange: (String) -> Unit,
    onEditClick: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    if (editMode) {
        OutlinedTextField(
            value = newName,
            onValueChange = onNameChange,
            label = { Text("Full Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(onClick = onSave) {
                Text("Save", color = Color.Cyan, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            TextButton(onClick = onCancel) {
                Text("Cancel", color = Color.Red, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name.ifBlank { "No Name" },
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit display name", tint = Color.Cyan)
            }
        }
    }
}

@Composable
private fun ActionButtons(
    isEditing: Boolean,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = if (isEditing) onSave else onEdit,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
        ) {
            Text(
                if (isEditing) "Save Changes" else "Edit Profile",
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }

        if (isEditing) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                border = BorderStroke(1.dp, Color.Cyan),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Cyan)
            ) {
                Text("Cancel", color = Color.Cyan, fontWeight = FontWeight.Bold)
            }
        }
    }
}
