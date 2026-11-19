package com.snowdroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import kotlin.math.sqrt

class SnowService : Service(), SensorEventListener {
    companion object {
        @Volatile var running = false
        const val ACTION_SANTA = "com.snowdroid.SANTA"
        const val ACTION_STOP = "com.snowdroid.STOP"
        private const val CHANNEL = "snow"
    }

    private lateinit var wm: WindowManager
    private lateinit var sm: SensorManager
    private var view: SnowView? = null
    private var sensing = false
    private var lastShake = 0L

    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { view?.pause(); sense(false) }
                Intent.ACTION_SCREEN_ON -> { view?.resume(); sense(true) }
                Intent.ACTION_USER_PRESENT -> view?.santa(false)
            }
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Neige", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, SnowService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE)
        val notif = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle("SnowDroid")
            .setContentText("Il neige sur ton écran")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "Arrêter", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notif)
        }

        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        sm = getSystemService(SENSOR_SERVICE) as SensorManager
        val b = wm.currentWindowMetrics.bounds
        val lp = WindowManager.LayoutParams(
            b.width(), b.height(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT)
        lp.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        view = SnowView(this).also { wm.addView(it, lp) }

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(rx, f)
        if (getSystemService(PowerManager::class.java).isInteractive) sense(true)
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SANTA -> view?.santa(true)
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val v = view ?: return
        val b = wm.currentWindowMetrics.bounds
        val lp = v.layoutParams as WindowManager.LayoutParams
        lp.width = b.width(); lp.height = b.height()
        wm.updateViewLayout(v, lp)
    }

    // --- secouer pour faire tomber la neige ---
    private fun sense(on: Boolean) {
        if (!::sm.isInitialized || on == sensing) return
        sensing = on
        if (on) {
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        } else sm.unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) {
        val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
        val now = System.currentTimeMillis()
        if (g > 22f && now - lastShake > 1500) {
            lastShake = now
            view?.shake()
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onDestroy() {
        running = false
        if (::sm.isInitialized) sm.unregisterListener(this)
        try { unregisterReceiver(rx) } catch (_: Exception) {}
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
        super.onDestroy()
    }
}
