package com.amosley.signal.data

import com.amosley.signal.core.LyricLine
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.languageid.LanguageIdentification
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
    /** (language code, lines with `tr` filled), or null when the lyrics are already English or the language isn't known. */
    suspend fun translate(lines: List<LyricLine>, knownLang: String? = null): Pair<String, List<LyricLine>>? {
        val text = lines.joinToString("\n") { it.text }.take(4000)
        if (text.isBlank()) return null
        val lang = knownLang?.lowercase()?.substringBefore('-')
            ?: LanguageIdentification.getClient().let { id -> try { id.identifyLanguage(text).await() } finally { id.close() } }
        if (lang == "und" || lang == "en") return null
        val source = TranslateLanguage.fromLanguageTag(lang) ?: return null
        val translator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(TranslateLanguage.ENGLISH).build())
        try {
            translator.downloadModelIfNeeded().await()
            val out = lines.map { l ->
                if (l.text.isBlank()) l else l.copy(tr = translator.translate(l.text).await().takeIf { it.isNotBlank() && !it.equals(l.text, true) })
            }
            return lang to out
        } finally {
            translator.close()
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}
