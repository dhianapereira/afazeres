package io.github.dhianapereira.afazeres.model

object TaskRules {
    fun valid(title: String, description: String, priority: Int): Boolean =
        title.isNotBlank() && title.length <= 200 && description.length <= 4000 && priority in -1..2
}
