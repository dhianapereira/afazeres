package io.github.dhianapereira.afazeres.model

object CategoryAppearance {
    // The first seven entries preserve the original category palette.
    val colors = listOf(0xFF409AF5L, 0xFFA57DE4L, 0xFF59B968L, 0xFFF06B6BL, 0xFFA57DE4L, 0xFFEAAF46L, 0xFF96989DL)
    val palette = (colors + listOf(0xFFED73A4L, 0xFF24A99DL, 0xFFEF8646L, 0xFF596BD8L, 0xFF92694BL)).distinct()
    val icons = listOf("work", "book", "person", "heart", "finance", "more", "home", "shopping", "fitness", "travel", "music", "code", "food", "pet", "star", "leaf")
    fun legacyIcon(index: Int): String = when (index) {
        0 -> "work"
        1, 4 -> "book"
        2 -> "person"
        3 -> "heart"
        5 -> "finance"
        else -> "more"
    }
    fun validColor(color: Long) = color in 0xFF000000L..0xFFFFFFFFL
    fun parseHex(value: String): Long? = value.removePrefix("#").takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }?.toLong(16)?.or(0xFF000000L)
    fun hex(color: Long): String = "#" + (color and 0xFFFFFFL).toString(16).uppercase().padStart(6, '0')
}
