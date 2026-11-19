package com.snowdroid

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var perm: Button
    private lateinit var go: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("snow", MODE_PRIVATE)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        root.addView(TextView(this).apply { text = "SnowDroid ❄"; textSize = 28f })
        root.addView(TextView(this).apply {
            text = "De la neige par-dessus toutes tes applis."
            setPadding(0, dp(4), 0, dp(16))
        })

        perm = Button(this).apply {
            text = "1. Autoriser l'affichage par-dessus les autres applis"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
        }
        root.addView(perm)

        go = Button(this).apply { setOnClickListener { toggleService() } }
        root.addView(go)

        root.addView(TextView(this).apply {
            text = "Intensité de la neige"
            setPadding(0, dp(20), 0, 0)
        })
        root.addView(SeekBar(this).apply {
            max = 100
            progress = prefs.getInt("intensity", 45)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) prefs.edit().putInt("intensity", p).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })

        val opts = listOf(
            "pile" to "Neige qui s'accumule en bas de l'écran",
            "men" to "Bonshommes de neige",
            "fights" to "Batailles de boules de neige",
            "santa" to "Père Noël quand je déverrouille le téléphone",
            "shake" to "Secouer le téléphone pour faire tomber la neige"
        )
        for ((key, label) in opts) {
            root.addView(Switch(this).apply {
                text = label
                isChecked = prefs.getBoolean(key, true)
                setPadding(0, dp(10), 0, dp(10))
                setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean(key, v).apply() }
            })
        }

        root.addView(Button(this).apply {
            text = "Tester le Père Noël"
            gravity = Gravity.CENTER
            setOnClickListener {
                if (SnowService.running) {
                    startService(Intent(this@MainActivity, SnowService::class.java)
                        .setAction(SnowService.ACTION_SANTA))
                } else {
                    Toast.makeText(this@MainActivity, "Lance d'abord la neige", Toast.LENGTH_SHORT).show()
                }
            }
        })

        val sv = ScrollView(this)
        sv.addView(root)
        sv.setOnApplyWindowInsetsListener { v, ins ->
            val b = ins.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(0, b.top, 0, b.bottom)
            ins
        }
        setContentView(sv)
    }

    override fun onResume() {
        super.onResume()
        perm.visibility = if (Settings.canDrawOverlays(this)) android.view.View.GONE
        else android.view.View.VISIBLE
        go.text = if (SnowService.running) "Arrêter la neige" else "2. Lancer la neige"
    }

    private fun toggleService() {
        if (SnowService.running) {
            stopService(Intent(this, SnowService::class.java))
            go.text = "2. Lancer la neige"
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Autorise d'abord l'affichage par-dessus les autres applis", Toast.LENGTH_LONG).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        startForegroundService(Intent(this, SnowService::class.java))
        go.text = "Arrêter la neige"
    }
}
