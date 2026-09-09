package io.github.dhianapereira.afazeres

import io.github.dhianapereira.afazeres.model.CategoryAppearance
import org.junit.Assert.*
import org.junit.Test

class CategoryAppearanceTest {
    @Test fun parsesAndFormatsOpaqueRgb() {
        assertEquals(0xFFA1B2C3L, CategoryAppearance.parseHex("#a1B2c3"))
        assertEquals(0xFF000000L, CategoryAppearance.parseHex("000000"))
        assertEquals("#FFFFFF", CategoryAppearance.hex(0xFFFFFFFFL))
        assertEquals("#000001", CategoryAppearance.hex(0xFF000001L))
    }
    @Test fun rejectsInvalidColorInput() {
        listOf("", "#123", "#1234567", "##123456", "GGHHII", "-12345").forEach { assertNull(CategoryAppearance.parseHex(it)) }
    }
}
