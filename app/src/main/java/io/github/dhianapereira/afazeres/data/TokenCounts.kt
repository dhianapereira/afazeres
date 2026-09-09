package io.github.dhianapereira.afazeres.data

import org.json.JSONObject

/** Counts retain vocabulary, but never word order or the original title. Not encryption. */
object TokenCounts {
    fun encode(counts: Map<String, Int>): String = JSONObject().apply {
        counts.toSortedMap().forEach { (word, count) -> put(word, count) }
    }.toString()
    fun decode(value: String): Map<String, Int> {
        val json = JSONObject(value)
        require(json.length() <= 64)
        return json.keys().asSequence().associateWith { word ->
            require(word.isNotBlank() && word.length <= 200)
            val count = json.get(word)
            require(count is Int && count in 1..3)
            count
        }
    }
}
