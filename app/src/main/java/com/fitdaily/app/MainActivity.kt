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
            webViewClient = WebViewClient()
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
        runOnUiThread { web.evaluateJavascript("fitDailyNativeResultV20("+JSONObject.quote(kind)+","+JSONObject.quote(obj.toString())+")", null) }
    }
    inner class Bridge {
        @JavascriptInterface fun getStatus() = """{"connected":true,"version":"2.3","platform":"android"}"""
        @JavascriptInterface fun requestHealthPermissions(): String {
            runOnUiThread { permissionLauncher.launch(permissions) }
            return """{"requested":true}"""
        }
        @JavascriptInterface fun syncHealth(): String {
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
            val time=o.optString(key)
            if(time.matches(Regex("\\d{2}:\\d{2}"))) {
                val (h,m)=time.split(":").map{it.toInt()}
                val cal=java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY,h); set(java.util.Calendar.MINUTE,m); set(java.util.Calendar.SECOND,0)
                    if(timeInMillis<=System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_YEAR,1)
                }
                val pi=PendingIntent.getBroadcast(this,100+i,Intent(this,ReminderReceiver::class.java).putExtra("type",key),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                (getSystemService(ALARM_SERVICE) as AlarmManager).setInexactRepeating(AlarmManager.RTC_WAKEUP,cal.timeInMillis,AlarmManager.INTERVAL_DAY,pi)
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