package com.musiclisten.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.musiclisten.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micOk = grants[Manifest.permission.RECORD_AUDIO] == true
        val notifOk = if (Build.VERSION.SDK_INT >= 33) {
            grants[Manifest.permission.POST_NOTIFICATIONS] != false
        } else {
            true
        }
        if (micOk && notifOk) {
            maybeRequestBatteryExemption()
            startListening()
        } else {
            Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show()
        }
    }

    private val updates = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ListeningService.ACTION_STATUS -> {
                    binding.status.text = intent.getStringExtra(ListeningService.EXTRA_STATUS)
                    refreshToggle()
                }
                ListeningService.ACTION_TRACK -> {
                    val title = intent.getStringExtra(ListeningService.EXTRA_TITLE).orEmpty()
                    val artist = intent.getStringExtra(ListeningService.EXTRA_ARTIST).orEmpty()
                    binding.lastMessage.text = getString(R.string.last_message_fmt, title, artist)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val existing = TokenStore.get(this)
        if (existing.isNotBlank()) {
            binding.tokenInput.setText(existing)
        } else {
            binding.status.text = getString(R.string.token_missing)
        }

        val title = ListeningService.lastTitle.ifBlank { Prefs.lastTitle(this) }
        val artist = ListeningService.lastArtist.ifBlank { Prefs.lastArtist(this) }
        if (title.isNotBlank()) {
            binding.lastMessage.text = getString(R.string.last_message_fmt, title, artist)
        }

        binding.toggleButton.setOnClickListener {
            if (ListeningService.isRunning) {
                ListeningService.stop(this)
                refreshToggle()
            } else {
                ensurePermissionsAndStart()
            }
        }
        refreshToggle()

        // Resume always-on after process death if user had it enabled.
        if (Prefs.wantRunning(this) &&
            !ListeningService.isRunning &&
            TokenStore.get(this).isNotBlank() &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            ListeningService.start(this)
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(ListeningService.ACTION_STATUS)
            addAction(ListeningService.ACTION_TRACK)
        }
        ContextCompat.registerReceiver(this, updates, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refreshToggle()
    }

    override fun onStop() {
        runCatching { unregisterReceiver(updates) }
        super.onStop()
    }

    private fun ensurePermissionsAndStart() {
        val token = binding.tokenInput.text?.toString().orEmpty().trim()
        if (token.isBlank()) {
            Toast.makeText(this, R.string.token_missing, Toast.LENGTH_LONG).show()
            return
        }
        TokenStore.set(this, token)
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (needed.isEmpty()) {
            maybeRequestBatteryExemption()
            startListening()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun maybeRequestBatteryExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        Toast.makeText(this, R.string.battery_hint, Toast.LENGTH_LONG).show()
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                )
            )
        }
    }

    private fun startListening() {
        Prefs.setWantRunning(this, true)
        ListeningService.start(this)
        refreshToggle()
    }

    private fun refreshToggle() {
        binding.toggleButton.text = getString(
            if (ListeningService.isRunning) R.string.stop else R.string.start
        )
        binding.toggleButton.setBackgroundResource(
            if (ListeningService.isRunning) R.drawable.bg_button_stop else R.drawable.bg_button
        )
        if (!ListeningService.isRunning && !Prefs.wantRunning(this)) {
            binding.status.text = getString(R.string.status_stopped)
        } else if (ListeningService.isRunning && binding.status.text.isNullOrBlank()) {
            binding.status.text = getString(R.string.status_watching)
        }
    }
}

