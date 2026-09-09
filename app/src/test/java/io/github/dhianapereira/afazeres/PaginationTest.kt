package io.github.dhianapereira.afazeres

import io.github.dhianapereira.afazeres.ui.*
import org.junit.Assert.*
import org.junit.Test

class PaginationTest {
    @Test fun emptyListHasOneEmptyPage() {
        assertEquals(1, pageCount(0))
        assertEquals(emptyList<Int>(), pageItems(emptyList<Int>(), 8))
    }
    @Test fun pagesPreserveOrderWithoutDuplicates() {
        val source = (1..43).toList()
        assertEquals(source, (0..2).flatMap { pageItems(source, it) })
        assertEquals(3, pageItems(source, 2).size)
        assertEquals(1, pageCount(20))
    }
    @Test fun deletingLastPageClampsToRemainingPage() {
        assertEquals(0, validPage(1, 20))
        assertEquals((1..20).toList(), pageItems((1..20).toList(), 1))
        assertEquals(0, validPage(-1, 30))
    }
}
