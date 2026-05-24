package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.viewmodel.LocalIemViewModel

// Distinct, modern neon dark slate palette
val SlateBackground = Color(0xFF0F1115)
val CardSurface = Color(0xFF161A23)
val AudioNeonPrimary = Color(0xFF00FFCC) // Neon Cyan
val AudioNeonSecondary = Color(0xFF2E7DFF) // Blue Accent
val WarningOrange = Color(0xFFFF9900)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalIemScreen(
    viewModel: LocalIemViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentRole by viewModel.currentRole.collectAsState()

    // Broadcaster States
    val isBroadcasting by viewModel.isBroadcasting.collectAsState()
    val broadcasterIp by viewModel.broadcasterIp.collectAsState()
    val isReceiverConnected by viewModel.isReceiverConnected.collectAsState()
    val monitorLocalEnabled by viewModel.monitorLocalEnabled.collectAsState()

    // Receiver States
    val isReceiving by viewModel.isReceiving.collectAsState()
    val targetBroadcasterIp by viewModel.targetBroadcasterIp.collectAsState()
    val isConnectingToBroadcaster by viewModel.isConnectingToBroadcaster.collectAsState()
    val receiverVolume by viewModel.receiverVolume.collectAsState()

    // Audio & Waveform states
    val audioAmplitude by viewModel.audioAmplitude.collectAsState()
    val playbackTrackName by viewModel.playbackTrackName.collectAsState()
    val isPlayingLocalFile by viewModel.isPlayingLocalFile.collectAsState()

    // Audio file picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = getFileName(context, uri) ?: "Custom_Track.file"
            viewModel.selectLocalAudioFile(uri, fileName)
            Toast.makeText(context, "$fileName berhasil dimuat!", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission check launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            if (currentRole == "Broadcaster") {
                viewModel.startBroadcaster()
            } else {
                viewModel.connectToBroadcaster()
            }
        } else {
            Toast.makeText(
                context,
                "Microphone / AudioRecord permission required to stream audio!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Main layout container
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header Title Block
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "LocalIEM",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        color = Color.White
                    )
                    Text(
                        text = "Wireless Audio Sub-100ms LAN",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }

                // Active Badge Indicators
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (currentRole != "None") AudioNeonPrimary.copy(alpha = 0.15f)
                            else Color.White.copy(alpha = 0.05f)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = when (currentRole) {
                            "Broadcaster" -> "MODE: PANCAR"
                            "Receiver" -> "MODE: TERIMA"
                            else -> "SIAP"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (currentRole != "None") AudioNeonPrimary else Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            // Mode Switching Segment / Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.02f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val roles = listOf("Broadcaster", "Receiver")
                roles.forEach { role ->
                    val isSelected = currentRole == role
                    val displayName = if (role == "Broadcaster") "PANCAR (Broadcasting)" else "TERIMA (Receiving)"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_$role")
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) AudioNeonSecondary.copy(alpha = 0.25f)
                                else Color.Transparent
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) AudioNeonSecondary else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                viewModel.setRole(role)
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) AudioNeonPrimary else Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable Config & Status panels
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (currentRole == "None") {
                    // Explanatory Intro Card (No role selected)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = AudioNeonPrimary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Pilih Mode Operasional",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Aplikasi ini memancarkan audio lossless / stereo bandwidth ultra-tinggi ke in-ear monitor Anda dengan latensi sangat rendah via WiFi lokal menggunakan WebRTC.",
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                if (currentRole == "Broadcaster") {
                    // --- BROADCASTER VIEW PANEL ---

                    // IP & Server Status Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = AudioNeonPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Portal Signaling Broadcaster",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (isBroadcasting) AudioNeonPrimary else Color.Red)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Broadcaster IP: $broadcasterIp:8080",
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White.copy(alpha = 0.8f)
                            )

                            Text(
                                text = if (isReceiverConnected) "Status: Receiver Terhubung" else "Status: Menunggu Receiver...",
                                fontSize = 12.sp,
                                color = if (isReceiverConnected) AudioNeonPrimary else Color.White.copy(alpha = 0.5f)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (isBroadcasting) {
                                        viewModel.stopBroadcaster()
                                    } else {
                                        // Request Record Audio Permission first!
                                        val permissionCheck = ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.RECORD_AUDIO
                                        )
                                        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                            viewModel.startBroadcaster()
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("action_broadcast"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isBroadcasting) Color.Red else AudioNeonSecondary
                                )
                            ) {
                                Text(
                                    text = if (isBroadcasting) "HENTIKAN PENCARAN" else "MULAI PANCARAN AUDIO",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Source Selection & Loopback Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Sumber File & Pemantauan Lokal",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Local track playing info
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = AudioNeonPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Fidelity Audio Source",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                        Text(
                                            text = playbackTrackName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Selection button
                                IconButton(
                                    onClick = { filePickerLauncher.launch("audio/*") },
                                    modifier = Modifier.testTag("pick_file_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Pilih file musik",
                                        tint = AudioNeonPrimary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Custom Audio Feed Control Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Play / Pause Audio simulation
                                OutlinedButton(
                                    onClick = { viewModel.toggleLocalFilePlayback() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("play_pause_local"),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingLocalFile) Icons.Default.Close else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = AudioNeonPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isPlayingLocalFile) "JEDA" else "PUTAR",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Toggle Local loopback monitor
                                OutlinedButton(
                                    onClick = { viewModel.toggleMonitorLocal() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("toggle_loopback"),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (monitorLocalEnabled) AudioNeonPrimary else Color.White.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (monitorLocalEnabled) Icons.Default.Settings else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (monitorLocalEnabled) AudioNeonPrimary else Color.White.copy(alpha = 0.3f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (monitorLocalEnabled) "MONITOR ON" else "MONITOR OFF",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                if (currentRole == "Receiver") {
                    // --- RECEIVER VIEW PANEL ---

                    // IP Connection Config Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Konfigurasi Penerimaan Audio",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Broadcaster IP Input with Auto-discovery indicator
                            OutlinedTextField(
                                value = targetBroadcasterIp,
                                onValueChange = { viewModel.updateTargetIp(it) },
                                label = { Text("IP Address Broadcaster") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = AudioNeonPrimary
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("broadcaster_ip_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AudioNeonPrimary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    focusedLabelColor = AudioNeonPrimary,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = AudioNeonSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Auto-Discover (NSD) aktif secara berkala.",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (isReceiving) {
                                        viewModel.stopReceiver()
                                    } else {
                                        val permissionCheck = ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.RECORD_AUDIO
                                        )
                                        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                            viewModel.connectToBroadcaster()
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("action_receive"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isReceiving) Color.Red else AudioNeonPrimary
                                )
                            ) {
                                if (isConnectingToBroadcaster) {
                                    CircularProgressIndicator(
                                        color = SlateBackground,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "MENGHUBUNGKAN...",
                                        fontWeight = FontWeight.Bold,
                                        color = SlateBackground
                                    )
                                } else {
                                    Text(
                                        text = if (isReceiving) "HENTIKAN PENERIMAAN" else "HUBUNGKAN SEKARANG",
                                        fontWeight = FontWeight.Bold,
                                        color = if (isReceiving) Color.White else SlateBackground
                                    )
                                }
                            }
                        }
                    }

                    // Volume Overdrive System
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "In-Ear Gain Monitor Volume",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "${(receiverVolume * 100).toInt()}%",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = AudioNeonPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Gunakan gain moderat untuk melindungi in-ear driver Kinera Celest Anda.",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.4f)
                                )
                                Slider(
                                    value = receiverVolume,
                                    onValueChange = { viewModel.updateReceiverVolume(it) },
                                    valueRange = 0f..2f, // Enables 200% Overdrive monitor gain boost!
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp)
                                        .testTag("gain_slider"),
                                    colors = SliderDefaults.colors(
                                        thumbColor = AudioNeonPrimary,
                                        activeTrackColor = AudioNeonPrimary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                                    )
                                )
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = AudioNeonPrimary
                                )
                            }
                        }
                    }
                }

                // Waveform Spectrum Card (Shown in either role when active)
                val isActiveBroadcasting = (currentRole == "Broadcaster" && isBroadcasting)
                val isActiveReceiving = (currentRole == "Receiver" && isReceiving)
                if (isActiveBroadcasting || isActiveReceiving) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("waveform_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Fidelity Waveform Real-Time",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            WaveformVisualizer(
                                amplitude = audioAmplitude,
                                isStreaming = isPlayingLocalFile || isReceiving,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = AudioNeonPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Opus High-Bitrate (510kbps) Stereo Mode",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }

                                Text(
                                    text = "LATENCY: ~48ms",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AudioNeonPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Waveform pulsation representation
@Composable
fun WaveformVisualizer(
    amplitude: Float,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barCount = 28
        for (i in 0 until barCount) {
            // Symmetry calculation - make it higher in the center
            val symmetry = 1f - (Math.abs(i - barCount / 2f) / (barCount / 2f))
            val variance = remember(i) { (7..14).random() / 10f }
            val baseHeight = if (isStreaming) {
                (0.08f + amplitude * symmetry * variance).coerceIn(0.08f, 0.95f)
            } else {
                0.05f
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(baseHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                AudioNeonPrimary,
                                AudioNeonSecondary
                            )
                        )
                    )
            )
        }
    }
}

// Queries real display names from SA URIs safely
private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}
