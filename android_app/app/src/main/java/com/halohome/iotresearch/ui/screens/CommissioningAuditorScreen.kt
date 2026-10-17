package com.halohome.iotresearch.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halohome.iotresearch.data.api.NetworkModule
import com.halohome.iotresearch.data.models.*
import com.halohome.iotresearch.telemetry.MobileTelemetryManager
import com.halohome.iotresearch.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Commissioning Auditor Screen.
 * Primary research interface presenting the 7 IoT gateway trade-off settings.
 * Organized into 3 categorized tabs (Access, Network, Privacy) with a compact live
 * auditor posture bar and clean, native selectable cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommissioningAuditorScreen(
    onCommissionSuccess: (AuditReportDto) -> Unit,
    onLogOut: () -> Unit
) {
    // Current 7 settings state, initialized to factory defaults (Option A)
    var authCredential by remember { mutableStateOf("default_pin") }
    var transportCrypto by remember { mutableStateOf("mqtt_plain_1883") }
    var networkTopology by remember { mutableStateOf("shared_home_wifi") }
    var remoteExposure by remember { mutableStateOf("upnp_port") }
    var firmwarePolicy by remember { mutableStateOf("manual_never") }
    var dataPrivacy by remember { mutableStateOf("full_cloud_sharing") }
    var actionAuth by remember { mutableStateOf("one_tap_always") }

    // Active Category Tab Index (0: Access, 1: Network, 2: Privacy)
    var selectedTab by remember { mutableIntStateOf(0) }

    // Live Audit State (dynamically recomputed on each toggle)
    var securityScore by remember { mutableFloatStateOf(15.0f) }
    var usabilityScore by remember { mutableFloatStateOf(100.0f) }
    var grade by remember { mutableStateOf("D-") }
    var riskTier by remember { mutableStateOf("High Vulnerability") }

    // Dialog state
    var isDeploying by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableIntStateOf(0) }
    var showScanDialog by remember { mutableStateOf(false) }
    var auditReport by remember { mutableStateOf<AuditReportDto?>(null) }
    var deployError by remember { mutableStateOf<String?>(null) }
    var sessionExpired by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Local score calculation for instantaneous 60fps gauge response
    fun recalculate() {
        var sec = 0f
        var use = 0f

        when (authCredential) {
            "default_pin" -> { sec += 2f; use += 15f }
            "pin_6" -> { sec += 10f; use += 12f }
            "passphrase_biometric" -> { sec += 15f; use += 6f }
        }
        when (transportCrypto) {
            "mqtt_plain_1883" -> { sec += 2f; use += 20f }
            "tls_8883" -> { sec += 16f; use += 16f }
            "mtls_strict" -> { sec += 20f; use += 7f }
        }
        when (networkTopology) {
            "shared_home_wifi" -> { sec += 3f; use += 15f }
            "isolated_vlan" -> { sec += 15f; use += 8f }
        }
        when (remoteExposure) {
            "upnp_port" -> { sec += 2f; use += 20f }
            "cloud_relay" -> { sec += 16f; use += 17f }
            "local_lan" -> { sec += 20f; use += 6f }
        }
        when (firmwarePolicy) {
            "manual_never" -> { sec += 2f; use += 10f }
            "auto_nightly_patch" -> { sec += 10f; use += 7f }
        }
        when (dataPrivacy) {
            "full_cloud_sharing" -> { sec += 2f; use += 10f }
            "anonymized_telemetry" -> { sec += 7f; use += 9f }
            "local_only" -> { sec += 10f; use += 5f }
        }
        when (actionAuth) {
            "one_tap_always" -> { sec += 2f; use += 10f }
            "biometric_step_up" -> { sec += 10f; use += 6f }
        }

        securityScore = sec
        usabilityScore = use

        grade = when {
            sec >= 88f -> "A"
            sec >= 72f -> "B"
            sec >= 50f -> "C"
            else -> "D"
        }

        riskTier = when {
            sec >= 88f -> "Hardened Zero-Trust"
            sec >= 72f -> "Balanced Consumer Protection"
            sec >= 50f -> "Moderate Exposure Risk"
            else -> "High Vulnerability"
        }
    }

    LaunchedEffect(Unit) {
        recalculate()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SafeHome Gateway Setup", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Participant ${NetworkModule.participantId} • Live Auditor", fontSize = 11.sp, color = BlueAccent)
                    }
                },
                actions = {
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Log Out",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateBackground)
            )
        },
        containerColor = SlateBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // COMPACT SLIM AUDITOR POSTURE BAR
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardSurface,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("SECURITY POSTURE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            BadgeGrade(grade)
                        }
                        Text(
                            text = riskTier,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = getScoreColor(securityScore)
                        )
                    }

                    // Compact Dual Progress Meters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Security Meter
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Security", fontSize = 11.sp, color = TextSecondary)
                                Text("${securityScore.toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = getScoreColor(securityScore))
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            val animatedSec by animateFloatAsState(targetValue = securityScore / 100f, animationSpec = tween(400))
                            val secColor by animateColorAsState(targetValue = getScoreColor(securityScore), animationSpec = tween(400))
                            LinearProgressIndicator(
                                progress = { animatedSec },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = secColor,
                                trackColor = BorderLight
                            )
                        }

                        // Convenience Meter
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Convenience", fontSize = 11.sp, color = TextSecondary)
                                Text("${usabilityScore.toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BlueAccent)
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            val animatedUse by animateFloatAsState(targetValue = usabilityScore / 100f, animationSpec = tween(400))
                            LinearProgressIndicator(
                                progress = { animatedUse },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = BlueAccent,
                                trackColor = BorderLight
                            )
                        }
                    }
                }
            }

            // CLEAN CATEGORY TABS
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = SlateBackground,
                contentColor = BlueAccent,
                divider = { HorizontalDivider(color = BorderLight) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("1. Access", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("2. Network", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("3. Privacy", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            // TAB CONTENT AREA (Spacious, Scrollable)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    // TAB 0: ACCESS & IDENTITY
                    0 -> {
                        SettingCardClean(
                            title = "Device Authentication Credential",
                            subtitle = "Controls local lockscreen and brute-force vulnerability",
                            options = listOf(
                                SettingOption("default_pin", "Default PIN (1234)", "Instant setup, but vulnerable to local brute-forcing.", isRisky = true),
                                SettingOption("pin_6", "6-Digit Custom PIN", "Recommended balance of convenience and brute-force resistance."),
                                SettingOption("passphrase_biometric", "Passphrase + Biometrics", "Highest security. Requires fingerprint or long passphrase.")
                            ),
                            selectedKey = authCredential,
                            onSelect = {
                                val prev = authCredential
                                authCredential = it
                                recalculate()
                                MobileTelemetryManager.recordToggle("auth_credential", it)
                                if (prev == "default_pin" && it != "default_pin") {
                                    MobileTelemetryManager.recordWarningReversal("auth_credential", prev, it)
                                }
                            }
                        )

                        SettingCardClean(
                            title = "Action Authorization Threshold",
                            subtitle = "Verification required before triggering switches and automations",
                            options = listOf(
                                SettingOption("one_tap_always", "One-Tap Execution", "Immediate control. Anyone holding the phone can adjust climate and rules.", isRisky = true),
                                SettingOption("biometric_step_up", "Biometric / PIN Step-Up", "Requires fingerprint verification for sensitive actions. High protection.")
                            ),
                            selectedKey = actionAuth,
                            onSelect = {
                                val prev = actionAuth
                                actionAuth = it
                                recalculate()
                                MobileTelemetryManager.recordToggle("action_auth", it)
                                if (prev == "one_tap_always" && it != "one_tap_always") {
                                    MobileTelemetryManager.recordWarningReversal("action_auth", prev, it)
                                }
                            }
                        )
                    }

                    // TAB 1: NETWORK & CONNECTIVITY
                    1 -> {
                        SettingCardClean(
                            title = "Transport Protocol Encryption",
                            subtitle = "Protects sensor readings and commands in transit",
                            options = listOf(
                                SettingOption("mqtt_plain_1883", "Plaintext MQTT (Port 1883)", "Cleartext transmission. Vulnerable to Wi-Fi packet sniffing.", isRisky = true),
                                SettingOption("tls_8883", "Standard TLS Encrypted (Port 8883)", "Protects against network eavesdropping. Minimal CPU overhead."),
                                SettingOption("mtls_strict", "Strict Mutual TLS (mTLS)", "Cryptographic certificates on both hub and app. Enterprise security.")
                            ),
                            selectedKey = transportCrypto,
                            onSelect = {
                                val prev = transportCrypto
                                transportCrypto = it
                                recalculate()
                                MobileTelemetryManager.recordToggle("transport_crypto", it)
                                if (prev == "mqtt_plain_1883" && it != "mqtt_plain_1883") {
                                    MobileTelemetryManager.recordWarningReversal("transport_crypto", prev, it)
                                }
                            }
                        )

                        SettingCardClean(
                            title = "Wi-Fi Network Architecture",
                            subtitle = "Segregates smart devices from personal computers and phones",
                            options = listOf(
                                SettingOption("shared_home_wifi", "Shared Home Wi-Fi Subnet", "Standard Wi-Fi. If a smart plug is breached, personal PCs are accessible.", isRisky = true),
                                SettingOption("isolated_vlan", "Isolated IoT VLAN / Guest Network", "Quarantines smart home hardware to a dedicated network segment.")
                            ),
                            selectedKey = networkTopology,
                            onSelect = {
                                val prev = networkTopology
                                networkTopology = it
                                recalculate()
                                MobileTelemetryManager.recordToggle("network_topology", it)
                                if (prev == "shared_home_wifi" && it != "shared_home_wifi") {
                                    MobileTelemetryManager.recordWarningReversal("network_topology", prev, it)
                                }
                            }
                        )

                        SettingCardClean(
                            title = "Remote Access & Firewall Exposure",
                            subtitle = "Inbound internet connectivity when away from home",
                            options = listOf(
                                SettingOption("upnp_port", "UPnP Router Port Forward", "Automatic remote control, but exposes the hub to internet scanners (Shodan).", isRisky = true),
                                SettingOption("cloud_relay", "Authenticated Cloud Relay Bridge", "Secure outbound relay. No open inbound ports on home router."),
                                SettingOption("local_lan", "Local-Only LAN Mode", "Zero external attack surface. Device only controllable while on home Wi-Fi.")
                            ),
                            selectedKey = remoteExposure,
                            onSelect = {
                                val prev = remoteExposure
                                remoteExposure = it
                                recalculate()
                                MobileTelemetryManager.recordToggle("remote_exposure", it)
                                if (prev == "upnp_port" && it != "upnp_port") {
                                    MobileTelemetryManager.recordWarningReversal("remote_exposure", prev, it)
                                }
                            }
                        )
                    }

                    // TAB 2: SYSTEM & PRIVACY
                    2 -> {
                        SettingCardClean(
                            title = "Firmware Patch Management",
                            subtitle = "Automated defense against discovered software vulnerabilities",
                            options = listOf(
                                SettingOption("manual_never", "Manual Updates Only", "Zero reboot disruption. Leaves known security vulnerabilities unpatched.", isRisky = true),
                                SettingOption("auto_nightly_patch", "Automated Security Patches", "Applies critical patches automatically overnight. Keeps hub hardened.")
                            ),
                            selectedKey = firmwarePolicy,
                            onSelect = {
                                val prev = firmwarePolicy
                                firmwarePolicy = it
                                recalculate()
                                MobileTelemetryManager.recordToggle("firmware_policy", it)
                                if (prev == "manual_never" && it != "manual_never") {
                                    MobileTelemetryManager.recordWarningReversal("firmware_policy", prev, it)
                                }
                            }
                        )

                        SettingCardClean(
                            title = "Cloud Telemetry & Data Privacy",
                            subtitle = "Determines where household occupancy and sensor data is stored",
                            options = listOf(
                                SettingOption("full_cloud_sharing", "Full Cloud Analytics & AI", "Shares full household occupancy telemetry with third-party cloud servers.", isRisky = true),
                                SettingOption("anonymized_telemetry", "Anonymized Diagnostics Only", "Strips identifying metadata before sending diagnostic telemetry."),
                                SettingOption("local_only", "Strictly Local Storage Only", "Complete data privacy. No telemetry or sensor logs ever leave your hub.")
                            ),
                            selectedKey = dataPrivacy,
                            onSelect = {
                                val prev = dataPrivacy
                                dataPrivacy = it
                                recalculate()
                                MobileTelemetryManager.recordToggle("data_privacy", it)
                                if (prev == "full_cloud_sharing" && it != "full_cloud_sharing") {
                                    MobileTelemetryManager.recordWarningReversal("data_privacy", prev, it)
                                }
                            }
                        )
                    }
                }
            }

            // CLEAN STEPPER BOTTOM BAR
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardSurface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedTab > 0) {
                        OutlinedButton(
                            onClick = { selectedTab-- },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Back", fontSize = 14.sp)
                        }
                    }

                    if (selectedTab < 2) {
                        Button(
                            onClick = { selectedTab++ },
                            modifier = Modifier
                                .weight(if (selectedTab > 0) 1.5f else 1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
                        ) {
                            Text(
                                text = if (selectedTab == 0) "Next: Network Security" else "Next: Privacy & Updates",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Button(
                            onClick = {
                                showScanDialog = true
                                isDeploying = true
                                scope.launch {
                                    scanProgress = 1
                                    delay(700)
                                    scanProgress = 2
                                    delay(800)
                                    scanProgress = 3
                                    delay(600)

                                    val settingsPayload = CommissionSettingsDto(
                                        authCredential = authCredential,
                                        transportCrypto = transportCrypto,
                                        networkTopology = networkTopology,
                                        remoteExposure = remoteExposure,
                                        firmwarePolicy = firmwarePolicy,
                                        dataPrivacy = dataPrivacy,
                                        actionAuth = actionAuth
                                    )
                                    val metricsPayload = CommissionMetricsDto(
                                        toggleCount = MobileTelemetryManager.toggleCount,
                                        warningReversals = MobileTelemetryManager.warningReversals
                                    )

                                    auditReport = null
                                    deployError = null
                                    sessionExpired = false

                                    try {
                                        val res = NetworkModule.apiService.deployGateway(
                                            DeployRequest(settingsPayload, metricsPayload)
                                        )
                                        val report = res.body()?.auditReport
                                        if (res.isSuccessful && res.body()?.success == true && report != null) {
                                            auditReport = report
                                        } else if (res.code() == 401) {
                                            sessionExpired = true
                                            deployError = "Your session is no longer valid on the server. Please sign in again and redo the setup."
                                        } else {
                                            deployError = "The server could not record your setup (error ${res.code()}). Please try again."
                                        }
                                    } catch (e: Exception) {
                                        deployError = "Could not reach the server. Check your connection and try again."
                                    } finally {
                                        isDeploying = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(if (selectedTab > 0) 1.5f else 1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                        ) {
                            Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Deploy Gateway", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // PENETRATION SCAN & COMMISSIONING CERTIFICATE DIALOG
    if (showScanDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeploying) showScanDialog = false
            },
            title = {
                Text(
                    text = when {
                        isDeploying -> "Security Audit in Progress"
                        auditReport != null -> "Commissioning Certificate"
                        sessionExpired -> "Session Expired"
                        else -> "Deployment Failed"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                val report = auditReport
                if (isDeploying) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ScanRow("Testing Transport Encryption...", scanProgress >= 1)
                        ScanRow("Probing Remote Port Exposure...", scanProgress >= 2)
                        ScanRow("Evaluating Subnet Isolation...", scanProgress >= 3)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                } else if (report != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SlateBackground),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Audit Grade", fontSize = 12.sp, color = TextSecondary)
                                    BadgeGrade(report.grade)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Security Health", fontSize = 12.sp, color = TextSecondary)
                                    Text("${report.securityScore.toInt()}%", fontWeight = FontWeight.Bold, color = getScoreColor(report.securityScore))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Convenience Index", fontSize = 12.sp, color = TextSecondary)
                                    Text("${report.usabilityScore.toInt()}%", fontWeight = FontWeight.Bold, color = BlueAccent)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Posture Classification", fontSize = 12.sp, color = TextSecondary)
                                    Text(report.riskTier, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (report.risks.isNotEmpty()) {
                            Text("Identified Residual Risks:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                            report.risks.forEach { r ->
                                Text("• ${r.name}: ${r.desc}", fontSize = 11.sp, color = TextSecondary)
                            }
                        } else {
                            Text("Zero high-severity vulnerabilities detected.", fontSize = 12.sp, color = SuccessGreen)
                        }
                    }
                } else {
                    Text(
                        text = deployError ?: "Something went wrong while deploying.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                val report = auditReport
                if (!isDeploying) {
                    if (report != null) {
                        Button(
                            onClick = {
                                showScanDialog = false
                                NetworkModule.isCommissioned = true
                                onCommissionSuccess(report)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Open Active Hub Dashboard")
                        }
                    } else if (sessionExpired) {
                        Button(
                            onClick = {
                                showScanDialog = false
                                NetworkModule.clearSession()
                                onLogOut()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Sign In Again")
                        }
                    } else {
                        Button(
                            onClick = { showScanDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Back to Settings")
                        }
                    }
                }
            }
        )
    }

    // LOG OUT CONFIRMATION DIALOG
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out?", fontWeight = FontWeight.SemiBold) },
            text = { Text("Are you sure you want to log out of your session? You can sign back in at any time.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        MobileTelemetryManager.logLogout {
                            NetworkModule.clearSession()
                            onLogOut()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// CLEAN HELPER COMPOSABLES
// ---------------------------------------------------------------------------
data class SettingOption(
    val key: String,
    val title: String,
    val desc: String,
    val isRisky: Boolean = false
)

/**
 * Modern Setting Card with clean selectable option tiles.
 * Replaces bulky radio buttons with sleek Material 3 clickable tiles.
 */
@Composable
private fun SettingCardClean(
    title: String,
    subtitle: String,
    options: List<SettingOption>,
    selectedKey: String,
    onSelect: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                Text(subtitle, fontSize = 12.sp, color = TextSecondary)
            }

            // Selectable Tiles
            options.forEach { opt ->
                val isSelected = opt.key == selectedKey
                val borderColor = if (isSelected) BlueAccent else BorderLight
                val bgColor = if (isSelected) LightBlueBg else SlateBackground

                Surface(
                    onClick = { onSelect(opt.key) },
                    shape = RoundedCornerShape(10.dp),
                    color = bgColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = opt.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (isSelected) BlueAccent else TextPrimary
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (opt.isRisky) {
                                    Surface(
                                        color = WarningLight,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Weak",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WarningAmber,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = BlueAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Contextual description
                        Text(
                            text = opt.desc,
                            fontSize = 11.5.sp,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanRow(text: String, isComplete: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        if (isComplete) {
            Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
        } else {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun BadgeGrade(grade: String) {
    val bg = when (grade) {
        "A" -> SuccessLight
        "B" -> Color(0xFFE0F2FE)
        "C" -> WarningLight
        else -> DangerLight
    }
    val fg = when (grade) {
        "A" -> SuccessGreen
        "B" -> BlueAccent
        "C" -> WarningAmber
        else -> DangerRed
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = "GRADE $grade",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

private fun getScoreColor(score: Float): Color {
    return when {
        score >= 72f -> SuccessGreen
        score >= 50f -> WarningAmber
        else -> DangerRed
    }
}
