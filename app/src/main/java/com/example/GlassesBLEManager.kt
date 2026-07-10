package com.example

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("MissingPermission")
class GlassesBLEManager(private val context: Context) {

    companion object {
        private const val TAG = "GlassesBLEManager"
        
        // HeyCyan / G300 BLE UUID constants
        val SERVICE_UUID: UUID = UUID.fromString("0000fee7-0000-1000-8000-00805f9b34fb")
        val CHAR_NOTIFY_UUID: UUID = UUID.fromString("0000fee8-0000-1000-8000-00805f9b34fb")
        val CHAR_WRITE_UUID: UUID = UUID.fromString("0000fee9-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter
    private val leScanner = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    // State flows for UI
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel: StateFlow<Int?> = _batteryLevel

    private val _transferProgress = MutableStateFlow<Float?>(null)
    val transferProgress: StateFlow<Float?> = _transferProgress

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _lastCapturedPhoto = MutableStateFlow<Bitmap?>(null)
    val lastCapturedPhoto: StateFlow<Bitmap?> = _lastCapturedPhoto

    // WiFi States
    private val _wifiSSID = MutableStateFlow("قطع اتصال")
    val wifiSSID: StateFlow<String> = _wifiSSID

    private val _isWifiConnectedToGlasses = MutableStateFlow(false)
    val isWifiConnectedToGlasses: StateFlow<Boolean> = _isWifiConnectedToGlasses

    // Buffers for receiving image bytes
    private var fileTransferBuffer = ByteArrayOutputStream()
    private var currentFileId: Int = -1
    private var expectedBytes: Int = 0
    private val scope = CoroutineScope(Dispatchers.Main)

    enum class ConnectionState {
        DISCONNECTED,
        SCANNING,
        CONNECTING,
        CONNECTED
    }

    init {
        addLog("برنامه آماده اتصال به عینک G300")
        updateWifiStatus()
    }

    fun addLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        scope.launch {
            _logs.value = listOf("[$time] $message") + _logs.value.take(49)
        }
        Log.d(TAG, message)
    }

    fun updateWifiStatus() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val info = wifiManager?.connectionInfo
            var ssid = info?.ssid ?: ""
            
            // Clean up surrounding quotes
            ssid = ssid.replace("\"", "")
            
            if (ssid == "<unknown ssid>" || ssid.isEmpty() || ssid == "0x") {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                val activeNetwork = connectivityManager?.activeNetwork
                val caps = connectivityManager?.getNetworkCapabilities(activeNetwork)
                if (caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    ssid = "شبکه وای‌فای"
                } else {
                    ssid = "قطع اتصال"
                }
            }
            
            _wifiSSID.value = ssid
            
            val isGlasses = ssid.contains("G300", ignoreCase = true) || 
                            ssid.contains("Cyan", ignoreCase = true) || 
                            ssid.contains("SmartGlasses", ignoreCase = true) ||
                            ssid.contains("Glasses", ignoreCase = true)
            
            _isWifiConnectedToGlasses.value = isGlasses
        } catch (e: Exception) {
            _wifiSSID.value = "نامشخص"
            _isWifiConnectedToGlasses.value = false
        }
    }

    fun startScanning() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            addLog("خطا: بلوتوث گوشی غیرفعال است.")
            return
        }

        addLog("در حال جستجوی تمامی عینک‌ها و دستگاه‌های بلوتوثی...")
        _connectionState.value = ConnectionState.SCANNING
        _discoveredDevices.value = emptyList()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            // Scan without a service UUID filter so we can discover any device (solves connection issues)
            leScanner?.startScan(null, settings, scanCallback)
            
            // Auto stop scan after 15 seconds to save battery
            Handler(Looper.getMainLooper()).postDelayed({
                if (_connectionState.value == ConnectionState.SCANNING) {
                    stopScanning()
                    addLog("جستجو به پایان رسید.")
                }
            }, 15000)
        } catch (e: SecurityException) {
            addLog("خطا: مجوز بلوتوث صادر نشده است.")
            _connectionState.value = ConnectionState.DISCONNECTED
        } catch (e: Exception) {
            addLog("خطا در شروع جستجو: ${e.message}")
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    fun stopScanning() {
        try {
            leScanner?.stopScan(scanCallback)
            if (_connectionState.value == ConnectionState.SCANNING) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (device !in _discoveredDevices.value) {
                _discoveredDevices.value = _discoveredDevices.value + device
                val name = device.name ?: "دستگاه ناشناس"
                addLog("دستگاه یافت شد: $name (${device.address})")
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            addLog("خطا در جستجوی بلوتوث: کد $errorCode")
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        stopScanning()
        addLog("در حال اتصال به ${device.name ?: "عینک G300"}...")
        _connectionState.value = ConnectionState.CONNECTING

        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        } catch (e: SecurityException) {
            addLog("خطا: عدم دسترسی لازم برای اتصال بلوتوث")
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    fun disconnect() {
        addLog("در حال قطع اتصال...")
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
        }
        bluetoothGatt = null
        writeCharacteristic = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _batteryLevel.value = null
        _transferProgress.value = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                addLog("عینک متصل شد! در حال شناسایی سرویس‌ها...")
                _connectionState.value = ConnectionState.CONNECTED
                try {
                    gatt.discoverServices()
                } catch (e: SecurityException) {
                    addLog("خطا در شروع شناسایی سرویس‌ها")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                addLog("اتصال قطع شد.")
                disconnect()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            addLog("سرعت اتصال بهینه شد (MTU: $mtu).")
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("سرویس‌های عینک شناسایی شدند.")
                
                // Request MTU to speed up image transmission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        addLog("در حال بهینه‌سازی سرعت اتصال (MTU)...")
                        gatt.requestMtu(512)
                    } catch (e: SecurityException) {
                        addLog("خطا در درخواست بهینه‌سازی سرعت")
                    }
                }

                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    val notifyChar = service.getCharacteristic(CHAR_NOTIFY_UUID)
                    writeCharacteristic = service.getCharacteristic(CHAR_WRITE_UUID)

                    if (notifyChar != null) {
                        try {
                            // Enable notifications locally
                            gatt.setCharacteristicNotification(notifyChar, true)

                            // Enable notifications on the G300 device side (Client Characteristic Configuration Descriptor)
                            val descriptor = notifyChar.getDescriptor(
                                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                            )
                            if (descriptor != null) {
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(descriptor)
                            }
                            addLog("کانال دریافت عینک با موفقیت فعال شد. آماده دریافت عکس.")
                        } catch (e: SecurityException) {
                            addLog("خطا در تنظیم دریافت نوتیفیکیشن")
                        }
                    } else {
                        addLog("خطا: کاراکتریستیک دریافت داده پیدا نشد.")
                    }
                } else {
                    addLog("خطا: سرویس اختصاصی عینک G300 یافت نشد.")
                }
            } else {
                addLog("کشف سرویس‌ها با خطا مواجه شد: کد $status")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == CHAR_NOTIFY_UUID) {
                val data = characteristic.value ?: return
                handleIncomingData(data)
            }
        }

        // Support modern API levels as well
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == CHAR_NOTIFY_UUID) {
                handleIncomingData(value)
            }
        }
    }

    private fun handleIncomingData(data: ByteArray) {
        if (data.isEmpty()) return

        val header = data[0].toInt() and 0xFF
        
        when (header) {
            0x05 -> { // File Transfer Protocol Header
                if (data.size < 2) return
                val subType = data[1].toInt() and 0xFF
                
                when (subType) {
                    0x01 -> { // Event: New photo file is available on the glasses
                        if (data.size >= 6) {
                            // Extract file ID (Big-Endian integer from bytes 2, 3, 4, 5)
                            val fileId = ((data[2].toInt() and 0xFF) shl 24) or
                                         ((data[3].toInt() and 0xFF) shl 16) or
                                         ((data[4].toInt() and 0xFF) shl 8) or
                                         (data[5].toInt() and 0xFF)
                            
                            addLog("دکمه عینک فشار داده شد! عکس با شناسه $fileId ثبت شد.")
                            requestPhotoTransfer(fileId)
                        }
                    }
                    0x02 -> { // Start of file transmission metadata
                        if (data.size >= 10) {
                            // Glasses acknowledge and send total file size
                            val fileSize = ((data[6].toInt() and 0xFF) shl 24) or
                                           ((data[7].toInt() and 0xFF) shl 16) or
                                           ((data[8].toInt() and 0xFF) shl 8) or
                                           (data[9].toInt() and 0xFF)
                            
                            expectedBytes = fileSize
                            fileTransferBuffer.reset()
                            _transferProgress.value = 0.0f
                            addLog("شروع دریافت عکس اصلی از عینک. حجم فایل: ${fileSize / 1024} کیلوبایت")
                        }
                    }
                    0x03 -> { // Chunk of photo data
                        if (data.size > 5) {
                            val payloadLength = data[4].toInt() and 0xFF
                            if (data.size >= 5 + payloadLength) {
                                val chunk = data.sliceArray(5 until 5 + payloadLength)
                                fileTransferBuffer.write(chunk)
                                
                                val currentBytes = fileTransferBuffer.size()
                                if (expectedBytes > 0) {
                                    val progress = currentBytes.toFloat() / expectedBytes.toFloat()
                                    _transferProgress.value = progress.coerceIn(0f, 1f)
                                }
                            }
                        }
                    }
                    0x04 -> { // End of transmission from glasses
                        addLog("انتقال بایت‌های عکس به پایان رسید. در حال پردازش فایل...")
                        processReceivedPhoto()
                    }
                }
            }
            0x0B -> { // Battery Status notification from glasses
                if (data.size >= 2) {
                    val battery = data[1].toInt() and 0xFF
                    _batteryLevel.value = battery
                    addLog("میزان باتری عینک: $battery%")
                }
            }
        }
    }

    private fun requestPhotoTransfer(fileId: Int) {
        if (_isWifiConnectedToGlasses.value) {
            downloadPhotoViaWifi(fileId)
        } else {
            requestPhotoTransferOverBle(fileId)
        }
    }

    private fun requestPhotoTransferOverBle(fileId: Int) {
        val characteristic = writeCharacteristic ?: run {
            addLog("خطا: عینک متصل نیست یا قابلیت ارسال دستور ندارد.")
            return
        }

        currentFileId = fileId
        addLog("ارسال درخواست دریافت عکس اصلی با کیفیت بالا از طریق بلوتوث...")

        // Command format: [0x05 (Header), 0x02 (Request Transfer), FileId3, FileId2, FileId1, FileId0]
        val cmd = ByteArray(6)
        cmd[0] = 0x05.toByte()
        cmd[1] = 0x02.toByte()
        cmd[2] = ((fileId shr 24) and 0xFF).toByte()
        cmd[3] = ((fileId shr 16) and 0xFF).toByte()
        cmd[4] = ((fileId shr 8) and 0xFF).toByte()
        cmd[5] = (fileId and 0xFF).toByte()

        try {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = cmd
            bluetoothGatt?.writeCharacteristic(characteristic)
        } catch (e: SecurityException) {
            addLog("خطا در ارسال دستور به عینک از طریق بلوتوث")
        }
    }

    private fun downloadPhotoViaWifi(fileId: Int) {
        _transferProgress.value = 0.1f
        addLog("وای‌فای عینک متصل است. در حال دانلود مستقیم و سریع عکس از طریق وای‌فای...")
        scope.launch(Dispatchers.IO) {
            val endpoints = listOf(
                "http://192.168.10.1/get_photo?id=$fileId",
                "http://192.168.10.1/photo.jpg",
                "http://192.168.10.1/photo",
                "http://192.168.10.1/cgi-bin/download?id=$fileId",
                "http://192.168.1.1/get_photo?id=$fileId"
            )
            
            var downloadedBytes: ByteArray? = null
            var successUrl = ""
            
            for (urlStr in endpoints) {
                try {
                    addLog("تلاش برای دانلود از: $urlStr")
                    val url = java.net.URL(urlStr)
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 3000
                    connection.readTimeout = 5000
                    connection.requestMethod = "GET"
                    
                    val responseCode = connection.responseCode
                    if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                        val inputStream = connection.inputStream
                        val buffer = ByteArrayOutputStream()
                        val data = ByteArray(4096)
                        var count: Int
                        var totalRead = 0
                        val contentLength = connection.contentLength
                        
                        while (inputStream.read(data, 0, data.size).also { count = it } != -1) {
                            buffer.write(data, 0, count)
                            totalRead += count
                            if (contentLength > 0) {
                                val progress = 0.1f + 0.8f * (totalRead.toFloat() / contentLength.toFloat())
                                _transferProgress.value = progress.coerceIn(0.1f, 0.9f)
                            }
                        }
                        
                        val bytes = buffer.toByteArray()
                        if (bytes.isNotEmpty() && bytes.size > 100) {
                            downloadedBytes = bytes
                            successUrl = urlStr
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed downloading from $urlStr: ${e.message}")
                }
            }
            
            if (downloadedBytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(downloadedBytes, 0, downloadedBytes.size)
                launch(Dispatchers.Main) {
                    if (bitmap != null) {
                        _lastCapturedPhoto.value = bitmap
                        _transferProgress.value = null
                        addLog("عکس با کیفیت اصلی با موفقیت و سرعت بالا از وای‌فای دریافت شد! (ابعاد: ${bitmap.width}x${bitmap.height})")
                        
                        val savedUri = ImageStorageHelper.saveImageToGallery(context, bitmap, "G300_${System.currentTimeMillis()}")
                        if (savedUri != null) {
                            addLog("عکس در گالری گوشی ذخیره شد: $savedUri")
                        } else {
                            addLog("خطا در ذخیره‌سازی عکس در گالری.")
                        }
                    } else {
                        addLog("خطا: عکس دریافتی از وای‌فای قابل پردازش نیست.")
                        _transferProgress.value = null
                        addLog("در حال تلاش مجدد برای دریافت از طریق کانال بلوتوث (BLE)...")
                        requestPhotoTransferOverBle(fileId)
                    }
                }
            } else {
                launch(Dispatchers.Main) {
                    addLog("خطا: ارتباط وای‌فای ناموفق بود یا فایل عکس روی سرور وای‌فای یافت نشد.")
                    _transferProgress.value = null
                    addLog("در حال تلاش مجدد برای دریافت از طریق کانال بلوتوث (BLE)...")
                    requestPhotoTransferOverBle(fileId)
                }
            }
        }
    }

    fun triggerManualCapture() {
        val characteristic = writeCharacteristic ?: run {
            addLog("خطا: عینک متصل نیست.")
            return
        }

        addLog("دستور عکاسی دستی از اپ به عینک ارسال شد.")
        // Command to trigger capture: [0x05, 0x01]
        val cmd = byteArrayOf(0x05.toByte(), 0x01.toByte())
        try {
            characteristic.value = cmd
            bluetoothGatt?.writeCharacteristic(characteristic)
        } catch (e: SecurityException) {
            addLog("خطا در ارسال دستور عکاسی دستی")
        }
    }

    private fun processReceivedPhoto() {
        val bytes = fileTransferBuffer.toByteArray()
        if (bytes.isEmpty()) {
            addLog("خطا: داده‌های عکس دریافتی خالی است.")
            _transferProgress.value = null
            return
        }

        scope.launch(Dispatchers.Default) {
            try {
                // Decode raw Jpeg bytes to Bitmap
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    launch(Dispatchers.Main) {
                        _lastCapturedPhoto.value = bitmap
                        _transferProgress.value = null
                        addLog("عکس با کیفیت اصلی با موفقیت دریافت و آماده شد! (ابعاد: ${bitmap.width}x${bitmap.height})")
                        
                        // Save to phone's public picture gallery
                        val savedUri = ImageStorageHelper.saveImageToGallery(context, bitmap, "G300_${System.currentTimeMillis()}")
                        if (savedUri != null) {
                            addLog("عکس در گالری گوشی ذخیره شد: $savedUri")
                        } else {
                            addLog("خطا در ذخیره‌سازی عکس در گالری.")
                        }
                    }
                } else {
                    launch(Dispatchers.Main) {
                        addLog("خطا: فرمت فایل JPEG ناخوانا یا ناقص است.")
                        _transferProgress.value = null
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    addLog("خطا در پردازش تصویر: ${e.message}")
                    _transferProgress.value = null
                }
            }
        }
    }

}
