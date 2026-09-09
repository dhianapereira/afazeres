package io.github.dhianapereira.afazeres.model.nlp

import io.github.dhianapereira.afazeres.data.Task

enum class LearningTarget { CATEGORY, PRIORITY, BOTH }

/** Shared eligibility rules keep the audit and the trained models in agreement. */
object TrainingExamples {
    data class Summary(val categories: List<Task> = emptyList(), val priorities: List<Task> = emptyList(), val deletedCategories: Map<String?, Int> = emptyMap(), val deletedPriorities: Map<String?, Int> = emptyMap()) {
        val categoryCount get() = categories.size + deletedCategories.values.sum()
        val priorityCount get() = priorities.size + deletedPriorities.values.sum()
    }
    fun summarize(records: List<io.github.dhianapereira.afazeres.data.TrainingRecord>, tasks: List<Task>, categoryIds: Set<String>): Summary {
        val current = tasks.associateBy { it.id }
        val usable = records.filter { it.counts().isNotEmpty() }
        val categories = usable.filter { it.categoryConfirmed && (it.categoryId == null || it.categoryId in categoryIds) }
        val priorities = usable.filter { it.priorityConfirmed }
        return Summary(categories.mapNotNull { current[it.id] }, priorities.mapNotNull { current[it.id] },
            categories.filter { it.id !in current }.groupingBy { it.categoryId }.eachCount(),
            priorities.filter { it.id !in current }.groupingBy { it.priority.toString() as String? }.eachCount())
    }
    fun categories(tasks: List<Task>, categoryIds: Set<String>): List<Task> = tasks.filter {
        it.categoryConfirmed && !it.categoryTrainingExcluded &&
            (it.categoryId == null || it.categoryId in categoryIds) && TextTokenizer.counts(it.title).isNotEmpty()
    }
    fun priorities(tasks: List<Task>): List<Task> = tasks.filter {
        it.priorityConfirmed && !it.priorityTrainingExcluded && it.priority in -1..2 && TextTokenizer.counts(it.title).isNotEmpty()
    }
}
