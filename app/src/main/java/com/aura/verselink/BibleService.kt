package com.aura.verselink

import android.app.*
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aura.verselink.ai.AIError
import com.aura.verselink.ai.AIProvider
import com.aura.verselink.ai.AIProviderFactory
import okhttp3.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BibleService : Service(), RecognitionListener {

    companion object {
        val logFlow = MutableStateFlow<List<String>>(emptyList())
        val testTrigger = MutableSharedFlow<String>(extraBufferCapacity = 1)

        var isRunning = false
            private set

        private const val CHANNEL_LISTENER = "listener_status"
        private const val CHANNEL_VERSES   = "verse_detections"

        fun addLog(tag: String, message: String) {
            val entry = "[${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}] [$tag] $message"
            Log.d("BibleService/$tag", message)
            logFlow.value = (listOf(entry) + logFlow.value).take(100)
        }
    }

    // --- State Management ---
    private var isAiProcessing = false
    private val lastFoundReferences = mutableMapOf<String, Long>()
    private val REFERENCE_EXPIRY = 30000L

    private val speechBuffer = mutableListOf<String>()
    private val MAX_BUFFER_SIZE = 20

    private var speechRecognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var debounceJob: Job? = null

    private var lastSentSnippet = ""
    private var lastAiCallTime = 0L

    private val httpClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private var aiProvider: AIProvider? = null

    // --- Normalization & Maps ---
    private val verbalVariations = mapOf(
        "1st" to "1", "2nd" to "2", "3rd" to "3",
        "first" to "1", "second" to "2", "third" to "3",
        "revelations" to "revelation",
        "songs of solomon" to "song of solomon",
        "psalm" to "psalms"
    )

    private val bibleBooks = mapOf(
        "genesis" to "GEN",
        "exodus" to "EXO",
        "leviticus" to "LEV",
        "numbers" to "NUM",
        "deuteronomy" to "DEU",
        "joshua" to "JOS",
        "judges" to "JDG",
        "ruth" to "RUT",
        "1 samuel" to "1SA",
        "2 samuel" to "2SA",
        "1 kings" to "1KI",
        "2 kings" to "2KI",
        "1 chronicles" to "1CH",
        "2 chronicles" to "2CH",
        "ezra" to "EZR",
        "nehemiah" to "NEH",
        "esther" to "EST",
        "job" to "JOB",
        "psalms" to "PSA",
        "proverbs" to "PRO",
        "ecclesiastes" to "ECC",
        "song of solomon" to "SNG",
        "isaiah" to "ISA",
        "jeremiah" to "JER",
        "lamentations" to "LAM",
        "ezekiel" to "EZK",
        "daniel" to "DAN",
        "hosea" to "HOS",
        "joel" to "JOL",
        "amos" to "AMO",
        "obadiah" to "OBA",
        "jonah" to "JON",
        "micah" to "MIC",
        "nahum" to "NAM",
        "habakkuk" to "HAB",
        "zephaniah" to "ZEP",
        "haggai" to "HAG",
        "zechariah" to "ZEC",
        "malachi" to "MAL",
        "matthew" to "MAT",
        "mark" to "MRK",
        "luke" to "LUK",
        "john" to "JHN",
        "acts" to "ACT",
        "romans" to "ROM",
        "1 corinthians" to "1CO",
        "2 corinthians" to "2CO",
        "galatians" to "GAL",
        "ephesians" to "EPH",
        "philippians" to "PHP",
        "colossians" to "COL",
        "1 thessalonians" to "1TH",
        "2 thessalonians" to "2TH",
        "1 timothy" to "1TI",
        "2 timothy" to "2TI",
        "titus" to "TIT",
        "philemon" to "PHM",
        "hebrews" to "HEB",
        "james" to "JAS",
        "1 peter" to "1PE",
        "2 peter" to "2PE",
        "1 john" to "1JN",
        "2 john" to "2JN",
        "3 john" to "3JN",
        "jude" to "JUD",
        "revelation" to "REV"
    )

    private fun shouldTriggerAI(text: String): Boolean {
        if (text.isBlank()) return false
        var normalized = text.lowercase()
        verbalVariations.forEach { (key, value) -> normalized = normalized.replace(key, value) }

        val hasBook = bibleBooks.keys.any { normalized.contains(it) }
        val structureKeywords = listOf("chapter", "verse", "turn to", "reading from", "page")
        val hasStructure = structureKeywords.any { normalized.contains(it) }

        return hasBook || hasStructure
    }

    // --- Lifecycle & Recognizer ---
    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_LISTENER, "Listener Status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while the verse listener is active in the background"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_VERSES, "Verse Detections", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when a Bible verse reference is detected"
            }
        )
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannels()

        val prefs = getSharedPreferences("AuraPrefs", Context.MODE_PRIVATE)

        // One-time migration: copy legacy "api_key" → "api_key_google"
        val legacyKey = prefs.getString("api_key", null)
        if (!legacyKey.isNullOrEmpty() &&
            prefs.getString(AIProviderFactory.apiKeyPrefFor(AIProviderFactory.PROVIDER_GOOGLE), null).isNullOrEmpty()) {
            prefs.edit()
                .putString(AIProviderFactory.apiKeyPrefFor(AIProviderFactory.PROVIDER_GOOGLE), legacyKey)
                .putString("provider_id", AIProviderFactory.PROVIDER_GOOGLE)
                .apply()
        }

        aiProvider = AIProviderFactory.fromPrefs(prefs, httpClient)
        if (aiProvider == null) {
            addLog("Service", "No API key found — stopping")
            stopSelf()
            return
        }

        val providerId = prefs.getString("provider_id", AIProviderFactory.PROVIDER_GOOGLE) ?: ""
        val modelName  = prefs.getString("model_name", "") ?: ""
        addLog("Service", "Starting with provider: $providerId model: $modelName")

        startForeground(1, getStickyNotification("Listening for sermon references..."))
        initRecognizer()

        scope.launch {
            testTrigger.collect { text ->
                addLog("Test", "Manual test: \"$text\"")
                parseWithAI(text)
            }
        }
    }

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            addLog("SR", "Speech recognition not available")
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(this)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            addLog("SR", "Failed to start listening: ${e.message}")
            recreateRecognizer()
        }
    }

    private fun recreateRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        scope.launch {
            delay(1000)
            initRecognizer()
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val fullSnapshot = matches?.get(0) ?: return
        val words = fullSnapshot.split(" ")

        speechBuffer.clear()
        speechBuffer.addAll(words.takeLast(MAX_BUFFER_SIZE))

        val currentContext = speechBuffer.joinToString(" ")
        val recentlyAdded = words.takeLast(7).joinToString(" ")

        if (shouldTriggerAI(recentlyAdded)) {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(2000)

                val currentTime = System.currentTimeMillis()

                if (isAiProcessing && currentContext.startsWith(lastSentSnippet)) return@launch
                if (currentTime - lastAiCallTime < 4000 && currentContext == lastSentSnippet) return@launch

                lastSentSnippet = currentContext
                lastAiCallTime = currentTime
                parseWithAI(currentContext)
            }
        }
    }

    override fun onResults(results: Bundle?) {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
        debounceJob?.cancel()

        if (text.isNotEmpty() && shouldTriggerAI(text)) {
            if (text != lastSentSnippet) {
                parseWithAI(text)
            }
        }

        speechBuffer.clear()
        lastSentSnippet = ""
        initRecognizer()
    }

    // --- AI Logic ---
    private fun parseWithAI(rawText: String) {
        if (isAiProcessing) return
        isAiProcessing = true

        addLog("AI", "Sending snippet: \"${rawText.take(80)}${if (rawText.length > 80) "…" else ""}\"")

        scope.launch {
            try {
                val prompt = """
                    You are a strict extractor. Output ONLY the final answer. No preamble, no explanation, no <think> tags, no reasoning, no markdown.
                    Task: Extract Bible references (Book, Chapter, Verse) from the transcript.

                    Rules:
                    1. Return ONLY the USFM code(s) in the format BOOK.CHAPTER.VERSE the book is a 3 character code (e.g., JHN.3.16).
                    2. If multiple references are found, return them separated by commas.
                    3. If a range of verses is specified in the same chapter return it as a range (e.g., JHN.3.16-18).
                    4. If NO valid reference (Book + Chapter at minimum) is found, return 'NONE'.
                    5. The reference might be "sandwiched" in the middle of long speech.
                    6. Ignore partial mentions like just a book name without a chapter.
                    
                    Transcript: "$rawText"
                """.trimIndent()

                val raw = withContext(Dispatchers.IO) {
                    aiProvider!!.generateContent(prompt)
                }.trim()
                addLog("AI", "Raw response: $raw")

                val validBookCodes = bibleBooks.values.toSet()

                // Allow up to 4-char book codes (models often return e.g. 1TIM instead of 1TI).
                // The lookbehind prevents matching TIM inside 1TIM.2.12.
                // Each candidate is then normalized: we try the extracted code as-is, then
                // progressively shorter prefixes, until we find a known USFM code.
                val usfmRegex = Regex("(?<![A-Z0-9])[A-Z0-9]{2,4}\\.[0-9]+\\.[0-9]+(?:-[0-9]+)?")
                val refs = usfmRegex.findAll(raw.uppercase())
                    .mapNotNull { match ->
                        val bookCode = match.value.substringBefore(".")
                        val rest = match.value.substringAfter(".")
                        val validCode = (bookCode.length downTo 2)
                            .map { bookCode.take(it) }
                            .firstOrNull { it in validBookCodes }
                        validCode?.let { "$it.$rest" }
                    }
                    .distinct()
                    .toList()

                addLog("AI", if (refs.isNotEmpty()) "Found: ${refs.joinToString()}" else "No references found")

                for (ref in refs) {
                    processDetectedReference(ref)
                }

            } catch (e: AIError) {
                val userMsg = when (e) {
                    is AIError.RateLimitError -> "Rate limit hit. Try a different model or wait before re-enabling."
                    is AIError.AuthError      -> "Invalid API key. Open Settings to fix it."
                    is AIError.NetworkError   -> "AI error: ${e.message}"
                }
                addLog("AI", "Error: ${e.message}")
                sendErrorNotification(userMsg)
            } catch (e: Exception) {
                addLog("AI", "Unexpected error: ${e.message}")
                sendErrorNotification("AI error: ${e.message}")
            } finally {
                isAiProcessing = false
            }
        }
    }

    private fun processDetectedReference(ref: String) {
        val currentTime = System.currentTimeMillis()
        val lastSeen = lastFoundReferences[ref] ?: 0L

        if (currentTime - lastSeen > REFERENCE_EXPIRY) {
            lastFoundReferences[ref] = currentTime
            addLog("Detect", "Verse found: $ref")
            sendDetectionNotification(ref)
        }
    }

    private fun sendDetectionNotification(reference: String) {
        val url = "https://www.bible.com/bible/$reference"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val pendingIntent = PendingIntent.getActivity(this, reference.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_VERSES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Verse Detected: $reference")
            .setContentText("Tap to open in YouVersion")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java).notify(reference.hashCode(), notification)
    }

    private fun sendErrorNotification(message: String) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, "error".hashCode(), openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_VERSES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Aura Verse Link — Error")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java).notify("error".hashCode(), notification)
    }

    private fun getStickyNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_LISTENER)
            .setContentTitle("Aura Verse Link")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }

    override fun onError(error: Int) {
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            initRecognizer()
            return
        }

        addLog("SR", "Error code: $error")
        when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                speechRecognizer?.cancel()
                initRecognizer()
            }
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED, SpeechRecognizer.ERROR_CLIENT -> {
                recreateRecognizer()
            }
            else -> {
                scope.launch {
                    delay(2000)
                    initRecognizer()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        isRunning = false
        addLog("Service", "Service stopped")
        scope.cancel()
        speechRecognizer?.destroy()
        super.onDestroy()
    }

    override fun onReadyForSpeech(p0: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(p0: Float) {}
    override fun onBufferReceived(p0: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(p0: Int, p1: Bundle?) {}
}
