package io.github.dhianapereira.afazeres.model.nlp

import kotlin.math.exp
import kotlin.math.ln

/** Multinomial Naive Bayes with Laplace smoothing and conservative abstention. */
class NaiveBayesClassifier(examples: List<Example>) {
    data class Example(val text: String, val label: String, val counts: Map<String, Int> = TextTokenizer.counts(text))
    data class Prediction(val label: String, val score: Double)
    enum class Reason { NO_WORDS, INSUFFICIENT_EXAMPLES, SINGLE_LABEL, UNKNOWN_WORDS, LOW_COVERAGE, WEAK_SUPPORT, WEAK_EVIDENCE, LOW_SCORE, ACCEPTED }
    data class Explanation(
        val prediction: Prediction?, val reason: Reason, val knownWords: List<String>,
        val exampleCount: Int, val labelCount: Int, val supportingExamples: Int = 0,
    )
    private data class Document(val words: Map<String, Int>, val label: String)
    private val documents = examples.map { Document(it.counts, it.label) }.filter { it.words.isNotEmpty() }
    private val vocabulary = documents.flatMap { it.words.keys }.toSet()
    private val classes = documents.groupBy { it.label }.mapValues { (_, docs) ->
        val counts = mutableMapOf<String, Int>()
        docs.forEach { doc -> doc.words.forEach { (word, count) -> counts[word] = (counts[word] ?: 0) + count } }
        ClassStats(docs, counts, counts.values.sum())
    }
    private data class ClassStats(val documents: List<Document>, val counts: Map<String, Int>, val total: Int)
    private data class Score(val label: String, val likelihood: Double, val joint: Double)

    fun predict(text: String): Prediction? = explain(text).prediction

    fun explain(text: String): Explanation {
        val query = TextTokenizer.counts(text)
        val known = query.filterKeys { it in vocabulary }
        fun result(reason: Reason, prediction: Prediction? = null, support: Int = 0) =
            Explanation(prediction, reason, known.keys.sorted(), documents.size, classes.size, support)
        if (query.isEmpty()) return result(Reason.NO_WORDS)
        if (documents.size < 8) return result(Reason.INSUFFICIENT_EXAMPLES)
        // One class would have a misleading posterior of 1 regardless of evidence.
        if (classes.size < 2) return result(Reason.SINGLE_LABEL)
        if (known.isEmpty()) return result(Reason.UNKNOWN_WORDS)
        if (known.size.toDouble() / query.size < .6) return result(Reason.LOW_COVERAGE)
        val scores = classes.map { (label, stats) ->
            val likelihood = known.entries.sumOf { (word, count) ->
                count * ln(((stats.counts[word] ?: 0) + 1.0) / (stats.total + vocabulary.size))
            }
            Score(label, likelihood, likelihood + ln(stats.documents.size.toDouble() / documents.size))
        }.sortedWith(compareByDescending<Score> { it.joint }.thenBy { it.label })
        val best = scores.first()
        val runnerUp = scores[1]
        val evidence = classes.getValue(best.label).documents.count { doc ->
            known.keys.count { it in doc.words }.toDouble() / known.size >= .5
        }
        if (evidence < 3) return result(Reason.WEAK_SUPPORT, support = evidence)
        if (best.likelihood - runnerUp.likelihood < ln(3.0)) return result(Reason.WEAK_EVIDENCE, support = evidence)
        val normalizer = scores.sumOf { exp(it.joint - best.joint) }
        val posterior = 1.0 / normalizer
        val margin = posterior - exp(runnerUp.joint - best.joint) / normalizer
        if (posterior < .85 || margin < .25) return result(Reason.LOW_SCORE, support = evidence)
        // This is a model score, not a calibrated probability of being correct.
        return result(Reason.ACCEPTED, Prediction(best.label, posterior), evidence)
    }
}
