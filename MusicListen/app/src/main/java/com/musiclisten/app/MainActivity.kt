package com.musiclisten.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.musiclisten.app.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val cellRoots = mutableListOf<View>()
    private val cellEdits = mutableListOf<EditText>()
    private var buildingGrid = false
    private var latestAnswer: String = ""
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var cameraUri: Uri? = null
    private var scanning = false

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCamera() else {
            Toast.makeText(this, R.string.camera_needed, Toast.LENGTH_LONG).show()
        }
    }

    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val uri = cameraUri
        if (saved && uri != null) readCard(uri)
    }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) readCard(uri)
    }

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
                    val cell = intent.getIntExtra(ListeningService.EXTRA_CELL, -1)
                    val line = intent.getStringExtra(ListeningService.EXTRA_LINE).orEmpty()
                    val answer = AnswerFormat.headline(title, artist)
                    latestAnswer = answer
                    binding.lastMessage.text = when {
                        line.isNotBlank() -> getString(R.string.line_ready, "$answer\n$line")
                        cell >= 0 -> getString(R.string.mark_cell, answer)
                        else -> answer
                    }
                    refreshMarks()
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
        val goKey = TokenStore.goKey(this)
        if (goKey.isNotBlank()) binding.goKeyInput.setText(goKey)
        val acr = TokenStore.acr(this)
        if (acr.accessKey.isNotBlank()) {
            binding.acrHostInput.setText(acr.host)
            binding.acrKeyInput.setText(acr.accessKey)
            binding.acrSecretInput.setText(acr.accessSecret)
        }
        binding.scanCard.setOnClickListener { chooseCardPhoto() }

        val title = ListeningService.lastTitle.ifBlank { Prefs.lastTitle(this) }
        val artist = ListeningService.lastArtist.ifBlank { Prefs.lastArtist(this) }
        if (title.isNotBlank()) {
            binding.lastMessage.text = getString(R.string.last_message_fmt, title, artist)
        }

        binding.size4.setOnClickListener { changeSize(4) }
        binding.size5.setOnClickListener { changeSize(5) }
        binding.size6.setOnClickListener { changeSize(6) }
        binding.goalLine.setOnClickListener { setGoal(WinTarget.ONE_LINE) }
        binding.goalThree.setOnClickListener { setGoal(WinTarget.THREE_LINES) }
        binding.goalFull.setOnClickListener { setGoal(WinTarget.FULL_CARD) }
        binding.copyAnswer.setOnClickListener {
            val text = latestAnswer.ifBlank { binding.lastMessage.text?.toString().orEmpty() }
            val clipboard = getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("answer", text))
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
        highlightGoal(TicketStore.goal(this))
        binding.clearMarks.setOnClickListener {
            TicketStore.clearMarks(this)
            refreshMarks()
            binding.lastMessage.text = getString(R.string.last_message_empty)
        }

        buildGrid()

        binding.toggleButton.setOnClickListener {
            if (ListeningService.isRunning) {
                ListeningService.stop(this)
                refreshToggle()
            } else {
                ensurePermissionsAndStart()
            }
        }
        refreshToggle()

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
        refreshMarks()
    }

    override fun onStop() {
        TokenStore.setGoKey(this, binding.goKeyInput.text?.toString().orEmpty())
        saveAcr()
        runCatching { unregisterReceiver(updates) }
        super.onStop()
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    private fun chooseCardPhoto() {
        val key = binding.goKeyInput.text?.toString().orEmpty().trim()
        if (key.isBlank()) {
            Toast.makeText(this, R.string.go_key_missing, Toast.LENGTH_LONG).show()
            return
        }
        TokenStore.setGoKey(this, key)
        AlertDialog.Builder(this)
            .setTitle(R.string.scan_card)
            .setItems(arrayOf(getString(R.string.scan_camera), getString(R.string.scan_gallery))) { _, which ->
                if (which == 0) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        launchCamera()
                    } else {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                } else {
                    pickImage.launch("image/*")
                }
            }
            .show()
    }

    private fun launchCamera() {
        val dir = File(cacheDir, "cards").apply { mkdirs() }
        val file = File(dir, "card.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        cameraUri = uri
        takePicture.launch(uri)
    }

    private fun readCard(uri: Uri) {
        if (scanning) return
        val key = binding.goKeyInput.text?.toString().orEmpty().trim()
        if (key.isBlank()) {
            Toast.makeText(this, R.string.go_key_missing, Toast.LENGTH_LONG).show()
            return
        }
        scanning = true
        binding.scanCard.isEnabled = false
        binding.status.text = getString(R.string.scanning_card)
        uiScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val jpeg = CardImage.jpegBytes(this@MainActivity, uri)
                    CardVisionClient(key, TokenStore.goSession(this@MainActivity)).readCard(jpeg)
                }
            }
            scanning = false
            binding.scanCard.isEnabled = true
            result.onSuccess { draft ->
                TicketStore.replaceCard(this@MainActivity, draft.size, draft.cells)
                buildGrid()
                val filled = draft.cells.count { it.isNotBlank() }
                binding.lastMessage.text = getString(R.string.card_ready, draft.size, filled)
                binding.status.text = getString(R.string.status_stopped)
            }.onFailure { error ->
                binding.status.text = error.message ?: getString(R.string.status_error)
                Toast.makeText(
                    this@MainActivity,
                    error.message ?: getString(R.string.status_error),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun changeSize(size: Int) {
        if (TicketStore.gridSize(this) == size) return
        TicketStore.setGridSize(this, size)
        buildGrid()
    }

    private fun buildGrid() {
        buildingGrid = true
        binding.ticketRows.removeAllViews()
        cellRoots.clear()
        cellEdits.clear()
        val size = TicketStore.gridSize(this)
        val cells = TicketStore.cells(this)
        highlightSize(size)
        for (row in 0 until size) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
            for (col in 0 until size) {
                val index = row * size + col
                val cell = layoutInflater.inflate(R.layout.item_ticket_cell, rowLayout, false)
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                cell.layoutParams = params
                val edit = cell.findViewById<EditText>(R.id.cellText)
                val toggle = cell.findViewById<TextView>(R.id.markToggle)
                edit.setText(cells.getOrElse(index) { "" })
                edit.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        if (!buildingGrid) TicketStore.setCell(this@MainActivity, index, s?.toString().orEmpty())
                    }
                })
                toggle.setOnClickListener {
                    TicketStore.setMarked(this, index, !TicketStore.isMarked(this, index))
                    refreshMarks()
                    announceIfGoalMet()
                }
                cellRoots += cell
                cellEdits += edit
                rowLayout.addView(cell)
            }
            binding.ticketRows.addView(rowLayout)
        }
        buildingGrid = false
        refreshMarks()
    }

    private fun setGoal(target: WinTarget) {
        TicketStore.setGoal(this, target)
        highlightGoal(target)
        refreshMarks()
    }

    private fun highlightGoal(target: WinTarget) {
        val buttons = listOf(
            WinTarget.ONE_LINE to binding.goalLine,
            WinTarget.THREE_LINES to binding.goalThree,
            WinTarget.FULL_CARD to binding.goalFull,
        )
        buttons.forEach { (value, button) ->
            button.alpha = if (value == target) 1f else 0.45f
        }
    }

    private fun highlightSize(size: Int) {
        val buttons = listOf(4 to binding.size4, 5 to binding.size5, 6 to binding.size6)
        buttons.forEach { (value, button) ->
            button.alpha = if (value == size) 1f else 0.45f
        }
    }

    private fun cardFilled(): BooleanArray {
        val cells = TicketStore.cells(this)
        return BooleanArray(cells.size) { cells[it].isNotBlank() }
    }

    private fun announceIfGoalMet() {
        val size = TicketStore.gridSize(this)
        val cells = TicketStore.cells(this)
        val marks = TicketStore.marks(this)
        val filled = cardFilled()
        val goal = TicketStore.goal(this)
        if (!LineChecker.goalReached(size, marks, goal, filled)) return
        val line = LineChecker.winningLines(size, marks, filled).firstOrNull()
        val label = if (line != null) LineChecker.label(cells, line) else ""
        binding.lastMessage.text = getString(R.string.line_ready, label.ifBlank { getString(R.string.goal_full) })
    }

    private fun refreshMarks() {
        if (cellRoots.isEmpty()) return
        val size = TicketStore.gridSize(this)
        val marks = TicketStore.marks(this)
        val filled = cardFilled()
        val goal = TicketStore.goal(this)
        val won = LineChecker.goalReached(size, marks, goal, filled)
        val winning = when {
            !won -> emptySet()
            goal == WinTarget.FULL_CARD -> filled.indices.filter { filled[it] && marks[it] }.toSet()
            else -> LineChecker.winningLines(size, marks, filled).flatten().toSet()
        }
        cellRoots.forEachIndexed { index, view ->
            val background = when {
                index in winning -> R.drawable.bg_cell_line
                marks.getOrElse(index) { false } -> R.drawable.bg_cell_marked
                else -> R.drawable.bg_cell
            }
            view.setBackgroundResource(background)
            view.findViewById<TextView>(R.id.markToggle).text =
                if (marks.getOrElse(index) { false }) "✓" else "·"
        }
    }

    private fun ensurePermissionsAndStart() {
        val token = binding.tokenInput.text?.toString().orEmpty().trim()
        TokenStore.set(this, token)
        saveAcr()
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

    private fun saveAcr() {
        TokenStore.setAcr(
            this,
            binding.acrHostInput.text?.toString().orEmpty(),
            binding.acrKeyInput.text?.toString().orEmpty(),
            binding.acrSecretInput.text?.toString().orEmpty(),
        )
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
