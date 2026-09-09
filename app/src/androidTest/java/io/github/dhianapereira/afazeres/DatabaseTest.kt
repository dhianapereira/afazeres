package io.github.dhianapereira.afazeres
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.dhianapereira.afazeres.data.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: AfazeresDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AfazeresDatabase::class.java).build() }
    @After fun close() { db.close() }
    @Test fun highestPriorityAlwaysComesFirst() = runTest {
        val repo = TaskRepository(db)
        listOf(Task("none", "No priority", createdAt = 900), Task("low", "Low", priority = 0, createdAt = 800), Task("high-old", "High", priority = 2, createdAt = 100), Task("medium", "Medium", priority = 1, createdAt = 700), Task("high-new", "High", priority = 2, createdAt = 200)).forEach { repo.save(it) }
        assertEquals(listOf("high-new", "high-old", "medium", "low", "none"), repo.tasks.first().map { it.id })
    }
    @Test fun restoreRollsBackOnInvalidRelationship() = runTest {
        val repo = TaskRepository(db)
        val original = Task("original", "Keep me")
        repo.save(original)
        try { repo.restore(Backup(emptyList(), listOf(Task("invalid", "Invalid", categoryId = "missing")))); fail() } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(listOf(original), repo.snapshot().tasks)
    }
    @Test fun restoreReplacesAndPreservesData() = runTest {
        val repo = TaskRepository(db)
        repo.save(Task("old", "Old"))
        val backup = Backup(listOf(Category("c", "Category")), listOf(Task("new", "New", categoryId = "c")))
        repo.restore(backup)
        assertEquals(backup, repo.snapshot())
    }
    @Test fun blocksDeletingCategoryWithActiveOrArchivedTasks() = runTest {
        val repo = TaskRepository(db)
        val category = Category("protected", "Protected")
        repo.save(category)
        for (archived in listOf(false, true)) {
            repo.save(Task("linked", "Linked", categoryId = category.id, done = archived))
            try { repo.delete(category); fail("Must block category deletion") } catch (_: CategoryInUseException) { }
            assertEquals(category, repo.snapshot().categories.single())
            assertEquals(category.id, repo.snapshot().tasks.single().categoryId)
        }
    }
    @Test fun databaseAlsoRejectsDirectDeletionOfReferencedCategory() = runTest {
        val category = Category("protected", "Protected")
        db.dao().save(category)
        db.dao().save(Task("linked", "Linked", categoryId = category.id, done = true))
        try { db.dao().delete(category); fail("Foreign key must block deletion") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
    }
    @Test fun editsAppearanceWithoutLosingLinksThenRemovesUnusedCategory() = runTest {
        val repo = TaskRepository(db)
        val original = Category("category", "Original")
        repo.save(original)
        repo.save(Task("linked", "Linked", categoryId = original.id))
        val edited = original.copy(name = "Edited", color = 0xFF123456L, icon = "pet")
        repo.save(edited)
        assertEquals(edited, repo.snapshot().categories.single())
        assertEquals(original.id, repo.snapshot().tasks.single().categoryId)
        repo.save(repo.snapshot().tasks.single().copy(categoryId = null))
        repo.delete(edited)
        assertTrue(repo.snapshot().categories.isEmpty())
        assertEquals(1, repo.snapshot().tasks.size)
    }
    @Test fun taskLifecyclePreservesFieldsAndSupportsClearingOptionalValues() = runTest {
        val repo = TaskRepository(db)
        val original = Task("task", "Title", createdAt = 100, updatedAt = 100)
        repo.save(original)
        assertEquals(original, repo.tasks.first().single())
        val category = Category("c", "Category")
        repo.save(category)
        val edited = original.copy(title = "Edited", note = "Description", categoryId = category.id, priority = 2, updatedAt = 200)
        repo.save(edited)
        assertEquals(edited, repo.tasks.first().single())
        repo.save(edited.copy(done = true))
        assertTrue(repo.tasks.first().filterNot { it.done }.isEmpty())
        assertEquals(edited.copy(done = true), repo.tasks.first().filter { it.done }.single())
        val reopened = edited.copy(done = false, note = "", categoryId = null, priority = -1)
        repo.save(reopened)
        assertTrue(repo.tasks.first().filter { it.done }.isEmpty())
        assertEquals(reopened, repo.tasks.first().filterNot { it.done }.single())
        repo.delete(reopened)
        assertTrue(repo.tasks.first().isEmpty())
        assertEquals(category, repo.snapshot().categories.single())
    }
    @Test fun invalidTaskEditsLeaveStoredTaskIntact() = runTest {
        val repo = TaskRepository(db)
        val original = Task("task", "Keep me")
        repo.save(original)
        for (invalid in listOf(original.copy(title = " "), original.copy(priority = 3))) {
            try { repo.save(invalid); fail("Invalid task must be rejected") } catch (_: IllegalArgumentException) { }
            assertEquals(original, repo.tasks.first().single())
        }
        try { repo.save(original.copy(categoryId = "missing")); fail("Missing category must be rejected") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(original, repo.tasks.first().single())
    }
    @Test fun bulkReopenPreservesDetailsAndLeavesUnselectedTasksUntouched() = runTest {
        val repo = TaskRepository(db)
        val a = Task("a", "A", note = "Description", priority = 2, done = true, createdAt = 100, updatedAt = 100)
        val b = a.copy(id = "b")
        val unselected = a.copy(id = "unselected")
        val active = a.copy(id = "active", done = false)
        listOf(a, b, unselected, active).forEach { repo.save(it) }
        repo.reopenArchived(listOf("a", "b", "active", "missing"))
        val stored = repo.snapshot().tasks.associateBy { it.id }
        for (original in listOf(a, b)) {
            val reopened = stored.getValue(original.id)
            assertFalse(reopened.done)
            assertTrue(reopened.updatedAt > original.updatedAt)
            assertEquals(original.copy(done = false, updatedAt = reopened.updatedAt), reopened)
        }
        assertEquals(unselected, stored["unselected"])
        assertEquals(active, stored["active"])
    }
    @Test fun bulkDeleteOnlyRemovesSelectedTasksStillArchived() = runTest {
        val repo = TaskRepository(db)
        listOf(Task("a", "A", done = true), Task("b", "B", done = true), Task("keep", "Keep", done = true), Task("active", "Active")).forEach { repo.save(it) }
        repo.deleteArchived(listOf("a", "b", "active", "missing"))
        assertEquals(setOf("keep", "active"), repo.snapshot().tasks.map { it.id }.toSet())
        repo.deleteArchived(emptyList())
        repo.reopenArchived(emptyList())
        assertEquals(2, repo.snapshot().tasks.size)
    }
    @Test fun bulkActionsSupportMoreThanOneSqliteParameterBatch() = runTest {
        val repo = TaskRepository(db)
        val tasks = (1..1001).map { Task("task-$it", "Task", done = true) }
        repo.restore(Backup(emptyList(), tasks))
        repo.reopenArchived(tasks.map { it.id })
        assertTrue(repo.snapshot().tasks.none { it.done })
        repo.restore(Backup(emptyList(), tasks))
        repo.deleteArchived(tasks.map { it.id })
        assertTrue(repo.snapshot().tasks.isEmpty())
    }
    @Test fun bulkCompleteOnlyChangesSelectedPendingTasks() = runTest {
        val repo = TaskRepository(db)
        val a = Task("a", "A", note = "Keep description", priority = 2, createdAt = 100, updatedAt = 100)
        val b = a.copy(id = "b")
        val untouched = a.copy(id = "untouched")
        val archived = a.copy(id = "archived", done = true)
        listOf(a, b, untouched, archived).forEach { repo.save(it) }
        repo.completeTasks(listOf("a", "b", "archived", "missing"))
        val stored = repo.snapshot().tasks.associateBy { it.id }
        for (original in listOf(a, b)) {
            val completed = stored.getValue(original.id)
            assertTrue(completed.done)
            assertTrue(completed.updatedAt > original.updatedAt)
            assertEquals(original.copy(done = true, updatedAt = completed.updatedAt), completed)
        }
        assertEquals(untouched, stored["untouched"])
        assertEquals(archived, stored["archived"])
    }
    @Test fun bulkDeletePendingDoesNotDeleteUnselectedOrAlreadyCompletedTasks() = runTest {
        val repo = TaskRepository(db)
        listOf(Task("a", "A"), Task("b", "B"), Task("keep", "Keep"), Task("archived", "Archived", done = true)).forEach { repo.save(it) }
        repo.deletePending(listOf("a", "b", "archived", "missing"))
        assertEquals(setOf("keep", "archived"), repo.snapshot().tasks.map { it.id }.toSet())
    }
    @Test fun backupRoundTripRestoresPendingAndArchivedTasksWithCustomCategories() = runTest {
        val repo = TaskRepository(db)
        val category = Category("custom", "Custom", 0xFF123456L, icon = "pet")
        repo.save(category)
        repo.save(Task("pending", "Pending", "Description", category.id, 2, false, 100, 200))
        repo.save(Task("archived", "Archived", done = true, createdAt = 100, updatedAt = 300))
        val original = repo.snapshot()
        val exported = BackupCodec.encode(original)
        repo.save(Task("later", "Added after export"))
        repo.restore(BackupCodec.decode(exported))
        assertEquals(original, repo.snapshot())
    }
}
