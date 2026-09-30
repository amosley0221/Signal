package com.amosley.signal.core

import java.text.Normalizer

/** Letter index for the A–Z scrub bar beside long lists. */
object AlphaIndex {
    /** Rail letters, top to bottom. "#" = numbers, symbols and unknowns. */
    val LETTERS: List<Char> = listOf('#') + ('A'..'Z')

    /** Index letter for a label, using the same rules as sorting (ignores a leading "The " / "A ", accents). */
    fun letterOf(label: String?): Char {
        val first = Sorting.titleKey(label).firstOrNull() ?: return '#'
        val plain = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).firstOrNull()?.uppercaseChar() ?: return '#'
        return if (plain in 'A'..'Z') plain else '#'
    }

    /** Letter → position of its first item in [labels] (already sorted). */
    fun build(labels: List<String?>): Map<Char, Int> {
        val out = LinkedHashMap<Char, Int>()
        labels.forEachIndexed { i, s -> out.putIfAbsent(letterOf(s), i) }
        return out
    }

    /** Position to jump to for [letter]: its first item, else the nearest following letter present, else the nearest before. */
    fun positionFor(index: Map<Char, Int>, letter: Char): Int? {
        index[letter]?.let { return it }
        val at = LETTERS.indexOf(letter).coerceAtLeast(0)
        for (i in at + 1 until LETTERS.size) index[LETTERS[i]]?.let { return it }
        for (i in at - 1 downTo 0) index[LETTERS[i]]?.let { return it }
        return null
    }
}
