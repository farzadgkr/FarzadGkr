package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import android.annotation.SuppressLint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GlassesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            // Apply a premium, high-tech dark theme manually for best control
            CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
                MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                    GlassesAppScreen(viewModel = viewModel)
                }
            }
        }
    }
}

// Custom Sophisticated Dark Design Theme Colors
object TechTheme {
    val DeepSlate = Color(0xFF0D0D0D)      // Deep black-gray page background
    val GlassSurface = Color(0xFF1A1C1E)   // Sophisticated surface card color
    val CyberCyan = Color(0xFFA8C7FA)      // Elegant periwinkle blue accent color
    val HotPink = Color(0xFFE2808A)        // Soft muted rose accent for warning/sim states
    val NeonGreen = Color(0xFF10B981)      // Emerald-500 for connected pulse/indicator
    val TerminalBlack = Color(0xFF0A0A0A)  // Rich dark console panel background
    val GlowWhite = Color(0xFFE3E3E3)      // Main high-contrast text color
    val SlateMuted = Color(0xFF71717A)     // Zinc-500 modern muted text color
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassesAppScreen(viewModel: GlassesViewModel) {
    val context = LocalContext.current
    
    // Permission launcher
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    var hasPermissions by remember {
        mutableStateOf(
            permissionsToRequest.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
        if (hasPermissions) {
            viewModel.bleManager.addLog("تمامی مجوزهای بلوتوث با موفقیت صادر شدند.")
        } else {
            viewModel.bleManager.addLog("خطا: مجوزهای بلوتوث رد شدند. بدون مجوز نمی‌توان به عینک متصل شد.")
            Toast.makeText(context, "برای کار با عینک، تایید مجوزهای بلوتوث الزامی است.", Toast.LENGTH_LONG).show()
        }
    }

    // Collect flow states
    val connectionState by viewModel.bleManager.connectionState.collectAsState()
    val discoveredDevices by viewModel.bleManager.discoveredDevices.collectAsState()
    val batteryLevel by viewModel.bleManager.batteryLevel.collectAsState()
    val transferProgress by viewModel.bleManager.transferProgress.collectAsState()
    val logs by viewModel.bleManager.logs.collectAsState()
    val lastCapturedPhoto by viewModel.bleManager.lastCapturedPhoto.collectAsState()
    val wifiSSID by viewModel.bleManager.wifiSSID.collectAsState()
    val isWifiConnectedToGlasses by viewModel.bleManager.isWifiConnectedToGlasses.collectAsState()

    // Automatic permission request on initial launch
    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = TechTheme.DeepSlate,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Logo Circle Container
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(TechTheme.GlassSurface)
                                .border(1.dp, Color(0xFF2D2F31), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility, // Represents vision / glasses
                                contentDescription = "نماد عینک هوشمند",
                                tint = TechTheme.CyberCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "دستیار عینک G300",
                                color = TechTheme.GlowWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val isConnected = connectionState == GlassesBLEManager.ConnectionState.CONNECTED
                                val isScanning = connectionState == GlassesBLEManager.ConnectionState.SCANNING
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isConnected) TechTheme.NeonGreen
                                            else if (isScanning) TechTheme.CyberCyan
                                            else TechTheme.HotPink
                                        )
                                )
                                Text(
                                    text = if (isConnected) "متصل"
                                           else if (isScanning) "درحال جستجو..."
                                           else "قطع اتصال",
                                    color = if (isConnected) TechTheme.NeonGreen
                                            else if (isScanning) TechTheme.CyberCyan
                                            else TechTheme.SlateMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Battery indicator
                    if (connectionState == GlassesBLEManager.ConnectionState.CONNECTED && batteryLevel != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = "باتری عینک",
                                tint = TechTheme.NeonGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$batteryLevel%",
                                color = TechTheme.NeonGreen,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TechTheme.DeepSlate
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // 1. Connection Card (Unified BLE and WiFi)
                ConnectionControlPanel(
                    connectionState = connectionState,
                    discoveredDevices = discoveredDevices,
                    wifiSSID = wifiSSID,
                    isWifiConnectedToGlasses = isWifiConnectedToGlasses,
                    hasPermissions = hasPermissions,
                    onRequestPermissions = { permissionLauncher.launch(permissionsToRequest) },
                    onStartScan = { viewModel.bleManager.startScanning() },
                    onStopScan = { viewModel.bleManager.stopScanning() },
                    onConnect = { device -> viewModel.bleManager.connectToDevice(device) },
                    onDisconnect = { viewModel.bleManager.disconnect() },
                    onRefreshWifi = { viewModel.bleManager.updateWifiStatus() }
                )
            }

            item {
                // 2. Transferred Image Panel
                PhotoViewerCard(
                    lastCapturedPhoto = lastCapturedPhoto,
                    transferProgress = transferProgress,
                    onTriggerManualCapture = { viewModel.bleManager.triggerManualCapture() },
                    isConnected = connectionState == GlassesBLEManager.ConnectionState.CONNECTED
                )
            }

            item {
                // Recent Albums & Intelligent features Row
                RecentAlbumsRow(
                    onAddLog = { text -> viewModel.bleManager.addLog(text) }
                )
            }

            item {
                // 3. Real-time BLE handshakes (Monospace terminal style)
                TerminalLogPanel(
                    logs = logs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun RecentAlbumsRow(
    onAddLog: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "آلبوم‌های اخیر",
                color = TechTheme.SlateMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "مشاهده همه",
                color = TechTheme.CyberCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onAddLog("نمایش تمامی آلبوم‌ها و قابلیت‌های عینک") }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: بهینه‌سازی هوشمند
            Column(
                modifier = Modifier
                    .width(120.dp)
                    .height(115.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(TechTheme.GlassSurface)
                    .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(20.dp))
                    .clickable { onAddLog("قابلیت بهینه‌سازی خودکار تصاویر فعال شد.") }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TechTheme.CyberCyan.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "بهینه‌سازی هوشمند",
                        tint = TechTheme.CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "بهینه‌سازی هوشمند",
                    color = TechTheme.GlowWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // Card 2: تاریخچه انتقال
            Column(
                modifier = Modifier
                    .width(120.dp)
                    .height(115.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(TechTheme.GlassSurface)
                    .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(20.dp))
                    .clickable { onAddLog("باز کردن بخش تاریخچه انتقال عکس‌ها") }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TechTheme.CyberCyan.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "تاریخچه انتقال",
                        tint = TechTheme.CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "تاریخچه انتقال",
                    color = TechTheme.GlowWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // Card 3: پشتیبان‌گیری ابری
            Column(
                modifier = Modifier
                    .width(120.dp)
                    .height(115.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(TechTheme.GlassSurface)
                    .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(20.dp))
                    .clickable { onAddLog("پشتیبان‌گیری ابری تصاویر عینک فعال شد.") }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TechTheme.SlateMuted.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "پشتیبان‌گیری ابری",
                        tint = TechTheme.SlateMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "پشتیبان‌گیری ابری",
                    color = TechTheme.SlateMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ConnectionControlPanel(
    connectionState: GlassesBLEManager.ConnectionState,
    discoveredDevices: List<android.bluetooth.BluetoothDevice>,
    wifiSSID: String,
    isWifiConnectedToGlasses: Boolean,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (android.bluetooth.BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onRefreshWifi: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // BLE CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("connection_panel"),
            colors = CardDefaults.cardColors(containerColor = TechTheme.GlassSurface),
            border = BorderStroke(1.dp, Color(0x0DFFFFFF)), // white/5 border
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = "بلوتوث",
                            tint = TechTheme.CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "اتصال بلوتوث (BLE) عینک G300",
                            color = TechTheme.GlowWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Small pulsing scanning dot
                    if (connectionState == GlassesBLEManager.ConnectionState.SCANNING) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 0.8f,
                            targetValue = 1.3f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse_scale"
                        )
                        Box(
                            modifier = Modifier
                                .scale(scale)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(TechTheme.CyberCyan)
                        )
                    }
                }

                if (!hasPermissions) {
                    Button(
                        onClick = onRequestPermissions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("grant_permission_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = TechTheme.HotPink),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("اعطای مجوزهای موقعیت و بلوتوث", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                } else {
                    when (connectionState) {
                        GlassesBLEManager.ConnectionState.DISCONNECTED -> {
                            Button(
                                onClick = onStartScan,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("scan_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = TechTheme.CyberCyan),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "جستجو", tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("شروع جستجوی دستگاه‌های بلوتوث", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                        GlassesBLEManager.ConnectionState.SCANNING -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onStopScan,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("stop_scan_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = TechTheme.GlassSurface),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("توقف جستجو", color = TechTheme.GlowWhite, fontWeight = FontWeight.Bold)
                                }

                                Text(
                                    text = "دستگاه‌های بلوتوثی یافت شده نزدیک شما:",
                                    color = TechTheme.SlateMuted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )

                                if (discoveredDevices.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(60.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = TechTheme.CyberCyan, modifier = Modifier.size(24.dp))
                                    }
                                } else {
                                    discoveredDevices.forEach { device ->
                                        @SuppressLint("MissingPermission")
                                        val name = device.name ?: "دستگاه ناشناس"
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(TechTheme.TerminalBlack)
                                                .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(12.dp))
                                                .clickable { onConnect(device) }
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(text = name, color = TechTheme.GlowWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(text = device.address, color = TechTheme.SlateMuted, fontSize = 11.sp)
                                            }
                                            Icon(
                                                imageVector = Icons.Default.Link,
                                                contentDescription = "اتصال",
                                                tint = TechTheme.CyberCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        GlassesBLEManager.ConnectionState.CONNECTING -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(TechTheme.GlassSurface)
                                    .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(color = TechTheme.CyberCyan, modifier = Modifier.size(18.dp))
                                    Text("در حال برقراری ارتباط با عینک...", color = TechTheme.CyberCyan, fontSize = 13.sp)
                                }
                            }
                        }
                        GlassesBLEManager.ConnectionState.CONNECTED -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(TechTheme.NeonGreen.copy(alpha = 0.05f))
                                        .border(1.dp, TechTheme.NeonGreen.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "متصل", tint = TechTheme.NeonGreen)
                                        Text("بلوتوث عینک متصل است", color = TechTheme.NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Button(
                                    onClick = onDisconnect,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("disconnect_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = TechTheme.GlassSurface),
                                    border = BorderStroke(1.dp, TechTheme.HotPink.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("قطع اتصال", fontWeight = FontWeight.Bold, color = TechTheme.HotPink)
                                }
                            }
                        }
                    }
                }
            }
        }

        // WIFI CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wifi_panel"),
            colors = CardDefaults.cardColors(containerColor = TechTheme.GlassSurface),
            border = BorderStroke(1.dp, Color(0x0DFFFFFF)), // white/5 border
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "وای‌فای",
                            tint = TechTheme.CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "اتصال وای‌فای (Wi-Fi) عینک",
                            color = TechTheme.GlowWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Refresh Button
                    IconButton(
                        onClick = onRefreshWifi,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "به‌روزرسانی",
                            tint = TechTheme.CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Wi-Fi Connection Status Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isWifiConnectedToGlasses) TechTheme.NeonGreen.copy(alpha = 0.05f)
                            else TechTheme.TerminalBlack
                        )
                        .border(
                            width = 1.dp,
                            color = if (isWifiConnectedToGlasses) TechTheme.NeonGreen.copy(alpha = 0.2f) else Color(0x0DFFFFFF),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isWifiConnectedToGlasses) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = "وضعیت",
                            tint = if (isWifiConnectedToGlasses) TechTheme.NeonGreen else TechTheme.SlateMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = if (isWifiConnectedToGlasses) "متصل به وای‌فای عینک" else "عدم اتصال به وای‌فای عینک",
                                color = if (isWifiConnectedToGlasses) TechTheme.NeonGreen else TechTheme.GlowWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "وای‌فای متصل فعلی: $wifiSSID",
                                color = TechTheme.SlateMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Text(
                    text = "جهت انتقال تصاویر ثبت شده با کیفیت فوق‌العاده بالا و سرعت سریع، باید به هات‌اسپات وای‌فای عینک خود (G300_xxx) متصل شوید.",
                    color = TechTheme.SlateMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Button(
                    onClick = {
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "خطا در باز کردن تنظیمات وای‌فای", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TechTheme.GlassSurface),
                    border = BorderStroke(1.dp, TechTheme.CyberCyan.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "تنظیمات", tint = TechTheme.CyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("باز کردن تنظیمات وای‌فای گوشی", color = TechTheme.CyberCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun PhotoViewerCard(
    lastCapturedPhoto: Bitmap?,
    transferProgress: Float?,
    onTriggerManualCapture: () -> Unit,
    isConnected: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("photo_viewer_card"),
        colors = CardDefaults.cardColors(containerColor = TechTheme.GlassSurface),
        border = BorderStroke(
            width = 1.dp,
            color = if (transferProgress != null) TechTheme.CyberCyan else Color(0x0DFFFFFF)
        ),
        shape = RoundedCornerShape(32.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(32.dp))
        ) {
            // Background Image or Placeholder
            if (lastCapturedPhoto != null) {
                Image(
                    bitmap = lastCapturedPhoto.asImageBitmap(),
                    contentDescription = "عکس دریافتی از عینک G300",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Aesthetic placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF27272A)), // zinc-800
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.alpha(0.3f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "بدون تصویر",
                            tint = Color.White,
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }
            }

            // Gradient Scrim from black at bottom to transparent
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // Content Overlay aligned to bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Farsi Title Layout
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "آخرین تصویر انتقال یافته",
                        color = TechTheme.CyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (lastCapturedPhoto != null) {
                            "تصویر ثبت شده عینک"
                        } else {
                            "در انتظار ثبت عکس..."
                        },
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                }

                // Metadata Pill & Control Button
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Metadata pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (lastCapturedPhoto != null) "4K UHD • JPEG" else "کیفیت اصلی",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Floating circular capture trigger button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) TechTheme.CyberCyan else TechTheme.SlateMuted.copy(alpha = 0.3f))
                            .clickable(enabled = isConnected) {
                                onTriggerManualCapture()
                            }
                            .testTag("manual_capture_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "ثبت تصویر",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // High-Tech progress bar overlay when transferring
            if (transferProgress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "در حال انتقال عکس با کیفیت اصلی...",
                            color = TechTheme.CyberCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        LinearProgressIndicator(
                            progress = { transferProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = TechTheme.CyberCyan,
                            trackColor = TechTheme.SlateMuted.copy(alpha = 0.2f),
                        )
                        
                        Text(
                            text = "${(transferProgress * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalLogPanel(logs: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    // Scroll to top of list when a new log arrives
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Card(
        modifier = modifier.testTag("terminal_panel"),
        colors = CardDefaults.cardColors(containerColor = TechTheme.TerminalBlack),
        border = BorderStroke(1.dp, Color(0x0DFFFFFF)), // white/5 border
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeveloperMode,
                        contentDescription = "لاگ سیستم",
                        tint = TechTheme.SlateMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "کنسول خط فرمان بلوتوث و هندشیک G300",
                        color = TechTheme.SlateMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Text(
                    text = "LIVE ●",
                    color = TechTheme.CyberCyan.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Divider(color = TechTheme.SlateMuted.copy(alpha = 0.2f), modifier = Modifier.padding(bottom = 8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { log ->
                    Text(
                        text = log,
                        color = if (log.contains("خطا")) TechTheme.HotPink 
                                else if (log.contains("موفقیت") || log.contains("متصل")) TechTheme.NeonGreen
                                else if (log.contains("شبیه‌ساز")) TechTheme.CyberCyan.copy(alpha = 0.8f)
                                else TechTheme.GlowWhite.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
