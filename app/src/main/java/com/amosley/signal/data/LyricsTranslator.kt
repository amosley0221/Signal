package com.amosley.signal.data

import com.amosley.signal.core.LyricLine
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * English translations of lyrics, made on the phone with Google's offline translator (ML Kit). The language
 * model (~30 MB per language) downloads once the first time that language comes up; after that it works offline.
 */
class LyricsTranslator {
    /**
     * Lines with `tr` filled for every line not in English, plus the main language translated from; null when
     * nothing needs translating. Each line is checked on its own, so a Spanish verse in an English song is
     * translated too; short or unclear lines follow the song's overall language.
     */
    suspend fun translate(lines: List<LyricLine>, knownLang: String? = null): Pair<String, List<LyricLine>>? {
        val all = lines.joinToString("\n") { it.text }.take(4000)
        if (all.isBlank()) return null
        val id = LanguageIdentification.getClient(LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.5f).build())
        val perLine: List<String>
        val whole: String
        try {
            whole = knownLang?.lowercase()?.substringBefore('-') ?: id.identifyLanguage(all).await()
            perLine = lines.map { l -> if (l.text.trim().length < 6) "und" else id.identifyLanguage(l.text).await() }
        } finally {
            id.close()
        }
        val songForeign = whole != "en" && whole != "und"
        val langs = perLine.map { if (it == "und" && songForeign) whole else it }
        val wanted = langs.filter { it != "en" && it != "und" && TranslateLanguage.fromLanguageTag(it) != null }
        if (wanted.isEmpty()) return null
        val main = wanted.groupingBy { it }.eachCount().maxBy { it.value }.key
        val translators = wanted.toSet().associateWith { lang ->
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.fromLanguageTag(lang)!!).setTargetLanguage(TranslateLanguage.ENGLISH).build())
        }
        try {
            translators.values.forEach { it.downloadModelIfNeeded().await() }
            val out = lines.mapIndexed { i, l ->
                val t = translators[langs[i]]
                if (t == null || l.text.isBlank()) l
                else l.copy(tr = t.translate(l.text).await().takeIf { it.isNotBlank() && !it.equals(l.text, true) })
            }
            if (out.none { it.tr != null }) return null
            return main to out
        } finally {
            translators.values.forEach { it.close() }
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}
