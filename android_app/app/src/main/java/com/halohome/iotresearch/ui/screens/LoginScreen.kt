package com.halohome.iotresearch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halohome.iotresearch.data.api.NetworkModule
import com.halohome.iotresearch.data.models.LoginRequest
import com.halohome.iotresearch.telemetry.MobileTelemetryManager
import com.halohome.iotresearch.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Participant Login Screen.
 * Authenticates returning participants and includes a server URL configuration dialog
 * in the top app bar for switching between emulator, LAN, and cloud endpoints.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: (isCommissioned: Boolean) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showServerDialog by remember { mutableStateOf(false) }
    var serverUrlInput by remember { mutableStateOf(NetworkModule.baseUrl) }

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SafeHome IoT Setup", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateBackground),
                actions = {
                    IconButton(onClick = { showServerDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Server URL Settings", tint = TextSecondary)
                    }
                }
            )
        },
        containerColor = SlateBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(68.dp),
                shape = RoundedCornerShape(18.dp),
                color = BlueAccent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("SafeHome", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("IoT Commissioning & Security Auditor", fontSize = 13.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Participant Email") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    errorMessage?.let { err ->
                        Text(
                            text = err,
                            color = DangerRed,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DangerLight, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        )
                    }

                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                errorMessage = "Please enter email and password."
                                return@Button
                            }
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    val res = NetworkModule.apiService.login(LoginRequest(email.trim(), password.trim()))
                                    if (res.isSuccessful && res.body()?.success == true) {
                                        val body = res.body()!!
                                        NetworkModule.authToken = body.token
                                        NetworkModule.userId = body.user?.id ?: -1
                                        NetworkModule.participantId = body.user?.participantId ?: "P-001"
                                        val isCommissioned = (body.session?.get("status") as? String) == "commissioned"
                                        NetworkModule.isCommissioned = isCommissioned

                                        MobileTelemetryManager.startHeartbeat()
                                        MobileTelemetryManager.logEvent("user_signed_in")
                                        onLoginSuccess(isCommissioned)
                                    } else {
                                        errorMessage = res.body()?.error ?: "Invalid email or password."
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Cannot connect to server (${NetworkModule.baseUrl}). Tap settings to update URL."
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                        } else {
                            Text("Sign In to Commissioning", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onNavigateToRegister) {
                Text("New Participant? Register Here", color = BlueAccent, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("Server Configuration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Default emulator: http://10.0.2.2:5000/\nCustom domain: https://api.rickhomelab.co.za/",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        label = { Text("API Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    NetworkModule.baseUrl = serverUrlInput.trim()
                    showServerDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
