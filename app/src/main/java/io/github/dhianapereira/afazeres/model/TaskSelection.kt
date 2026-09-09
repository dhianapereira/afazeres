package io.github.dhianapereira.afazeres.model

object TaskSelection {
    fun toggle(selected: List<String>, id: String): List<String> =
        if (id in selected) selected - id else selected + id

    fun current(selected: List<String>, visibleIds: List<String>): List<String> {
        val available = visibleIds.toSet()
        return selected.distinct().filter { it in available }
    }
}
