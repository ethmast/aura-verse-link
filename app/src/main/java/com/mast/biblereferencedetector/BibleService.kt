package com.aura.verselink

import android.app.*
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.*

class BibleService : Service(), RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // Get your key at https://aistudio.google.com/
    private val generativeModel = GenerativeModel(
        modelName = "gemini-3.1-flash-lite-preview", // Updated to a stable model name
        apiKey = "AIzaSyBKHLySJvvPW9lu5q69CkhcHPaV_kwuXog"
    )

    // Common verbal variations for normalization
    private val verbalVariations = mapOf(
        "1st" to "1", "2nd" to "2", "3rd" to "3",
        "first" to "1", "second" to "2", "third" to "3",
        "revelations" to "revelation",
        "songs of solomon" to "song of solomon",
        "psalm" to "psalms"
    )

    // Comprehensive USFM Map
    private val bibleBooks = mapOf(
        "genesis" to "GEN", "exodus" to "EXO", "leviticus" to "LEV", "numbers" to "NUM", "deuteronomy" to "DEU",
        "joshua" to "JOS", "judges" to "JDG", "ruth" to "RUT", "1 samuel" to "1SA", "2 samuel" to "2SA",
        "1 kings" to "1KI", "2 kings" to "2KI", "1 chronicles" to "1CH", "2 chronicles" to "2CH",
        "ezra" to "EZR", "nehemiah" to "NEH", "esther" to "EST", "job" to "JOB", "psalms" to "PSA",
        "proverbs" to "PRO", "ecclesiastes" to "ECC", "song of solomon" to "SNG", "isaiah" to "ISA",
        "jeremiah" to "JER", "lamentations" to "LAM", "ezekiel" to "EZK", "daniel" to "DAN",
        "hosea" to "HOS", "joel" to "JOL", "amos" to "AMO", "obadiah" to "OBA", "jonah" to "JON",
        "micah" to "MIC", "nahum" to "NAM", "habakkuk" to "HAB", "zephaniah" to "ZEP", "haggai" to "HAG",
        "zechariah" to "ZEC", "malachi" to "MAL", "matthew" to "MAT", "mark" to "MRK", "luke" to "LUK",
        "john" to "JHN", "acts" to "ACT", "romans" to "ROM", "1 corinthians" to "1CO", "2 corinthians" to "2CO",
        "galatians" to "GAL", "ephesians" to "EPH", "philippians" to "PHP", "colossians" to "COL",
        "1 thessalonians" to "1TH", "2 thessalonians" to "2TH", "1 timothy" to "1TI", "2 timothy" to "2TI",
        "titus" to "TIT", "philemon" to "PHM", "hebrews" to "HEB", "james" to "JAS", "1 peter" to "1PE",
        "2 peter" to "2PE", "1 john" to "1JN", "2 john" to "2JN", "3 john" to "3JN", "jude" to "JUD", "revelation" to "REV"
    )

    override fun onCreate() {
        super.onCreate()
        startForeground(1, getStickyNotification("Listening for sermon references..."))
        initRecognizer()
    }

    private fun initRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        speechRecognizer?.startListening(intent)
    }

    override fun onResults(results: Bundle?) {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
        val lowercaseText = text.lowercase()

        // Normalize the text before checking
        var normalizedText = lowercaseText
        verbalVariations.forEach { (key, value) ->
            normalizedText = normalizedText.replace(key, value)
        }

        // Check against the map or for "chapter" keyword
        if (bibleBooks.keys.any { normalizedText.contains(it) } || normalizedText.contains("chapter")) {
            parseWithAI(text)
        }

        initRecognizer() // Continuous Loop
    }

    private fun parseWithAI(rawText: String) {
        scope.launch {
            try {
                val prompt = "Extract the Bible reference from this text and return only the USFM code format (e.g. JHN.3.16). If no specific reference, return 'NONE'. Text: $rawText"
                val response = generativeModel.generateContent(prompt)
                val ref = response.text?.trim() ?: "NONE"

                if (ref != "NONE" && ref.contains(".")) {
                    sendDetectionNotification(ref)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendDetectionNotification(reference: String) {
        val url = "https://www.bible.com/bible/$reference"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val pendingIntent = PendingIntent.getActivity(this, reference.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "BibleScannerChannel")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
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
            .setSmallIcon(android.R.drawable.presence_audio_online)
            .setOngoing(true)
            .build()
    }

    override fun onError(error: Int) { initRecognizer() } // Loop on error
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        scope.cancel()
        speechRecognizer?.destroy()
        super.onDestroy()
    }

    // Unused Listener Boilerplate
    override fun onReadyForSpeech(p0: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(p0: Float) {}
    override fun onBufferReceived(p0: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(p0: Bundle?) {}
    override fun onEvent(p0: Int, p1: Bundle?) {}
}