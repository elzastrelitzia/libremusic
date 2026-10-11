package app.pulse.android.utils

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.util.Log
import app.pulse.android.preferences.LyricsTranslationLanguage
import app.pulse.android.preferences.PlayerPreferences
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

sealed class LyricsTranslation {
    data class Done(val lrc: String) : LyricsTranslation()
    object SameLanguage : LyricsTranslation()
    object Unavailable : LyricsTranslation()
    object Failure : LyricsTranslation()
}

// Settings override first ("Device language" = follow the user's device/app
// locale, Android 13+ app locale, system locale below).
fun currentLyricsTargetLang(context: Context): String {
    val override = PlayerPreferences.lyricsTranslationLanguage
    if (override != LyricsTranslationLanguage.Device) return override.langCode
    val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales?.get(0)
        }.getOrNull() ?: Locale.getDefault()
    } else Locale.getDefault()
    return locale.language.ifBlank { "en" }
}

fun translationCacheFile(context: Context, mediaId: String, lang: String): File {
    val dir = File(context.filesDir, "lyrics_translations").apply { mkdirs() }
    val key = MessageDigest.getInstance("MD5")
        .digest("$mediaId|$lang".toByteArray())
        .joinToString("") { "%02x".format(it) }
    return File(dir, "$key.lrc")
}

// On-device only: ML Kit translates and detects the source language with no
// network, no key, no quota — free forever. Translated text keeps every
// timestamp, so the sync engine and the original parse stay untouched.
suspend fun loadOrTranslateLyrics(
    context: Context,
    mediaId: String,
    syncedLrc: String,
    targetLang: String
): LyricsTranslation = withContext(Dispatchers.IO) {
    val cache = translationCacheFile(context, mediaId, targetLang)
    if (cache.isFile && cache.length() > 0L) {
        runCatching { cache.readText() }.getOrNull()?.let {
            return@withContext LyricsTranslation.Done(it)
        }
    }
    val translated = translateLrcLines(context, syncedLrc, targetLang)
    if (translated is LyricsTranslation.Done) {
        runCatching { cache.writeText(translated.lrc) }
    }
    translated
}

// Matches any [mm:ss(.xx)] / [mm:ss:xx] timestamp; used to isolate content.
private val timeStampRegex = Regex("\\[(\\d{1,2}):(\\d{1,2})([.:]\\d{1,3})?\\]")
private const val TAG = "LyricsTranslation"

