package io.github.dhianapereira.afazeres.model.nlp

import java.text.Normalizer
import java.util.Locale

/** The same bounded word-count representation is used for training and prediction. */
object TextTokenizer {
    private val marks = Regex("\\p{M}+")
    private val words = Regex("\\p{L}+")
    private val stopWords = setOf(
        "a", "o", "as", "os", "um", "uma", "uns", "umas", "de", "da", "do", "das", "dos",
        "e", "em", "na", "nas", "nos", "para", "por", "com", "ao", "aos", "que",
        "the", "a", "an", "and", "of", "to", "in", "on", "at", "for", "with",
    )

    fun counts(text: String): Map<String, Int> {
        val normalized = marks.replace(Normalizer.normalize(text.take(200).lowercase(Locale.ROOT), Normalizer.Form.NFD), "")
        val counts = linkedMapOf<String, Int>()
        words.findAll(normalized).map { it.value }.filter { it.length > 1 && it !in stopWords }.take(64).forEach {
            counts[it] = ((counts[it] ?: 0) + 1).coerceAtMost(3)
        }
        return counts
    }
}
