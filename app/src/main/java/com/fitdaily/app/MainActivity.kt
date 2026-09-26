package com.fitdaily.app

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.webkit.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.*

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingBackupName: String = "fitdaily-backup.json"
    private var pendingBackupContent: String = ""
    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileChooserCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        fileChooserCallback = null
    }
    private val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(pendingBackupContent.toByteArray(Charsets.UTF_8)) }
            }.onSuccess { sendToWeb("backup", JSONObject().put("saved",true)) }
             .onFailure { sendToWeb("error", JSONObject().put("message","Backup save failed: " + (it.message ?: "unknown error"))) }
        }
        pendingBackupContent = ""
    }
    private val health by lazy { HealthConnectClient.getOrCreate(this) }
    private val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class)
    )
    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { allowed -> sendToWeb("notification", JSONObject().put("allowed", allowed)) }
    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted -> sendToWeb("permissions", JSONObject().apply {
        put("steps", granted.contains(HealthPermission.getReadPermission(StepsRecord::class)))
        put("sleep", granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class)))
        put("exercise", granted.contains(HealthPermission.getReadPermission(ExerciseSessionRecord::class)))
        put("weight", granted.contains(HealthPermission.getReadPermission(WeightRecord::class)))
    }) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = filePathCallback
                    fileChooserLauncher.launch("application/json")
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    sendToWeb("notification", JSONObject().put("allowed", Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED))
                    Bridge().checkHealthStatus()
                }
            }
            addJavascriptInterface(Bridge(), "FitDailyAndroid")
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(web)
        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (web.canGoBack()) web.goBack() else finish() }
        })
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            sendToWeb("notification", JSONObject().put("allowed", true))
        }
    }
    private fun sendToWeb(kind:String, obj:JSONObject) {
        runOnUiThread { web.evaluateJavascript("if(typeof fitDailyNativeResultV20==='function'){fitDailyNativeResultV20("+JSONObject.quote(kind)+","+JSONObject.quote(obj.toString())+")}", null) }
    }
    inner class Bridge {
        @JavascriptInterface fun getStatus() = """{"connected":true,"version":"2.9","platform":"android"}"""
        @JavascriptInterface fun exportBackup(fileName:String, content:String) {
            pendingBackupName = fileName.ifBlank { "fitdaily-backup.json" }
            pendingBackupContent = content
            runOnUiThread { backupLauncher.launch(pendingBackupName) }
        }
        @JavascriptInterface fun checkHealthStatus(): String {
            lifecycleScope.launch {
                val sdk = HealthConnectClient.getSdkStatus(this@MainActivity)
                val out = JSONObject()
                out.put("available", sdk == HealthConnectClient.SDK_AVAILABLE)
                out.put("updateRequired", sdk == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED)
                if (sdk == HealthConnectClient.SDK_AVAILABLE) {
                    try {
                        val granted = health.permissionController.getGrantedPermissions()
                        val steps = granted.contains(HealthPermission.getReadPermission(StepsRecord::class))
                        val sleep = granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class))
                        val exercise = granted.contains(HealthPermission.getReadPermission(ExerciseSessionRecord::class))
                        val weight = granted.contains(HealthPermission.getReadPermission(WeightRecord::class))
                        out.put("steps",steps).put("sleep",sleep).put("exercise",exercise).put("weight",weight)
                        out.put("allGranted",steps && sleep && exercise && weight)
                    } catch(e:Exception) { out.put("message", e.message ?: "Could not read Health Connect permissions") }
                } else {
                    out.put("message", if(sdk == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) "Health Connect needs an update." else "Health Connect is unavailable on this device.")
                }
                sendToWeb("status", out)
            }
            return """{"checking":true}"""
        }
        @JavascriptInterface fun requestHealthPermissions(): String {
            val sdk = HealthConnectClient.getSdkStatus(this@MainActivity)
            if (sdk != HealthConnectClient.SDK_AVAILABLE) {
                sendToWeb("status", JSONObject().put("available",false).put("updateRequired",sdk == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED).put("message","Health Connect is unavailable or needs an update."))
                return """{"requested":false}"""
            }
            lifecycleScope.launch {
                val granted = health.permissionController.getGrantedPermissions()
                if (granted.containsAll(permissions)) checkHealthStatus()
                else runOnUiThread { permissionLauncher.launch(permissions) }
            }
            return """{"requested":true}"""
        }
        @JavascriptInterface fun syncHealth(): String {
            if (HealthConnectClient.getSdkStatus(this@MainActivity) != HealthConnectClient.SDK_AVAILABLE) {
                sendToWeb("error",JSONObject().put("message","Health Connect is unavailable or needs an update."))
                return """{"started":false}"""
            }
            lifecycleScope.launch {
                try {
                    val granted = health.permissionController.getGrantedPermissions()
                    val now = Instant.now()
                    val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
                    val out = JSONObject().put("syncedAt", now.toString())
                    if (granted.contains(HealthPermission.getReadPermission(StepsRecord::class))) {
                        val r=health.readRecords(ReadRecordsRequest(StepsRecord::class,TimeRangeFilter.between(start,now)))
                        out.put("steps", r.records.sumOf { it.count })
                    }
                    if (granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class))) {
                        val r=health.readRecords(ReadRecordsRequest(SleepSessionRecord::class,TimeRangeFilter.between(start.minusSeconds(43200),now)))
                        val sec=r.records.sumOf { Duration.between(it.startTime,it.endTime).seconds }
                        out.put("sleepHours", sec/3600.0)
                    }
                    if (granted.contains(HealthPermission.getReadPermission(WeightRecord::class))) {
                        val r=health.readRecords(ReadRecordsRequest(WeightRecord::class,TimeRangeFilter.before(now)))
                        r.records.maxByOrNull { it.time }?.let { out.put("weightKg", it.weight.inKilograms) }
                    }
                    if (granted.contains(HealthPermission.getReadPermission(ExerciseSessionRecord::class))) {
                        val r=health.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class,TimeRangeFilter.between(start,now)))
                        val sessions=org.json.JSONArray()
                        var totalMinutes=0L
                        r.records.forEach {
                            val minutes=Duration.between(it.startTime,it.endTime).toMinutes().coerceAtLeast(0)
                            totalMinutes += minutes
                            sessions.put(JSONObject().put("minutes",minutes).put("start",it.startTime.toString()).put("end",it.endTime.toString()))
                        }
                        out.put("exerciseMinutes",totalMinutes)
                        out.put("exercises",sessions)
                    }
                    sendToWeb("sync",out)
                } catch(e:Exception) {
                    sendToWeb("error",JSONObject().put("message",e.message ?: "Health Connect sync failed"))
                }
            }
            return """{"started":true}"""
        }
        @JavascriptInterface fun scheduleReminders(json:String) { scheduleFromJson(json) }
    }
    private fun scheduleFromJson(json:String) {
        val o=runCatching { JSONObject(json) }.getOrNull() ?: return
        listOf("workout","water","sleep").forEachIndexed { i,key ->
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            val intent = Intent(this,ReminderReceiver::class.java).putExtra("type",key)
            val pi=PendingIntent.getBroadcast(this,100+i,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarmManager.cancel(pi)
            val time=o.optString(key)
            if(time.matches(Regex("\\d{2}:\\d{2}"))) {
                val (h,m)=time.split(":").map{it.toInt()}
                val cal=java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY,h); set(java.util.Calendar.MINUTE,m); set(java.util.Calendar.SECOND,0); set(java.util.Calendar.MILLISECOND,0)
                    if(timeInMillis<=System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_YEAR,1)
                }
                alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP,cal.timeInMillis,AlarmManager.INTERVAL_DAY,pi)
            }
        }
    }
    private fun createNotificationChannel() {
        if(android.os.Build.VERSION.SDK_INT>=26)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel("fitdaily","FitDaily reminders",NotificationManager.IMPORTANCE_DEFAULT)
            )
    }
}