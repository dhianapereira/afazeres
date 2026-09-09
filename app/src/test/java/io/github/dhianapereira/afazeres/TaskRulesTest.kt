package io.github.dhianapereira.afazeres

import io.github.dhianapereira.afazeres.model.TaskRules
import org.junit.Assert.*
import org.junit.Test

class TaskRulesTest {
    @Test fun titleAloneIsValid() { assertTrue(TaskRules.valid("Buy groceries", "", -1)) }
    @Test fun blankTitleIsRejected() {
        listOf("", " ", "\n\t").forEach { assertFalse(TaskRules.valid(it, "Description", 2)) }
    }
    @Test fun acceptsFieldLimits() { assertTrue(TaskRules.valid("a".repeat(200), "b".repeat(4000), 2)) }
    @Test fun rejectsOverlongFields() {
        assertFalse(TaskRules.valid("a".repeat(201), "", -1))
        assertFalse(TaskRules.valid("Title", "b".repeat(4001), -1))
    }
    @Test fun acceptsOnlySupportedPriorities() {
        (-1..2).forEach { assertTrue(TaskRules.valid("Title", "", it)) }
        listOf(Int.MIN_VALUE, -2, 3, Int.MAX_VALUE).forEach { assertFalse(TaskRules.valid("Title", "", it)) }
    }
}
