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
}
