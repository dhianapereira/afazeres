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
}
