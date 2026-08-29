package com.omcreations.zetamax

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin


enum class Screen { START, GAME, RESULTS }

data class ProblemItem(
    val text: String,
    val answer: Int,
    val typeSymbol: String,
    val op: String,
    val isWeaknessItem: Boolean = false
)

data class LoggedQuestion(
    val problem: String,
    val userAns: String,
    val correctAns: String,
    val timeMs: Long,
    val symbol: String,
    val op: String,
    val isWeakness: Boolean,
    val isMasteredNow: Boolean
)

data class WeaknessItem(
    val problem: String,
    val answer: Int,
    val op: String,
    val symbol: String,
    val timeMs: Long,
    val dateAdded: String
)

data class HistoryEntry(
    val date: String,
    val score: Int,
    val ppm: String,
    val duration: Int,
    val avgTimeSec: String,
    val isUntimed: Boolean,
    val mode: String
)

data class RankBenchmark(
    val title: String,
    val color: Color
)


object StorageHelper {
    private const val PREF_NAME = "zetamac_prefs"
    private const val KEY_WEAKNESS = "zetamac_weakness_pool"
    private const val KEY_HISTORY = "zetamac_history"

    fun getWeaknessPool(context: Context): List<WeaknessItem> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_WEAKNESS, "[]") ?: "[]"
        val list = mutableListOf<WeaknessItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    WeaknessItem(
                        problem = obj.getString("problem"),
                        answer = obj.getInt("answer"),
                        op = obj.optString("op", "mult"),
                        symbol = obj.optString("symbol", "×"),
                        timeMs = obj.getLong("timeMs"),
                        dateAdded = obj.optString("dateAdded", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveWeaknessPool(context: Context, pool: List<WeaknessItem>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        for (item in pool) {
            val obj = JSONObject().apply {
                put("problem", item.problem)
                put("answer", item.answer)
                put("op", item.op)
                put("symbol", item.symbol)
                put("timeMs", item.timeMs)
                put("dateAdded", item.dateAdded)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_WEAKNESS, array.toString()).apply()
    }

    fun getHistory(context: Context): List<HistoryEntry> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val list = mutableListOf<HistoryEntry>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    HistoryEntry(
                        date = obj.getString("date"),
                        score = obj.getInt("score"),
                        ppm = obj.getString("ppm"),
                        duration = obj.getInt("duration"),
                        avgTimeSec = obj.getString("avgTimeSec"),
                        isUntimed = obj.optBoolean("isUntimed", false),
                        mode = obj.optString("mode", "zetamac")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveHistoryEntry(context: Context, entry: HistoryEntry) {
        val current = getHistory(context).toMutableList()
        current.add(0, entry)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        for (item in current.take(30)) {
            val obj = JSONObject().apply {
                put("date", item.date)
                put("score", item.score)
                put("ppm", item.ppm)
                put("duration", item.duration)
                put("avgTimeSec", item.avgTimeSec)
                put("isUntimed", item.isUntimed)
                put("mode", item.mode)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    fun clearHistory(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    fun clearWeaknessPool(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_WEAKNESS).apply()
    }
}

object SoundEngine {
    fun playSynthSound(type: String, enabled: Boolean) {
        if (!enabled) return
        Thread {
            try {
                val sampleRate = 44100
                val (freqs, durations) = when (type) {
                    "correct" -> listOf(587.33, 880.0) to listOf(0.04, 0.08)
                    "mastered" -> listOf(523.25, 659.25, 783.99) to listOf(0.04, 0.04, 0.12)
                    else -> listOf(440.0, 880.0) to listOf(0.1, 0.15) // finish
                }

                var totalSamples = 0
                for (d in durations) totalSamples += (sampleRate * d).toInt()

                val buffer = ShortArray(totalSamples)
                var offset = 0

                for (i in freqs.indices) {
                    val freq = freqs[i]
                    val dur = durations[i]
                    val numSamples = (sampleRate * dur).toInt()
                    for (j in 0 until numSamples) {
                        val angle = 2.0 * Math.PI * j / (sampleRate / freq)
                        val sample = (sin(angle) * 12000).toInt().toShort()
                        if (offset < buffer.size) {
                            buffer[offset++] = sample
                        }
                    }
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()
                Thread.sleep(250)
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}


object ProblemGenerator {
    fun generate(
        isWeaknessMode: Boolean,
        weaknessPool: List<WeaknessItem>,
        opAdd: Boolean,
        opSub: Boolean,
        opMult: Boolean,
        opDiv: Boolean,
        opSquare: Boolean,
        addMin1: Int, addMax1: Int, addMin2: Int, addMax2: Int,
        multMin1: Int, multMax1: Int, multMin2: Int, multMax2: Int,
        sqMin: Int, sqMax: Int
    ): ProblemItem? {
        if (isWeaknessMode) {
            if (weaknessPool.isEmpty()) return null
            val item = weaknessPool[Random().nextInt(weaknessPool.size)]
            return ProblemItem(
                text = item.problem,
                answer = item.answer,
                typeSymbol = item.symbol,
                op = item.op,
                isWeaknessItem = true
            )
        }

        val activeOps = mutableListOf<String>()
        if (opAdd) activeOps.add("add")
        if (opSub) activeOps.add("sub")
        if (opMult) activeOps.add("mult")
        if (opDiv) activeOps.add("div")
        if (opSquare) activeOps.add("square")

        if (activeOps.isEmpty()) activeOps.add("add")

        val op = activeOps[Random().nextInt(activeOps.size)]
        val rand = Random()

        val getRandInt = { minVal: Int, maxVal: Int ->
            val minClean = max(1, minVal)
            val maxClean = max(minClean + 1, maxVal)
            rand.nextInt(maxClean - minClean + 1) + minClean
        }

        return when (op) {
            "add" -> {
                val a = getRandInt(addMin1, addMax1)
                val b = getRandInt(addMin2, addMax2)
                ProblemItem("${a} + ${b}", a + b, "+", "add")
            }
            "sub" -> {
                val a = getRandInt(addMin1, addMax1)
                val b = getRandInt(addMin2, addMax2)
                val sum = a + b
                ProblemItem("${sum} – ${a}", b, "–", "sub")
            }
            "mult" -> {
                val a = getRandInt(multMin1, multMax1)
                val b = getRandInt(multMin2, multMax2)
                ProblemItem("${a} × ${b}", a * b, "×", "mult")
            }
            "div" -> {
                val a = getRandInt(multMin1, multMax1)
                val b = getRandInt(multMin2, multMax2)
                val prod = a * b
                ProblemItem("${prod} ÷ ${a}", b, "÷", "div")
            }
            "square" -> {
                val a = getRandInt(sqMin, sqMax)
                ProblemItem("${a}²", a * a, "x²", "square")
            }
            else -> {
                val a = getRandInt(2, 50)
                val b = getRandInt(2, 50)
                ProblemItem("${a} + ${b}", a + b, "+", "add")
            }
        }
    }

    fun getRankBenchmark(ppm: Double): RankBenchmark {
        return when {
            ppm >= 40.0 -> RankBenchmark("Quant Elite", Color(0xFFFFB74D))
            ppm >= 30.0 -> RankBenchmark("Senior Trader", Color(0xFF818CF8))
            ppm >= 20.0 -> RankBenchmark("Junior Trader", Color(0xFF34D399))
            ppm >= 12.0 -> RankBenchmark("Intermediate", Color(0xFF60A5FA))
            else -> RankBenchmark("Novice Practice", Color(0xFF9CA3AF))
        }
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZetamacTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A) // Dark slate background
                ) {
                    ZetamacAppScreen()
                }
            }
        }
    }
}

@Composable
fun ZetamacTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6366F1),
            secondary = Color(0xFF818CF8),
            background = Color(0xFF0F172A),
            surface = Color(0xFF1E293B),
            onPrimary = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

@Composable
fun ZetamacAppScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Navigation & Modals State
    var currentScreen by remember { mutableStateOf(Screen.START) }
    var showHistoryModal by remember { mutableStateOf(false) }
    var modalTab by remember { mutableStateOf("history") }
    var soundEnabled by remember { mutableStateOf(true) }

    // Configuration Settings State
    var presetMode by remember { mutableStateOf("zetamac") }
    var isWeaknessMode by remember { mutableStateOf(false) }
    var durationSec by remember { mutableStateOf(120) }
    var showTouchKeypad by remember { mutableStateOf(true) }
    var autoSubmit by remember { mutableStateOf(true) }

    var opAdd by remember { mutableStateOf(true) }
    var opSub by remember { mutableStateOf(true) }
    var opMult by remember { mutableStateOf(true) }
    var opDiv by remember { mutableStateOf(true) }
    var opSquare by remember { mutableStateOf(false) }

    var addMin1 by remember { mutableStateOf("2") }
    var addMax1 by remember { mutableStateOf("100") }
    var addMin2 by remember { mutableStateOf("2") }
    var addMax2 by remember { mutableStateOf("100") }

    var multMin1 by remember { mutableStateOf("2") }
    var multMax1 by remember { mutableStateOf("12") }
    var multMin2 by remember { mutableStateOf("2") }
    var multMax2 by remember { mutableStateOf("100") }

    var sqMin by remember { mutableStateOf("2") }
    var sqMax by remember { mutableStateOf("25") }

    // Weakness & History Persistent Data State
    var weaknessPool by remember { mutableStateOf(StorageHelper.getWeaknessPool(context)) }
    var historyList by remember { mutableStateOf(StorageHelper.getHistory(context)) }

    // Active Game State
    var isGameActive by remember { mutableStateOf(false) }
    var isGamePaused by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }
    var timeLeftSec by remember { mutableStateOf(120.0) }
    var elapsedTimeSec by remember { mutableStateOf(0.0) }
    var livePPM by remember { mutableStateOf("0.0") }
    var currentProblem by remember { mutableStateOf<ProblemItem?>(null) }
    var userInput by remember { mutableStateOf("") }
    var problemStartTime by remember { mutableStateOf(0L) }
    var pauseStartTime by remember { mutableStateOf(0L) }

    val questionLog = remember { mutableStateListOf<LoggedQuestion>() }
    var masteredInThisRun by remember { mutableStateOf(0) }
    var newlyLoggedCount by remember { mutableStateOf(0) }

    // Toast Notification State
    var toastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2000)
            toastMessage = null
        }
    }

    // Timer Loop
    LaunchedEffect(isGameActive, isGamePaused) {
        if (isGameActive && !isGamePaused) {
            val isUntimed = durationSec == 0
            while (isGameActive && !isGamePaused) {
                delay(100)
                if (isUntimed) {
                    elapsedTimeSec += 0.1
                    if (elapsedTimeSec > 0) {
                        livePPM = String.format(Locale.US, "%.1f", (score / (elapsedTimeSec / 60.0)))
                    }
                } else {
                    timeLeftSec -= 0.1
                    elapsedTimeSec += 0.1
                    if (timeLeftSec <= 0.0) {
                        timeLeftSec = 0.0
                        // End Game Trigger
                        isGameActive = false
                        SoundEngine.playSynthSound("finish", soundEnabled)

                        val totalTime = if (durationSec == 0) max(1, elapsedTimeSec.toInt()) else durationSec
                        val finalPPM = String.format(Locale.US, "%.1f", score / (totalTime / 60.0))
                        val avgMs = if (score > 0) questionLog.sumOf { it.timeMs } / score else 0L
                        val avgSecStr = String.format(Locale.US, "%.2f", avgMs / 1000.0)

                        // Analyze slow problems (>2.5s) if standard mode
                        var addedSlow = 0
                        if (!isWeaknessMode) {
                            val currentWeak = StorageHelper.getWeaknessPool(context).toMutableList()
                            questionLog.forEach { q ->
                                if (q.timeMs >= 2500 && !q.isMasteredNow) {
                                    val idx = currentWeak.indexOfFirst { it.problem == q.problem }
                                    if (idx == -1) {
                                        currentWeak.add(
                                            WeaknessItem(
                                                problem = q.problem,
                                                answer = q.correctAns.toIntOrNull() ?: 0,
                                                op = q.op,
                                                symbol = q.symbol,
                                                timeMs = q.timeMs,
                                                dateAdded = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                            )
                                        )
                                        addedSlow++
                                    } else if (q.timeMs > currentWeak[idx].timeMs) {
                                        currentWeak[idx] = currentWeak[idx].copy(timeMs = q.timeMs)
                                    }
                                }
                            }
                            currentWeak.sortByDescending { it.timeMs }
                            val trimmed = currentWeak.take(40)
                            StorageHelper.saveWeaknessPool(context, trimmed)
                            weaknessPool = trimmed
                        }
                        newlyLoggedCount = addedSlow

                        // Save History
                        val entry = HistoryEntry(
                            date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
                            score = score,
                            ppm = finalPPM,
                            duration = totalTime,
                            avgTimeSec = avgSecStr,
                            isUntimed = durationSec == 0,
                            mode = presetMode
                        )
                        StorageHelper.saveHistoryEntry(context, entry)
                        historyList = StorageHelper.getHistory(context)

                        currentScreen = Screen.RESULTS
                    } else {
                        val elapsedMins = (durationSec - timeLeftSec) / 60.0
                        if (elapsedMins > 0) {
                            livePPM = String.format(Locale.US, "%.1f", (score / elapsedMins))
                        }
                    }
                }
            }
        }
    }

    // Helper functions for Preset Application
    fun applyPreset(preset: String) {
        presetMode = preset
        isWeaknessMode = (preset == "weakness")
        if (isWeaknessMode && weaknessPool.isEmpty()) {
            toastMessage = "Weakness bank is empty! Complete regular drills first."
        }

        when (preset) {
            "zetamac" -> {
                opAdd = true; opSub = true; opMult = true; opDiv = true; opSquare = false
                addMin1 = "2"; addMax1 = "100"; addMin2 = "2"; addMax2 = "100"
                multMin1 = "2"; multMax1 = "12"; multMin2 = "2"; multMax2 = "100"
                durationSec = 120
            }
            "optiver" -> {
                opAdd = true; opSub = true; opMult = true; opDiv = true; opSquare = false
                addMin1 = "2"; addMax1 = "100"; addMin2 = "2"; addMax2 = "100"
                multMin1 = "2"; multMax1 = "12"; multMin2 = "2"; multMax2 = "100"
                durationSec = 480
            }
            "timestables" -> {
                opAdd = false; opSub = false; opMult = true; opDiv = false; opSquare = false
                multMin1 = "2"; multMax1 = "12"; multMin2 = "2"; multMax2 = "12"
                durationSec = 60
            }
            "marathon" -> {
                opAdd = true; opSub = true; opMult = true; opDiv = true; opSquare = true
                durationSec = 300
            }
        }
    }

    fun startNewGame() {
        if (isWeaknessMode && weaknessPool.isEmpty()) {
            toastMessage = "Weakness bank is empty! Practice regular mode first."
            return
        }

        score = 0
        timeLeftSec = if (durationSec == 0) 0.0 else durationSec.toDouble()
        elapsedTimeSec = 0.0
        livePPM = "0.0"
        questionLog.clear()
        masteredInThisRun = 0
        newlyLoggedCount = 0
        isGamePaused = false
        userInput = ""

        val newProb = ProblemGenerator.generate(
            isWeaknessMode, weaknessPool,
            opAdd, opSub, opMult, opDiv, opSquare,
            addMin1.toIntOrNull() ?: 2, addMax1.toIntOrNull() ?: 100,
            addMin2.toIntOrNull() ?: 2, addMax2.toIntOrNull() ?: 100,
            multMin1.toIntOrNull() ?: 2, multMax1.toIntOrNull() ?: 12,
            multMin2.toIntOrNull() ?: 2, multMax2.toIntOrNull() ?: 100,
            sqMin.toIntOrNull() ?: 2, sqMax.toIntOrNull() ?: 25
        )

        currentProblem = newProb
        problemStartTime = System.currentTimeMillis()
        isGameActive = true
        currentScreen = Screen.GAME
    }

    fun checkAnswer(inputVal: String) {
        val prob = currentProblem ?: return
        if (inputVal.trim() == prob.answer.toString()) {
            val now = System.currentTimeMillis()
            val timeMs = now - problemStartTime

            var masteredNow = false
            if (isWeaknessMode && timeMs < 2000) {
                masteredNow = true
                masteredInThisRun++
                val updated = weaknessPool.filter { it.problem != prob.text }
                StorageHelper.saveWeaknessPool(context, updated)
                weaknessPool = updated
                SoundEngine.playSynthSound("mastered", soundEnabled)
            } else {
                SoundEngine.playSynthSound("correct", soundEnabled)
            }

            questionLog.add(
                LoggedQuestion(
                    problem = prob.text,
                    userAns = inputVal,
                    correctAns = prob.answer.toString(),
                    timeMs = timeMs,
                    symbol = prob.typeSymbol,
                    op = prob.op,
                    isWeakness = prob.isWeaknessItem,
                    isMasteredNow = masteredNow
                )
            )

            score++
            userInput = ""

            // Generate Next Problem
            val nextProb = ProblemGenerator.generate(
                isWeaknessMode, weaknessPool,
                opAdd, opSub, opMult, opDiv, opSquare,
                addMin1.toIntOrNull() ?: 2, addMax1.toIntOrNull() ?: 100,
                addMin2.toIntOrNull() ?: 2, addMax2.toIntOrNull() ?: 100,
                multMin1.toIntOrNull() ?: 2, multMax1.toIntOrNull() ?: 12,
                multMin2.toIntOrNull() ?: 2, multMax2.toIntOrNull() ?: 100,
                sqMin.toIntOrNull() ?: 2, sqMax.toIntOrNull() ?: 25
            )

            if (nextProb == null && isWeaknessMode) {
                toastMessage = "🎉 All weaknesses in bank mastered!"
                // End game
                timeLeftSec = 0.0
            } else {
                currentProblem = nextProb
                problemStartTime = System.currentTimeMillis()
            }
        }
    }


    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F172A))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { currentScreen = Screen.START }
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF6366F1)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+×", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Zetamac", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF6366F1).copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Pro", color = Color(0xFF818CF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("Speed Math & Weakness Drill", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { soundEnabled = !soundEnabled },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B))
                ) {
                    Text(if (soundEnabled) "🔊" else "🔇", fontSize = 14.sp)
                }

                IconButton(
                    onClick = {
                        weaknessPool = StorageHelper.getWeaknessPool(context)
                        historyList = StorageHelper.getHistory(context)
                        modalTab = "history"
                        showHistoryModal = true
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B))
                ) {
                    Text("📊", fontSize = 14.sp)
                }
            }
        }

        Divider(color = Color(0xFF334155), thickness = 1.dp)

        // Main Screen Router
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (currentScreen) {
                Screen.START -> StartScreen(
                    presetMode = presetMode,
                    weaknessCount = weaknessPool.size,
                    isWeaknessMode = isWeaknessMode,
                    opAdd = opAdd, onOpAddChange = { opAdd = it },
                    opSub = opSub, onOpSubChange = { opSub = it },
                    opMult = opMult, onOpMultChange = { opMult = it },
                    opDiv = opDiv, onOpDivChange = { opDiv = it },
                    opSquare = opSquare, onOpSquareChange = { opSquare = it },
                    addMin1 = addMin1, onAddMin1Change = { addMin1 = it },
                    addMax1 = addMax1, onAddMax1Change = { addMax1 = it },
                    addMin2 = addMin2, onAddMin2Change = { addMin2 = it },
                    addMax2 = addMax2, onAddMax2Change = { addMax2 = it },
                    multMin1 = multMin1, onMultMin1Change = { multMin1 = it },
                    multMax1 = multMax1, onMultMax1Change = { multMax1 = it },
                    multMin2 = multMin2, onMultMin2Change = { multMin2 = it },
                    multMax2 = multMax2, onMultMax2Change = { multMax2 = it },
                    sqMin = sqMin, onSqMinChange = { sqMin = it },
                    sqMax = sqMax, onSqMaxChange = { sqMax = it },
                    durationSec = durationSec, onDurationChange = { durationSec = it },
                    showTouchKeypad = showTouchKeypad, onShowTouchKeypadChange = { showTouchKeypad = it },
                    autoSubmit = autoSubmit, onAutoSubmitChange = { autoSubmit = it },
                    onApplyPreset = { applyPreset(it) },
                    onStartClick = { startNewGame() },
                    onManageWeaknessClick = {
                        weaknessPool = StorageHelper.getWeaknessPool(context)
                        modalTab = "weakness"
                        showHistoryModal = true
                    },
                    personalBest120s = historyList.filter { it.duration == 120 }.maxOfOrNull { it.score }
                )

                Screen.GAME -> GameScreen(
                    problem = currentProblem,
                    score = score,
                    timeLeftSec = timeLeftSec,
                    durationSec = durationSec,
                    livePPM = livePPM,
                    userInput = userInput,
                    onUserInputChange = {
                        userInput = it
                        if (autoSubmit) checkAnswer(it)
                    },
                    isPaused = isGamePaused,
                    onPauseToggle = {
                        isGamePaused = !isGamePaused
                        if (isGamePaused) pauseStartTime = System.currentTimeMillis()
                        else problemStartTime += (System.currentTimeMillis() - pauseStartTime)
                    },
                    onRestart = { startNewGame() },
                    onFinish = {
                        isGameActive = false
                        currentScreen = Screen.RESULTS
                    },
                    showTouchKeypad = showTouchKeypad,
                    onKeypadInput = { digit ->
                        if (digit == "CLR") userInput = ""
                        else if (digit == "DEL") {
                            if (userInput.isNotEmpty()) userInput = userInput.dropLast(1)
                        } else {
                            userInput += digit
                            if (autoSubmit) checkAnswer(userInput)
                        }
                    },
                    isWeaknessMode = isWeaknessMode
                )

                Screen.RESULTS -> ResultsScreen(
                    score = score,
                    livePPM = livePPM,
                    durationSec = durationSec,
                    questionLog = questionLog,
                    masteredCount = masteredInThisRun,
                    newlyLoggedCount = newlyLoggedCount,
                    onPlayAgain = { startNewGame() },
                    onDrillWeaknesses = {
                        applyPreset("weakness")
                        startNewGame()
                    },
                    onChangeSettings = { currentScreen = Screen.START },
                    onCopySummary = {
                        val text = "🎯 Zetamac Speed Result:\nScore: $score solved ($livePPM PPM)\nDuration: ${durationSec}s"
                        clipboardManager.setText(AnnotatedString(text))
                        toastMessage = "Results copied to clipboard!"
                    }
                )
            }

            // Toast overlay
            toastMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF6366F1))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(msg, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }

    // Modal Dialog for History & Weakness Bank
    if (showHistoryModal) {
        HistoryWeaknessDialog(
            modalTab = modalTab,
            onTabChange = { modalTab = it },
            historyList = historyList,
            weaknessPool = weaknessPool,
            onDismiss = { showHistoryModal = false },
            onRemoveWeaknessItem = { prob ->
                val updated = weaknessPool.filter { it.problem != prob }
                StorageHelper.saveWeaknessPool(context, updated)
                weaknessPool = updated
                toastMessage = "Item removed"
            },
            onClearTab = {
                if (modalTab == "weakness") {
                    StorageHelper.clearWeaknessPool(context)
                    weaknessPool = emptyList()
                    toastMessage = "Weakness bank cleared"
                } else {
                    StorageHelper.clearHistory(context)
                    historyList = emptyList()
                    toastMessage = "History cleared"
                }
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(
    presetMode: String,
    weaknessCount: Int,
    isWeaknessMode: Boolean,
    opAdd: Boolean, onOpAddChange: (Boolean) -> Unit,
    opSub: Boolean, onOpSubChange: (Boolean) -> Unit,
    opMult: Boolean, onOpMultChange: (Boolean) -> Unit,
    opDiv: Boolean, onOpDivChange: (Boolean) -> Unit,
    opSquare: Boolean, onOpSquareChange: (Boolean) -> Unit,
    addMin1: String, onAddMin1Change: (String) -> Unit,
    addMax1: String, onAddMax1Change: (String) -> Unit,
    addMin2: String, onAddMin2Change: (String) -> Unit,
    addMax2: String, onAddMax2Change: (String) -> Unit,
    multMin1: String, onMultMin1Change: (String) -> Unit,
    multMax1: String, onMultMax1Change: (String) -> Unit,
    multMin2: String, onMultMin2Change: (String) -> Unit,
    multMax2: String, onMultMax2Change: (String) -> Unit,
    sqMin: String, onSqMinChange: (String) -> Unit,
    sqMax: String, onSqMaxChange: (String) -> Unit,
    durationSec: Int, onDurationChange: (Int) -> Unit,
    showTouchKeypad: Boolean, onShowTouchKeypadChange: (Boolean) -> Unit,
    autoSubmit: Boolean, onAutoSubmitChange: (Boolean) -> Unit,
    onApplyPreset: (String) -> Unit,
    onStartClick: () -> Unit,
    onManageWeaknessClick: () -> Unit,
    personalBest120s: Int?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Master Mental Speed",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Speed Arithmetic drill for quant interviews & mental math.",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Preset Selector Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("QUICK PRESETS", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetCard(
                        modifier = Modifier.weight(1f),
                        title = "Zetamac",
                        sub = "120s • Default",
                        category = "Standard",
                        categoryColor = Color(0xFF818CF8),
                        isSelected = presetMode == "zetamac",
                        onClick = { onApplyPreset("zetamac") }
                    )
                    PresetCard(
                        modifier = Modifier.weight(1f),
                        title = "Optiver 80",
                        sub = "480s • Goal 80",
                        category = "Quant Prep",
                        categoryColor = Color(0xFFF59E0B),
                        isSelected = presetMode == "optiver",
                        onClick = { onApplyPreset("optiver") }
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetCard(
                        modifier = Modifier.weight(1f),
                        title = "Weakness Drill",
                        sub = "Focus Slow Qs",
                        category = "Targeted ($weaknessCount)",
                        categoryColor = Color(0xFFF43F5E),
                        isSelected = presetMode == "weakness",
                        onClick = { onApplyPreset("weakness") }
                    )
                    PresetCard(
                        modifier = Modifier.weight(1f),
                        title = "Times Tables",
                        sub = "2-12 × 2-12",
                        category = "Basics",
                        categoryColor = Color(0xFF10B981),
                        isSelected = presetMode == "timestables",
                        onClick = { onApplyPreset("timestables") }
                    )
                }
            }
        }

        // Weakness Mode Notice Banner
        if (isWeaknessMode) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)),
                    border = BorderStroke(1.dp, Color(0xFFF43F5E).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Targeted Weakness Mode Active", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Serving problems that took >2.5s previously. Answering <2.0s clears them!", color = Color(0xFFFECDD3), fontSize = 11.sp)
                        }
                        TextButton(onClick = onManageWeaknessClick) {
                            Text("Manage ($weaknessCount)", color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Config Options Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("ACTIVE OPERATIONS", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = opAdd, onCheckedChange = onOpAddChange)
                            Text("Addition (+)", color = Color.White, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Checkbox(checked = opSub, onCheckedChange = onOpSubChange)
                            Text("Subtraction (−)", color = Color.White, fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = opMult, onCheckedChange = onOpMultChange)
                            Text("Multiplication (×)", color = Color.White, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Checkbox(checked = opDiv, onCheckedChange = onOpDivChange)
                            Text("Division (÷)", color = Color.White, fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = opSquare, onCheckedChange = onOpSquareChange)
                            Text("Squares (x²)", color = Color.White, fontSize = 13.sp)
                        }
                    }

                    Divider(color = Color(0xFF334155))

                    Text("DURATION & CONTROLS", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(30, 60, 120, 300, 480, 0).forEach { dur ->
                            val label = if (dur == 0) "Free" else "${dur}s"
                            Button(
                                onClick = { onDurationChange(dur) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (durationSec == dur) Color(0xFF6366F1) else Color(0xFF0F172A)
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showTouchKeypad, onCheckedChange = onShowTouchKeypadChange)
                        Text("Show On-Screen Numpad", color = Color.White, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoSubmit, onCheckedChange = onAutoSubmitChange)
                        Text("Instant Auto-Submit (Standard Zetamac)", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // Start Button
        item {
            Button(
                onClick = onStartClick,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) {
                Text("START DRILL", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        personalBest120s?.let { pb ->
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1B4B))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🏆 Personal Best (120s): $pb solved", color = Color(0xFFC7D2FE), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PresetCard(
    modifier: Modifier = Modifier,
    title: String,
    sub: String,
    category: String,
    categoryColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF1E293B) else Color(0xFF0F172A)),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) Color(0xFF6366F1) else Color(0xFF334155)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(category.uppercase(), color = categoryColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(sub, color = Color(0xFF94A3B8), fontSize = 10.sp)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    problem: ProblemItem?,
    score: Int,
    timeLeftSec: Double,
    durationSec: Int,
    livePPM: String,
    userInput: String,
    onUserInputChange: (String) -> Unit,
    isPaused: Boolean,
    onPauseToggle: () -> Unit,
    onRestart: () -> Unit,
    onFinish: () -> Unit,
    showTouchKeypad: Boolean,
    onKeypadInput: (String) -> Unit,
    isWeaknessMode: Boolean
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Dashboard Progress Header
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(if (durationSec == 0) "TIME ELAPSED" else "TIME REMAINING", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (durationSec == 0) String.format(Locale.US, "%.1fs", durationSec.toDouble()) else String.format(Locale.US, "%.1fs", timeLeftSec),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black
                            )
                        }

                        if (isWeaknessMode) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFF43F5E).copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("🎯 Weakness", color = Color(0xFFF43F5E), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("PPM", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(livePPM, color = Color(0xFF818CF8), fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("SOLVED", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("$score", color = Color(0xFF34D399), fontSize = 22.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    // Progress Bar
                    if (durationSec > 0) {
                        val progress = (timeLeftSec / durationSec).toFloat().coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = Color(0xFF6366F1),
                            trackColor = Color(0xFF0F172A)
                        )
                    }
                }
            }

            // Central Math Problem Box
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = (problem?.text ?: "") + " =",
                        color = Color.White,
                        fontSize = 38.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )

                    // Answer Input Display
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0F172A))
                            .border(2.dp, Color(0xFF6366F1), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userInput.ifEmpty { "Answer" },
                            color = if (userInput.isEmpty()) Color(0xFF475569) else Color(0xFF818CF8),
                            fontSize = 28.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(onClick = onPauseToggle) {
                            Text("Pause", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                        TextButton(onClick = onRestart) {
                            Text("Restart", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                        TextButton(onClick = onFinish) {
                            Text("Finish Drill", color = Color(0xFF34D399), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // On-Screen Numpad Keypad
            if (showTouchKeypad) {
                NumpadKeypad(onKeyClick = onKeypadInput)
            }
        }

        // Pause Overlay Dialog
        if (isPaused) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Drill Paused", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onPauseToggle, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))) {
                            Text("Resume")
                        }
                        Button(onClick = onRestart, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))) {
                            Text("Restart")
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun NumpadKeypad(onKeyClick: (String) -> Unit) {
    val keys = listOf(
        listOf("7", "8", "9"),
        listOf("4", "5", "6"),
        listOf("1", "2", "3"),
        listOf("CLR", "0", "DEL")
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        for (row in keys) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                for (key in row) {
                    Button(
                        onClick = { onKeyClick(key) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (key) {
                                "CLR" -> Color(0xFF450A0A)
                                "DEL" -> Color(0xFF334155)
                                else -> Color(0xFF1E293B)
                            }
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = key,
                            color = if (key == "CLR") Color(0xFFF43F5E) else Color.White,
                            fontSize = 18.sp,
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
fun ResultsScreen(
    score: Int,
    livePPM: String,
    durationSec: Int,
    questionLog: List<LoggedQuestion>,
    masteredCount: Int,
    newlyLoggedCount: Int,
    onPlayAgain: () -> Unit,
    onDrillWeaknesses: () -> Unit,
    onChangeSettings: () -> Unit,
    onCopySummary: () -> Unit
) {
    val ppmDouble = livePPM.toDoubleOrNull() ?: 0.0
    val rank = ProblemGenerator.getRankBenchmark(ppmDouble)
    val avgTimeMs = if (score > 0) questionLog.sumOf { it.timeMs } / score else 0L
    val avgSecStr = String.format(Locale.US, "%.2fs", avgTimeMs / 1000.0)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🏁 DRILL COMPLETED", color = Color(0xFF818CF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("$score", color = Color.White, fontSize = 56.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                    Text("${rank.title} • $livePPM PPM", color = rank.color, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)

                    if (masteredCount > 0 || newlyLoggedCount > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF450A0A))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "🎯 Weakness Engine: Logged $newlyLoggedCount slow problems (>2.5s) and cleared $masteredCount mastered items!",
                                color = Color(0xFFFECDD3),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Divider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("AVG SPEED", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            Text(avgSecStr, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PPM", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            Text(livePPM, color = Color(0xFF818CF8), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ACCURACY", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            Text("100%", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                        Button(
                            onClick = onPlayAgain,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Play Again", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onDrillWeaknesses,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Drill Weaknesses", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Response Time Speed Canvas Graph
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("RESPONSE TIME GRAPH", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    ResponseTimeGraph(questionLog = questionLog)
                }
            }
        }

        // Question Log Breakdown List
        item {
            Text("PROBLEM BREAKDOWN", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        itemsIndexed(questionLog) { idx, q ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#${idx + 1} ${q.problem} = ${q.userAns}", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val sec = String.format(Locale.US, "%.2fs", q.timeMs / 1000.0)
                        Text(sec, color = if (q.timeMs > 2500) Color(0xFFF43F5E) else Color(0xFF94A3B8), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        if (q.isMasteredNow) {
                            Text("Mastered", color = Color(0xFF34D399), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun ResponseTimeGraph(questionLog: List<LoggedQuestion>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        if (questionLog.isEmpty()) return@Canvas

        val timesSec = questionLog.map { it.timeMs / 1000.0 }
        val maxTime = max(4.0, timesSec.maxOrNull() ?: 4.0)
        val w = size.width
        val h = size.height

        val points = timesSec.mapIndexed { idx, t ->
            val x = (idx.toFloat() / max(1, timesSec.size - 1)) * w
            val y = h - ((t / maxTime).toFloat() * h)
            Offset(x, y)
        }

        // Threshold 2.5s line
        val thresholdY = h - ((2.5 / maxTime).toFloat() * h)
        drawLine(
            color = Color(0xFFF43F5E).copy(alpha = 0.4f),
            start = Offset(0f, thresholdY),
            end = Offset(w, thresholdY),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )

        val path = Path()
        points.forEachIndexed { i, pt ->
            if (i == 0) path.moveTo(pt.x, pt.y)
            else path.lineTo(pt.x, pt.y)
        }

        drawPath(
            path = path,
            color = Color(0xFF6366F1),
            style = Stroke(width = 4f)
        )

        points.forEachIndexed { i, pt ->
            val isSlow = timesSec[i] > 2.5
            drawCircle(
                color = if (isSlow) Color(0xFFF43F5E) else Color(0xFF34D399),
                radius = 6f,
                center = pt
            )
        }
    }
}


@Composable
fun HistoryWeaknessDialog(
    modalTab: String,
    onTabChange: (String) -> Unit,
    historyList: List<HistoryEntry>,
    weaknessPool: List<WeaknessItem>,
    onDismiss: () -> Unit,
    onRemoveWeaknessItem: (String) -> Unit,
    onClearTab: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(480.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Tab Header
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF0F172A)).padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onTabChange("history") },
                            colors = ButtonDefaults.buttonColors(containerColor = if (modalTab == "history") Color(0xFF6366F1) else Color(0xFF1E293B))
                        ) {
                            Text("Drill History", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { onTabChange("weakness") },
                            colors = ButtonDefaults.buttonColors(containerColor = if (modalTab == "weakness") Color(0xFF6366F1) else Color(0xFF1E293B))
                        ) {
                            Text("Weakness Bank (${weaknessPool.size})", fontSize = 11.sp)
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text("✕", color = Color.White)
                    }
                }

                // Content List
                Box(modifier = Modifier.weight(1f).padding(12.dp)) {
                    if (modalTab == "history") {
                        if (historyList.isEmpty()) {
                            Text("No saved drills yet.", color = Color(0xFF94A3B8), fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(historyList) { item ->
                                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("${item.score} Solved (${item.ppm} PPM)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("${item.date} • ${item.duration}s", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                            }
                                            Text("${item.avgTimeSec}/q", color = Color(0xFF818CF8), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (weaknessPool.isEmpty()) {
                            Text("No slow problems stored (>2.5s).", color = Color(0xFF94A3B8), fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(weaknessPool) { item ->
                                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("${item.problem} = ${item.answer}", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                val sec = String.format(Locale.US, "%.2fs", item.timeMs / 1000.0)
                                                Text(sec, color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                TextButton(onClick = { onRemoveWeaknessItem(item.problem) }) {
                                                    Text("🗑️", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onClearTab) {
                        Text("Clear Tab Log", color = Color(0xFFF43F5E), fontSize = 12.sp)
                    }
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))) {
                        Text("Close", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}