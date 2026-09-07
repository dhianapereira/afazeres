package io.github.dhianapereira.afazeres

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.dhianapereira.afazeres.data.*

class AfazeresApplication : Application() {
    val repository by lazy {
        val database = Room.databaseBuilder(this, AfazeresDatabase::class.java, "afazeres.db")
            .addMigrations(MIGRATION_1_2)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Seed only on database creation; an intentionally empty restore stays empty.
                    listOf("work", "study", "personal", "health", "reading", "finance", "others")
                        .forEachIndexed { index, id ->
                            db.execSQL("INSERT INTO categories (id, name, color, builtIn) VALUES (?, ?, ?, 1)", arrayOf(id, id, index))
                        }
                }
            }).build()
        TaskRepository(database)
    }
    val preferences by lazy { Preferences(this) }
}
