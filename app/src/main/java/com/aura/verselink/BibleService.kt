package com.aura.verselink

import android.app.*
import android.R
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
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BibleService : Service(), RecognitionListener {

    // --- State Management ---
    private var isAiProcessing = false
    private val lastFoundReferences = mutableMapOf<String, Long>()
    private val REFERENCE_EXPIRY = 30000L // 30 seconds

    private val speechBuffer = mutableListOf<String>()
    private val MAX_BUFFER_SIZE = 30 // Large enough to handle "sandwiched" references

    private var speechRecognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var debounceJob: Job? = null

    private var lastSentSnippet = ""
    private var lastAiCallTime = 0L

    // Using Gemini 1.5 Flash for optimal speed and extraction
    private var generativeModel: GenerativeModel? = null // Change from val to var

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
    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences("AuraPrefs", Context.MODE_PRIVATE)
        val userKey = prefs.getString("api_key", null)

        if (userKey.isNullOrEmpty()) {
            android.util.Log.e("BibleService", "No API Key found. Stopping.")
            stopSelf()
            return
        }

        // Initialize the model with the stored key
        generativeModel = GenerativeModel(
            modelName = "gemma-3-27b-it",
            apiKey = userKey
        )

        startForeground(1, getStickyNotification("Listening for sermon references..."))
        initRecognizer()
    }

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("BibleService", "Speech recognition not available")
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
            Log.e("BibleService", "Failed to start listening: ${e.message}")
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

        // Check if the *latest* words contain a potential trigger
        val recentlyAdded = words.takeLast(7).joinToString(" ")

        if (shouldTriggerAI(recentlyAdded)) {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(2000) // Wait for preacher cadence

                val currentTime = System.currentTimeMillis()

                // Avoid redundant calls
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

        // Reset for new sentence/chunk
        speechBuffer.clear()
        lastSentSnippet = ""
        initRecognizer()
    }

    // --- AI Logic ---
    private fun parseWithAI(rawText: String) {
        if (isAiProcessing) return
        isAiProcessing = true

        scope.launch {
            try {
                val prompt = """
                    Extract Bible references (Book, Chapter, Verse) from this transcript snippet.
                    Transcript: "$rawText"
                    
                    Rules:
                    1. Return ONLY the USFM code(s) (e.g., JHN.3.16).
                    2. If multiple references are found, return them separated by commas.
                    3. If a range of verses is specified in the same chapter return it as a range (e.g., JHN.3.16-18).
                    4. If NO valid reference (Book + Chapter at minimum) is found, return 'NONE'.
                    5. The reference might be "sandwiched" in the middle of long speech.
                    6. Ignore partial mentions like just "John" without a chapter.
                """.trimIndent()

                val response = withContext(Dispatchers.IO) {
                    generativeModel?.generateContent(prompt) // Use ?. call
                }

                val result = response?.text?.trim()?.uppercase()?.replace("`", "") ?: "NONE"

                if (result != "NONE") {
                    val refs = result.split(",").map { it.trim() }.filter { it.contains(".") }
                    for (ref in refs) {
                        processDetectedReference(ref)
                    }
                }

            } catch (e: Exception) {
                Log.e("BibleService", "AI Error: ${e.message}")
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
            sendDetectionNotification(ref)
        }
    }

    private fun sendDetectionNotification(reference: String) {
        val url = "https://www.bible.com/bible/$reference"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val pendingIntent = PendingIntent.getActivity(this, reference.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "BibleScannerChannel")
            .setSmallIcon(R.drawable.ic_btn_speak_now)
            .setContentTitle("Verse Detected: $reference")
            .setContentText("Tap to open in YouVersion")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(reference.hashCode(), notification)
    }

    private fun getStickyNotification(content: String): Notification {
        val channelId = "BibleScannerChannel"
        val channel = NotificationChannel(channelId, "Service", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Aura Verse Link")
            .setContentText(content)
            .setSmallIcon(R.drawable.presence_audio_online)
            .setOngoing(true)
            .build()
    }

    override fun onError(error: Int) {
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            initRecognizer()
            return
        }

        Log.e("BibleService", "SR Error: $error")
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
