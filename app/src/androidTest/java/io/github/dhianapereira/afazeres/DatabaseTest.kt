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
    private suspend fun learningHistory(repo: TaskRepository) {
        repo.save(Category("work", "Work")); repo.save(Category("personal", "Personal"))
        repeat(4) {
            repo.save(Task("w$it", "Revisar contrato cliente", categoryId = "work", priority = 2, done = true))
            repo.save(Task("p$it", "Comprar frutas mercado", categoryId = "personal", priority = 0))
        }
    }
    @Test fun newTasksLearnFromManualHistoryAndBackupPreservesLearning() = runTest {
        val repo = TaskRepository(db)
        repo.create(Task("cold", "Revisar contrato cliente"))
        assertNull(repo.snapshot().tasks.single().categoryId)
        learningHistory(repo)
        repo.create(Task("automatic", "Revisar contrato do cliente"))
        val automatic = db.dao().task("automatic")!!
        assertEquals("work", automatic.categoryId); assertEquals(2, automatic.priority)
        assertFalse(automatic.categoryConfirmed); assertFalse(automatic.priorityConfirmed)
        val backup = BackupCodec.encode(repo.snapshot())
        repo.restore(BackupCodec.decode(backup))
        assertEquals(automatic, db.dao().task("automatic"))
        TaskRepository(db).create(Task("after-restore", automatic.title))
        assertEquals("work", db.dao().task("after-restore")!!.categoryId)
    }
    @Test fun repeatedSavesAndStatusChangesDoNotManufactureTrainingExamples() = runTest {
        val repo = TaskRepository(db)
        repo.save(Category("work", "Work"))
        repo.save(Category("personal", "Personal"))
        repeat(20) {
            repo.save(Task("one", "Revisar contrato cliente", categoryId = "work", priority = 2, done = it % 2 == 0))
        }
        repo.save(Task("two", "Comprar frutas mercado", categoryId = "personal", priority = 0))
        repo.create(Task("new", "Revisar contrato cliente"))
        assertNull(db.dao().task("new")!!.categoryId)
        assertEquals(-1, db.dao().task("new")!!.priority)
    }
    @Test fun automaticTaskStatusChangesDoNotConfirmLabels() = runTest {
        val repo = TaskRepository(db)
        learningHistory(repo)
        repo.create(Task("automatic", "Revisar contrato cliente"))
        repo.completeTasks(listOf("automatic"))
        repo.reopenArchived(listOf("automatic"))
        val stored = db.dao().task("automatic")!!
        assertFalse(stored.categoryConfirmed); assertFalse(stored.priorityConfirmed)
        repo.save(stored.copy(categoryId = "personal", categoryConfirmed = true))
        assertTrue(db.dao().task("automatic")!!.categoryConfirmed)
        assertFalse(db.dao().task("automatic")!!.priorityConfirmed)
    }
    @Test fun deletionPreservesLearningAndRestoreReplacesHistory() = runTest {
        val repo = TaskRepository(db)
        learningHistory(repo)
        repo.deleteArchived(listOf("w0", "w1", "w2", "w3"))
        repo.create(Task("after-delete", "Revisar contrato cliente"))
        assertEquals("work", db.dao().task("after-delete")!!.categoryId)
        repo.restore(Backup(emptyList(), emptyList()))
        repo.create(Task("after-clear", "Comprar frutas mercado"))
        assertNull(db.dao().task("after-clear")!!.categoryId)
    }
    @Test fun resetPreservesTaskValuesAndStaleSavesCannotRestoreExcludedExamples() = runTest {
        val repo = TaskRepository(db)
        learningHistory(repo)
        val before = repo.snapshot()
        val stale = before.tasks.first()
        repo.resetLearning(io.github.dhianapereira.afazeres.model.nlp.LearningTarget.CATEGORY)
        val reset = repo.snapshot()
        assertEquals(before.tasks.map { it.copy(categoryTrainingExcluded = true) }, reset.tasks)
        assertEquals(before.categories, reset.categories)
        repo.save(stale.copy(note = "Unrelated edit"))
        assertTrue(db.dao().task(stale.id)!!.categoryTrainingExcluded)
        assertFalse(db.dao().task(stale.id)!!.priorityTrainingExcluded)
        repo.save(stale, confirmCategory = true)
        assertFalse(db.dao().task(stale.id)!!.categoryTrainingExcluded)
    }
    @Test fun resetSurvivesBackupAndUnrelatedStatusUpdates() = runTest {
        val repo = TaskRepository(db)
        learningHistory(repo)
        repo.resetLearning(io.github.dhianapereira.afazeres.model.nlp.LearningTarget.BOTH)
        repo.completeTasks(listOf("p0")); repo.reopenArchived(listOf("w0"))
        repo.restore(BackupCodec.decode(BackupCodec.encode(repo.snapshot())))
        repo.create(Task("after-reset", "Revisar contrato cliente"))
        assertNull(db.dao().task("after-reset")!!.categoryId)
        assertEquals(-1, db.dao().task("after-reset")!!.priority)
        assertTrue(db.dao().task("w0")!!.categoryConfirmed)
        assertTrue(db.dao().task("w0")!!.categoryTrainingExcluded)
    }
    @Test fun disabledAutomationDoesNotFillButKeepsHistoryAvailableForAudit() = runTest {
        val repo = TaskRepository(db)
        learningHistory(repo)
        repo.create(Task("disabled", "Revisar contrato cliente"), automatic = false)
        assertNull(db.dao().task("disabled")!!.categoryId)
        assertEquals(-1, db.dao().task("disabled")!!.priority)
        assertEquals("category:work", repo.explain("Revisar contrato cliente").category.prediction!!.label)
    }
    @Test fun priorityResetAndNewManualChoicesStartANewHistory() = runTest {
        val repo = TaskRepository(db)
        learningHistory(repo)
        repo.resetLearning(io.github.dhianapereira.afazeres.model.nlp.LearningTarget.PRIORITY)
        assertTrue(repo.snapshot().tasks.all { it.priorityTrainingExcluded && !it.categoryTrainingExcluded })
        for (task in repo.snapshot().tasks) repo.save(task, confirmPriority = true)
        repo.create(Task("relearned", "Revisar contrato cliente"))
        assertEquals(2, db.dao().task("relearned")!!.priority)
    }
    @Test fun deletedTasksKeepLearningThroughBackupAndReset() = runTest {
        val repo = TaskRepository(db)
        val task = Task("history", "Read book", priority = 2)
        repo.save(task)
        repo.deletePending(listOf(task.id))
        assertTrue(repo.snapshot().tasks.isEmpty())
        assertEquals(mapOf("read" to 1, "book" to 1), repo.snapshot().training.single().counts())
        val backup = BackupCodec.decode(BackupCodec.encode(repo.snapshot()))
        repo.resetLearning(io.github.dhianapereira.afazeres.model.nlp.LearningTarget.BOTH)
        assertTrue(repo.snapshot().training.isEmpty())
        repo.restore(backup)
        assertEquals(2, repo.snapshot().training.single().priority)
    }
    @Test fun correctionsReplaceHistoryAndArchivedDeletionPreservesIt() = runTest {
        val repo = TaskRepository(db)
        val task = Task("history", "Read book", priority = 2)
        repo.save(task)
        repo.save(task.copy(priority = 0), confirmPriority = true)
        repo.completeTasks(listOf(task.id))
        repo.deleteArchived(listOf(task.id))
        assertEquals(1, repo.snapshot().training.size)
        assertEquals(0, repo.snapshot().training.single().priority)
    }
    @Test fun forgetDeletionRemovesOnlyMatchingTasksAndTheirContributions() = runTest {
        val repo = TaskRepository(db)
        repo.save(Task("pending", "Read book", priority = 2))
        repo.save(Task("archived", "Read paper", priority = 1, done = true))
        repo.deletePending(listOf("pending", "archived"), forget = true)
        assertEquals(listOf("archived"), repo.snapshot().training.map { it.id })
        repo.deleteArchived(listOf("archived"), forget = true)
        assertTrue(repo.snapshot().training.isEmpty())
        repo.save(Task("single", "Read report", priority = 0))
        repo.delete(db.dao().task("single")!!, forget = true)
        assertTrue(repo.snapshot().training.isEmpty())
    }
    @Test fun auditShowsDeletedCountsAndPredictionsSurviveWithoutTitles() = runTest {
        val repo = TaskRepository(db)
        learningHistory(repo)
        val before = repo.explain("Revisar contrato cliente")
        val ids = repo.snapshot().tasks.map { it.id }
        repo.deletePending(ids)
        repo.deleteArchived(ids)
        assertEquals(before, repo.explain("Revisar contrato cliente"))
        val snapshot = repo.snapshot()
        val audit = io.github.dhianapereira.afazeres.model.nlp.TrainingExamples.summarize(snapshot.training, snapshot.tasks, snapshot.categories.map { it.id }.toSet())
        assertTrue(audit.categories.isEmpty())
        assertTrue(audit.priorities.isEmpty())
        assertEquals(8, audit.categoryCount)
        assertEquals(8, audit.priorityCount)
    }
}
