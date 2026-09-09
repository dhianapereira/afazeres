package io.github.dhianapereira.afazeres

import io.github.dhianapereira.afazeres.data.Task
import io.github.dhianapereira.afazeres.model.nlp.TaskClassifier
import org.junit.Assert.*
import org.junit.Test

class TaskClassifierTest {
    private val ids = setOf("work", "personal")
    private val history = List(4) { Task("w$it", "Revisar contrato cliente", categoryId = "work", priority = 2) } +
        List(4) { Task("p$it", "Comprar frutas mercado", categoryId = "personal", priority = 0) }
    private val draft = Task("new", "Revisar contrato do cliente")
    @Test fun independentlyFillsBothFieldsWithoutConfirmingItsOwnPredictions() {
        val result = TaskClassifier(history, ids).fill(draft)
        assertEquals("work", result.categoryId)
        assertEquals(2, result.priority)
        assertFalse(result.categoryConfirmed)
        assertFalse(result.priorityConfirmed)
    }
    @Test fun noHistoryLeavesOptionalFieldsUnset() {
        assertEquals(draft, TaskClassifier(emptyList(), ids).fill(draft))
    }
    @Test fun automaticLabelsAreNeverTrainingExamples() {
        val automatic = history.map { it.copy(categoryConfirmed = false, priorityConfirmed = false) }
        assertEquals(draft, TaskClassifier(automatic, ids).fill(draft))
    }
    @Test fun modelsLearnIndependentlyWhenOnlyCategoryWasChosen() {
        val categoryOnly = history.map { it.copy(priority = -1, priorityConfirmed = false) }
        val result = TaskClassifier(categoryOnly, ids).fill(draft)
        assertEquals("work", result.categoryId)
        assertEquals(-1, result.priority)
    }
    @Test fun archivedManualTasksStillTeachTheModel() {
        assertEquals("work", TaskClassifier(history.map { it.copy(done = true) }, ids).fill(draft).categoryId)
    }
    @Test fun doesNotOverwriteExplicitManualChoicesIncludingNone() {
        val manual = draft.copy(categoryId = "personal", priority = 0, categoryConfirmed = true, priorityConfirmed = true)
        assertEquals(manual, TaskClassifier(history, ids).fill(manual))
        val none = draft.copy(categoryConfirmed = true, priorityConfirmed = true)
        assertEquals(none, TaskClassifier(history, ids).fill(none))
    }
    @Test fun changingHumanLabelsChangesFuturePredictions() {
        val corrected = history.map { if (it.categoryId == "work") it.copy(categoryId = "personal") else it.copy(categoryId = "work") }
        assertEquals("personal", TaskClassifier(corrected, ids).fill(draft).categoryId)
    }
    @Test fun removingExamplesRemovesTheirInfluence() {
        assertEquals(draft, TaskClassifier(history.take(4), ids).fill(draft))
    }
    @Test fun missingCategoryIsNeverPredicted() {
        assertNull(TaskClassifier(history, setOf("personal")).fill(draft).categoryId)
    }
    @Test fun explicitNoPriorityCanBeLearnedWithoutTreatingDefaultsAsLabels() {
        val noPriority = history.map { if (it.priority == 2) it.copy(priority = -1, priorityConfirmed = true) else it }
        assertEquals(-1, TaskClassifier(noPriority, ids).fill(draft).priority)
        val unlabelled = history.map { it.copy(priority = -1, priorityConfirmed = false) }
        assertEquals(-1, TaskClassifier(unlabelled, ids).fill(draft).priority)
    }
}
