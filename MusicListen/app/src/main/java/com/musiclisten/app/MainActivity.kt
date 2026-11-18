package com.musiclisten.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
                    // Short toast — looks like a normal message, not a recognizer UI.
                    Toast.makeText(this@MainActivity, "$title — $artist", Toast.LENGTH_LONG).show()
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

        if (ListeningService.lastTitle.isNotBlank()) {
            binding.lastMessage.text = getString(
                R.string.last_message_fmt,
                ListeningService.lastTitle,
                ListeningService.lastArtist,
            )
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
            startListening()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startListening() {
        ListeningService.start(this)
        refreshToggle()
    }

    private fun refreshToggle() {
        val running = ListeningService.isRunning
        binding.toggleButton.text = getString(if (running) R.string.stop else R.string.start)
        binding.toggleButton.setBackgroundResource(
            if (running) R.drawable.bg_button_stop else R.drawable.bg_button
        )
        if (running && binding.status.text.isNullOrBlank()) {
            binding.status.text = getString(R.string.status_listening)
        }
        if (!running) {
            binding.status.text = getString(R.string.status_stopped)
        }
    }
}
