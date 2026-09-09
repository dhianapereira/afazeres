package io.github.dhianapereira.afazeres

import io.github.dhianapereira.afazeres.data.Task
import io.github.dhianapereira.afazeres.data.trainingRecord
import io.github.dhianapereira.afazeres.model.nlp.*
import org.junit.Assert.*
import org.junit.Test

class LearningAuditTest {
    private val tasks = List(4) { Task("w$it", "Review client contract", categoryId = "work", priority = 2) } +
        List(4) { Task("p$it", "Buy fruit groceries", categoryId = "personal", priority = 0) }
    private val ids = setOf("work", "personal")
    @Test fun auditAndPredictionUseTheSameDecision() {
        val model = TaskClassifier(tasks, ids)
        val explanation = model.explain("Review client contract")
        assertEquals(NaiveBayesClassifier.Reason.ACCEPTED, explanation.category.reason)
        assertEquals("category:" + model.fill(Task("new", "Review client contract")).categoryId, explanation.category.prediction!!.label)
        assertEquals(listOf("client", "contract", "review"), explanation.category.knownWords)
        assertEquals(8, explanation.category.exampleCount)
    }
    @Test fun auditExplainsColdStartAndUnknownVocabulary() {
        assertEquals(NaiveBayesClassifier.Reason.INSUFFICIENT_EXAMPLES, TaskClassifier(emptyList(), ids).explain("Review contract").category.reason)
        assertEquals(NaiveBayesClassifier.Reason.UNKNOWN_WORDS, TaskClassifier(tasks, ids).explain("Dentist appointment").category.reason)
        assertEquals(NaiveBayesClassifier.Reason.NO_WORDS, TaskClassifier(tasks, ids).explain("123 !").category.reason)
    }
    @Test fun resetExclusionsAffectOnlyTheSelectedModel() {
        val reset = tasks.map { it.copy(categoryTrainingExcluded = true) }
        val result = TaskClassifier(reset, ids).fill(Task("new", "Review client contract"))
        assertNull(result.categoryId)
        assertEquals(2, result.priority)
        assertTrue(TrainingExamples.categories(reset, ids).isEmpty())
        assertEquals(8, TrainingExamples.priorities(reset).size)
    }
    @Test fun summariesExcludeUnusableOrUnconfirmedExamples() {
        val mixed = tasks + listOf(
            Task("blankwords", "123", categoryId = "work", priority = 2),
            Task("automatic", "Review client contract", categoryId = "work", priority = 2, categoryConfirmed = false, priorityConfirmed = false),
            Task("excluded", "Review client contract", categoryId = "work", priority = 2, categoryTrainingExcluded = true, priorityTrainingExcluded = true),
        )
        assertEquals(tasks, TrainingExamples.categories(mixed, ids))
        assertEquals(tasks, TrainingExamples.priorities(mixed))
    }
    @Test fun confirmedEmptyLabelsStillCountAsExamples() {
        val task = Task("none", "Review contract", categoryConfirmed = true, priorityConfirmed = true)
        assertEquals(listOf(task), TrainingExamples.categories(listOf(task), ids))
        assertEquals(listOf(task), TrainingExamples.priorities(listOf(task)))
    }
    @Test fun archivedExamplesKeepTheirTitlesUntilActuallyDeleted() {
        val original = tasks.first()
        val records = listOf(original.trainingRecord())
        for (done in listOf(false, true, false)) {
            val current = original.copy(done = done)
            val summary = TrainingExamples.summarize(records, listOf(current), ids)
            assertEquals(listOf(current), summary.categories)
            assertEquals(listOf(current), summary.priorities)
            assertTrue(summary.deletedCategories.isEmpty())
            assertTrue(summary.deletedPriorities.isEmpty())
        }
        val deleted = TrainingExamples.summarize(records, emptyList(), ids)
        assertTrue(deleted.categories.isEmpty())
        assertEquals(mapOf("work" to 1), deleted.deletedCategories)
        assertEquals(mapOf("2" to 1), deleted.deletedPriorities)
    }
}
