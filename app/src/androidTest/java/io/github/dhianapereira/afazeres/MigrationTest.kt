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
}