private suspend fun translateLrcLines(
    context: Context,
    rawLrc: String,
    targetLang: String
): LyricsTranslation {
    // ML Kit needs Google Play services (models live in the Play provider)
    if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) !=
        ConnectionResult.SUCCESS
    ) {
        Log.w(TAG, "Google Play services unavailable, translation off")
        return LyricsTranslation.Unavailable
    }

    val contents = rawLrc.lines().mapNotNull { line ->
        val stamp = timeStampRegex.findAll(line).lastOrNull() ?: return@mapNotNull null
        line.substring(stamp.range.last + 1).trim().takeIf { it.isNotBlank() }
    }
    if (contents.isEmpty()) {
        Log.w(TAG, "nothing to translate: ${rawLrc.lines().size} lines")
        return LyricsTranslation.Failure
    }

    val target = languageOf(targetLang) ?: run {
        Log.w(TAG, "unsupported target: $targetLang")
        return LyricsTranslation.Failure
    }
    // Detect over the whole lyrics, not the first lines: an English intro or
    // hook must not decide the song's language. Keep every candidate ML Kit
    // translate can actually use, strongest first.
    val sample = contents.joinToString(" ").take(4000)
    val detected = detectLanguages(sample)
        .mapNotNull { (tag, confidence) -> languageOf(tag)?.let { it to confidence } }
    if (detected.isEmpty()) {
        Log.w(TAG, "no supported source language in ${sample.length} chars")
        return LyricsTranslation.Failure
    }
    // strongest language that is not already the target becomes the source, so
    // the song still translates instead of claiming already in your language.
    val source = detected
        .firstOrNull { it.first.substringBefore('-') != target.substringBefore('-') }
        ?.first
    if (source == null) {
        Log.d(TAG, "lyrics already in target language ($targetLang)")
        return LyricsTranslation.SameLanguage
    }

    val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()
    )
    try {
        // ~30MB model; downloadModelIfNeeded no-ops when already on device.
        // Without this gate the model would be missing and every line's
        // translate() call would throw "model files not found".
        if (!downloadModelIfNeeded(translator)) {
            Log.w(TAG, "model download failed for $source -> $targetLang")
            return LyricsTranslation.Failure
        }

        // sequential: one translator instance, and a slow first run is cached
        var translatable = 0
        var succeeded = 0
        val translatedLines = rawLrc.lines().map { line ->
            val stamp = timeStampRegex.findAll(line).lastOrNull()
            if (stamp == null) line
            else {
                val content = line.substring(stamp.range.last + 1).trim()
                if (content.isBlank()) line
                else {
                    translatable++
                    val out = translator.translate(content).awaitOrNull()
                    if (out != null && out.isNotBlank()) {
                        succeeded++
                        line.substring(0, stamp.range.last + 1) + " " + out
                    } else line
                }
            }
        }

        // all-or-nothing: partial results are not cached (a half-translated
        // song would poison the file forever); retry the whole song next toggle
        if (succeeded < translatable) {
            Log.w(TAG, "translated $succeeded/$translatable lines, not caching")
            return LyricsTranslation.Failure
        }
        Log.d(TAG, "translated $translatable lines $source -> $targetLang")
        return LyricsTranslation.Done(translatedLines.joinToString("\n"))
    } finally {
        translator.close()
    }
}

// ML Kit ships a fixed set of model languages; anything else would throw at
// runtime. Used to prune the translation-target list in the picker dialog.
fun isMlKitSupportedLang(langCode: String): Boolean {
    if (langCode.isEmpty()) return true // Device = locale resolution, not a model target
    return languageOf(langCode) != null
}

// Map an ML Kit / BCP-47 tag to a supported translate tag; unsupported → null.
private fun languageOf(tag: String): String? {
    val base = tag.substringBefore('-')
    return runCatching {
        if (base == "zh") TranslateLanguage.CHINESE
        else TranslateLanguage.fromLanguageTag(base)
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

// Ranked on-device identification over the whole lyric sample. The floor drops
// stray low-confidence guesses so they cannot hijack the source language;
// raise it if a genuinely mixed song is misread as monolingual.
// ponytail: fixed 0.2 floor, per-song tuning only if a real case is missed.
private suspend fun detectLanguages(sample: String): List<Pair<String, Float>> {
    val identifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.5f).build()
    )
    return try {
        identifier.identifyPossibleLanguages(sample).awaitOrNull()
            ?.map { it.languageTag to it.confidence }
            ?.filter { it.second >= 0.2f }
            ?: emptyList()
    } finally {
        identifier.close()
    }
}

private suspend fun downloadModelIfNeeded(translator: Translator): Boolean {
    val conditions = DownloadConditions.Builder().build()
    // Void tasks succeed with a null value, so check isSuccessful instead
    return translator.downloadModelIfNeeded(conditions).awaitSuccess()
}

// Task -> suspend bridges; avoids an extra coroutines-play-services dependency.
private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
    addOnSuccessListener {
        if (cont.isActive) cont.resume(it)
    }
    addOnFailureListener { e ->
        Log.w(TAG, "mlkit task failed: ${e.javaClass.simpleName}: ${e.message}")
        if (cont.isActive) cont.resume(null)
    }
}

private suspend fun Task<Void>.awaitSuccess(): Boolean = suspendCancellableCoroutine { cont ->
    addOnCompleteListener {
        if (!it.isSuccessful) {
            Log.w(TAG, "task failed: ${it.exception?.javaClass?.simpleName}: ${it.exception?.message}")
        }
        if (cont.isActive) cont.resume(it.isSuccessful)
    }
}
