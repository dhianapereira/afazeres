package io.github.dhianapereira.afazeres
import io.github.dhianapereira.afazeres.data.*
import org.junit.Assert.*
import org.junit.Test
class BackupCodecTest {
    private val category = Category("work", "work", 0, true)
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
        try { BackupCodec.decode(BackupCodec.encode(Backup(emptyList(), emptyList())).replace("\"version\": 2", "\"version\": 9")); fail() } catch (e: BackupException) { assertEquals("version", e.reason) }
    }
    @Test fun reportsEmptyFile() { try { BackupCodec.decode(" "); fail() } catch (e: BackupException) { assertEquals("empty", e.reason) } }
    @Test fun rejectsMalformedFile() { try { BackupCodec.decode("{}"); fail() } catch (e: BackupException) { assertEquals("invalid", e.reason) } }
    @Test fun rejectsOversizeFile() { try { BackupCodec.decode("x".repeat(BackupCodec.MAX_BYTES + 1)); fail() } catch (e: BackupException) { assertEquals("large", e.reason) } }
    private fun invalid(backup: Backup) { try { BackupCodec.decode(BackupCodec.encode(backup)); fail("Must reject invalid backup") } catch (e: BackupException) { assertEquals("invalid", e.reason) } }
}
