package io.github.dhianapereira.afazeres

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.dhianapereira.afazeres.data.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AfazeresDatabase::class.java)

    @Test fun removesPlanningPeriodWithoutLosingTaskDetails() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL("INSERT INTO categories VALUES ('work', 'work', 0, 1)")
            execSQL("INSERT INTO tasks (id, title, note, categoryId, priority, period, done, createdAt, updatedAt) VALUES ('task', 'Keep title', 'Keep description', 'work', 2, 1, 0, 100, 200)")
            close()
        }
        helper.runMigrationsAndValidate("migration-test", 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT * FROM tasks").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Keep title", cursor.getString(cursor.getColumnIndexOrThrow("title")))
                assertEquals("Keep description", cursor.getString(cursor.getColumnIndexOrThrow("note")))
                assertEquals(2, cursor.getInt(cursor.getColumnIndexOrThrow("priority")))
                assertEquals("work", cursor.getString(cursor.getColumnIndexOrThrow("categoryId")))
                assertEquals(-1, cursor.getColumnIndex("period"))
            }
        }
    }
    @Test fun migratesCategoryAppearanceAndProtectsExistingTaskLinks() {
        helper.createDatabase("category-migration-test", 2).apply {
            for (index in 0..6) execSQL("INSERT INTO categories (id, name, color, builtIn) VALUES (?, ?, ?, 0)", arrayOf<Any>("c$index", "Category $index", index))
            execSQL("INSERT INTO tasks (id, title, note, categoryId, priority, done, createdAt, updatedAt) VALUES ('task', 'Keep task', '', 'c5', 2, 1, 100, 200)")
            close()
        }
        helper.runMigrationsAndValidate("category-migration-test", 3, true, MIGRATION_2_3).use { db ->
            db.query("SELECT * FROM categories ORDER BY rowid").use { cursor ->
                for (index in 0..6) {
                    assertTrue(cursor.moveToNext())
                    assertEquals("c$index", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                    assertEquals(io.github.dhianapereira.afazeres.model.CategoryAppearance.colors[index], cursor.getLong(cursor.getColumnIndexOrThrow("color")))
                    assertEquals(io.github.dhianapereira.afazeres.model.CategoryAppearance.legacyIcon(index), cursor.getString(cursor.getColumnIndexOrThrow("icon")))
                }
            }
            db.query("SELECT categoryId, done FROM tasks").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("c5", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
            }
            // MigrationTestHelper opens raw SQLite; Room enables this for app connections.
            db.setForeignKeyConstraintsEnabled(true)
            try { db.execSQL("DELETE FROM categories WHERE id = 'c5'"); fail("Archived link must be protected") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        }
    }
    @Test fun preservesManualLabelsWhenAddingLearningProvenance() {
        helper.createDatabase("learning-migration-test", 3).apply {
            execSQL("INSERT INTO categories (id, name, color, builtIn, icon) VALUES ('work', 'Work', 4282424053, 1, 'work')")
            execSQL("INSERT INTO tasks (id, title, note, categoryId, priority, done, createdAt, updatedAt) VALUES ('manual', 'Keep title', 'Keep note', 'work', 2, 1, 100, 200)")
            execSQL("INSERT INTO tasks (id, title, note, categoryId, priority, done, createdAt, updatedAt) VALUES ('empty', 'Only title', '', NULL, -1, 0, 100, 100)")
            close()
        }
        helper.runMigrationsAndValidate("learning-migration-test", 4, true, MIGRATION_3_4).use { db ->
            db.query("SELECT categoryConfirmed, priorityConfirmed, done, note FROM tasks WHERE id = 'manual'").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0)); assertEquals(1, it.getInt(1))
                assertEquals(1, it.getInt(2)); assertEquals("Keep note", it.getString(3))
            }
            db.query("SELECT categoryConfirmed, priorityConfirmed FROM tasks WHERE id = 'empty'").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0)); assertEquals(0, it.getInt(1))
            }
        }
    }
    @Test fun upgradesOriginalDatabaseThroughAllLearningMigrations() {
        helper.createDatabase("full-learning-migration", 1).apply {
            execSQL("INSERT INTO categories VALUES ('work', 'work', 0, 1)")
            execSQL("INSERT INTO tasks (id, title, note, categoryId, priority, period, done, createdAt, updatedAt) VALUES ('task', 'Original', '', 'work', 2, 1, 0, 100, 200)")
            close()
        }
        helper.runMigrationsAndValidate("full-learning-migration", 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).close()
    }
    @Test fun migrationKeepsExistingLearningEligible() {
        helper.createDatabase("audit-migration", 4).apply {
            execSQL("INSERT INTO tasks (id, title, note, priority, done, createdAt, updatedAt, categoryConfirmed, priorityConfirmed) VALUES ('task', 'Keep task', '', 2, 1, 100, 200, 0, 1)")
            close()
        }
        helper.runMigrationsAndValidate("audit-migration", 5, true, MIGRATION_4_5).use { db ->
            db.query("SELECT priority, done, priorityConfirmed, categoryTrainingExcluded, priorityTrainingExcluded FROM tasks").use {
                assertTrue(it.moveToFirst())
                assertEquals(2, it.getInt(0)); assertEquals(1, it.getInt(1)); assertEquals(1, it.getInt(2))
                assertEquals(0, it.getInt(3)); assertEquals(0, it.getInt(4))
            }
        }
    }
    @Test fun independentHistoryMigrationPreservesResetAndSurvivesDeletion() {
        helper.createDatabase("history-migration", 5).apply {
            execSQL("INSERT INTO tasks (id, title, note, priority, done, createdAt, updatedAt, categoryConfirmed, priorityConfirmed, categoryTrainingExcluded, priorityTrainingExcluded) VALUES ('kept', 'Read book', '', 2, 0, 0, 0, 0, 1, 0, 0), ('reset', 'Forget book', '', 2, 0, 0, 0, 0, 1, 0, 1)")
            close()
        }
        helper.runMigrationsAndValidate("history-migration", 6, true, MIGRATION_5_6).use { db ->
            db.execSQL("DELETE FROM tasks")
            db.query("SELECT id, priorityConfirmed FROM training_examples").use {
                assertTrue(it.moveToFirst())
                assertEquals("kept", it.getString(0))
                assertEquals(1, it.getInt(1))
                assertFalse(it.moveToNext())
            }
        }
    }
    @Test fun tokenMigrationRemovesTitleColumnAndPreservesCounts() {
        helper.createDatabase("token-migration", 6).apply {
            execSQL("INSERT INTO training_examples VALUES ('deleted', 'Read book book!', NULL, 2, 0, 1)")
            close()
        }
        helper.runMigrationsAndValidate("token-migration", 7, true, MIGRATION_6_7).use { db ->
            db.query("SELECT * FROM training_examples").use {
                assertEquals(-1, it.getColumnIndex("title"))
                assertTrue(it.moveToFirst())
                assertEquals(mapOf("read" to 1, "book" to 2), TokenCounts.decode(it.getString(it.getColumnIndexOrThrow("tokens"))))
            }
        }
    }
}
