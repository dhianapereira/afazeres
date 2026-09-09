package io.github.dhianapereira.afazeres
import io.github.dhianapereira.afazeres.data.*
import io.github.dhianapereira.afazeres.model.CategoryAppearance
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class BackupCodecTest {
    private val category = Category("work", "work", CategoryAppearance.colors.first(), true)
    private val task = Task("t1", "Afazer", "Observação", "work", 2, true, 100, 200)
    @Test fun roundTripPreservesRelationshipsAndMetadata() {
        val backup = Backup(listOf(category), listOf(task))
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
    }
    @Test fun titleOnlyTaskRoundTrip() {
        val backup = Backup(emptyList(), listOf(Task("simple", "Only a title")))
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
        assertEquals(-1, backup.tasks.single().priority)
    }
    @Test fun emptyDatabaseIsValid() { assertEquals(Backup(emptyList(), emptyList()), BackupCodec.decode(BackupCodec.encode(Backup(emptyList(), emptyList())))) }
    @Test fun rejectsDanglingCategory() { invalid(Backup(emptyList(), listOf(task))) }
    @Test fun rejectsDuplicateIds() { invalid(Backup(listOf(category, category), emptyList())) }
    @Test fun rejectsInvalidPriority() { invalid(Backup(listOf(category), listOf(task.copy(priority = 7)))) }
    @Test fun rejectsInvalidTimestamp() { invalid(Backup(listOf(category), listOf(task.copy(updatedAt = 0)))) }
    @Test fun rejectsBlankTitle() { invalid(Backup(listOf(category), listOf(task.copy(title = "  ")))) }
    @Test fun rejectsUnknownBuiltInCategory() { invalid(Backup(listOf(category.copy(id = "unknown")), emptyList())) }
    @Test fun rejectsUnsupportedVersion() {
        try { BackupCodec.decode(BackupCodec.encode(Backup(emptyList(), emptyList())).replace("\"version\": 3", "\"version\": 9")); fail() } catch (e: BackupException) { assertEquals("version", e.reason) }
    }
    @Test fun reportsEmptyFile() { try { BackupCodec.decode(" "); fail() } catch (e: BackupException) { assertEquals("empty", e.reason) } }
    @Test fun rejectsMalformedFile() { try { BackupCodec.decode("{}"); fail() } catch (e: BackupException) { assertEquals("invalid", e.reason) } }
    @Test fun rejectsOversizeFile() { try { BackupCodec.decode("x".repeat(BackupCodec.MAX_BYTES + 1)); fail() } catch (e: BackupException) { assertEquals("large", e.reason) } }
    @Test fun roundTripCustomColorAndIndependentIcon() {
        val backup = Backup(listOf(Category("custom", "Custom", 0xFF123456L, icon = "pet")), emptyList())
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
    }
    @Test fun migratesBothLegacyBackupVersions() {
        for (version in 1..2) for (index in 0..6) {
            val json = JSONObject(BackupCodec.encode(Backup(listOf(category), emptyList()))).put("version", version)
            json.getJSONArray("categories").getJSONObject(0).put("color", index).remove("icon")
            val migrated = BackupCodec.decode(json.toString()).categories.single()
            assertEquals(CategoryAppearance.colors[index], migrated.color)
            assertEquals(CategoryAppearance.legacyIcon(index), migrated.icon)
            assertTrue(migrated.builtIn)
        }
    }
    @Test fun rejectsFractionalOrStringColor() {
        for (value in listOf<Any>(4289449459.5, "4289449459")) {
            val json = JSONObject(BackupCodec.encode(Backup(listOf(category), emptyList())))
            json.getJSONArray("categories").getJSONObject(0).put("color", value)
            try { BackupCodec.decode(json.toString()); fail() } catch (e: BackupException) { assertEquals("invalid", e.reason) }
        }
    }
    @Test fun rejectsUnknownIcon() { invalid(Backup(listOf(category.copy(icon = "unknown")), emptyList())) }
    @Test fun rejectsTransparentPrimaryColor() { invalid(Backup(listOf(category.copy(color = 0x12123456L)), emptyList())) }
    @Test fun rejectsInvalidLegacyPaletteIndex() {
        val json = JSONObject(BackupCodec.encode(Backup(listOf(category), emptyList()))).put("version", 2)
        json.getJSONArray("categories").getJSONObject(0).put("color", 100)
        try { BackupCodec.decode(json.toString()); fail() } catch (e: BackupException) { assertEquals("invalid", e.reason) }
    }
    @Test fun exportRejectsCountsThatCannotBeImported() {
        for (backup in listOf(
            Backup(List(1001) { category.copy(id = "c$it", builtIn = false) }, emptyList()),
            Backup(emptyList(), List(10001) { Task("t$it", "Title") }),
        )) {
            try { BackupCodec.encode(backup); fail("Must reject oversized export") }
            catch (e: BackupException) { assertEquals("large", e.reason) }
        }
    }
    @Test fun exportAcceptsImportableCountBoundary() {
        val backup = Backup(emptyList(), List(10000) { Task("t$it", "Title") })
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
    }
    @Test fun exportRejectsOversizePayloadBeforeWriting() {
        val backup = Backup(emptyList(), List(1500) { Task("t$it", "Title", note = "x".repeat(4000)) })
        try { BackupCodec.encode(backup); fail("Must reject oversized payload") }
        catch (e: BackupException) { assertEquals("large", e.reason) }
    }
    private fun invalid(backup: Backup) { try { BackupCodec.decode(BackupCodec.encode(backup)); fail("Must reject invalid backup") } catch (e: BackupException) { assertEquals("invalid", e.reason) } }
}
