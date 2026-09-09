package io.github.dhianapereira.afazeres.model.nlp

import io.github.dhianapereira.afazeres.data.Task

/** Current manual examples, represented by unordered word counts. */
class TaskClassifier private constructor(categoryExamples: List<NaiveBayesClassifier.Example>, priorityExamples: List<NaiveBayesClassifier.Example>, private val categoryIds: Set<String>) {
    constructor(tasks: List<Task>, categoryIds: Set<String>) : this(
        TrainingExamples.categories(tasks, categoryIds).map { NaiveBayesClassifier.Example(it.title, it.categoryId?.let { id -> "category:$id" } ?: "none") },
        TrainingExamples.priorities(tasks).map { NaiveBayesClassifier.Example(it.title, it.priority.toString()) }, categoryIds)
    private val categoryModel = NaiveBayesClassifier(categoryExamples)
    private val priorityModel = NaiveBayesClassifier(priorityExamples)
    companion object {
        fun fromRecords(records: List<io.github.dhianapereira.afazeres.data.TrainingRecord>, categoryIds: Set<String>): TaskClassifier {
            val usable = records.filter { it.counts().isNotEmpty() }
            return TaskClassifier(
                usable.filter { it.categoryConfirmed && (it.categoryId == null || it.categoryId in categoryIds) }
                    .map { NaiveBayesClassifier.Example("", it.categoryId?.let { id -> "category:$id" } ?: "none", it.counts()) },
                usable.filter { it.priorityConfirmed }.map { NaiveBayesClassifier.Example("", it.priority.toString(), it.counts()) }, categoryIds)
        }
    }

    data class Explanation(val category: NaiveBayesClassifier.Explanation, val priority: NaiveBayesClassifier.Explanation)
    fun explain(title: String) = Explanation(categoryModel.explain(title), priorityModel.explain(title))

    fun fill(task: Task): Task {
        val category = if (!task.categoryConfirmed && task.categoryId == null) categoryModel.predict(task.title) else null
        val priority = if (!task.priorityConfirmed && task.priority == -1) priorityModel.predict(task.title) else null
        return task.copy(
            categoryId = category?.label?.takeIf { it.startsWith("category:") }?.removePrefix("category:")?.takeIf { it in categoryIds } ?: task.categoryId,
            priority = priority?.label?.toIntOrNull() ?: task.priority,
            // Never promote an automatic prediction to a human training example.
            categoryConfirmed = task.categoryConfirmed,
            priorityConfirmed = task.priorityConfirmed,
        )
    }
}
