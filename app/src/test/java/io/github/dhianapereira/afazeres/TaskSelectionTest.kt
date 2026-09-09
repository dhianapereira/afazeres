package io.github.dhianapereira.afazeres

import io.github.dhianapereira.afazeres.model.TaskSelection
import org.junit.Assert.*
import org.junit.Test

class TaskSelectionTest {
    @Test fun togglesOneTaskWithoutChangingOtherSelections() {
        assertEquals(listOf("a", "b"), TaskSelection.toggle(listOf("a"), "b"))
        assertEquals(listOf("b"), TaskSelection.toggle(listOf("a", "b"), "a"))
    }
    @Test fun excludesTasksThatAreNoLongerVisible() {
        assertEquals(listOf("b"), TaskSelection.current(listOf("a", "b", "c"), listOf("b", "d")))
    }
    @Test fun doesNotSelectNewArrivalsOrCountDuplicates() {
        assertEquals(listOf("a"), TaskSelection.current(listOf("a", "a"), listOf("a", "b")))
    }
    @Test fun emptyListHasNoSelection() {
        assertTrue(TaskSelection.current(listOf("a"), emptyList()).isEmpty())
        assertTrue(TaskSelection.current(emptyList(), listOf("a")).isEmpty())
    }
}
