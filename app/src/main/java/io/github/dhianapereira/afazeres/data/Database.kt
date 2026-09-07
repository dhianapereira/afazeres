package io.github.dhianapereira.afazeres.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories")
data class Category(@PrimaryKey val id: String, val name: String, val color: Int = 0, val builtIn: Boolean = false)
@Entity(tableName = "tasks", foreignKeys = [ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)], indices = [Index("categoryId")])
data class Task(@PrimaryKey val id: String, val title: String, val note: String = "", val categoryId: String? = null, val priority: Int = -1, val done: Boolean = false, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt)
@Dao
interface AfazeresDao {
    @Query("SELECT * FROM tasks ORDER BY priority DESC, createdAt DESC, id ASC") fun tasks(): Flow<List<Task>>
    @Query("SELECT * FROM categories ORDER BY rowid") fun categories(): Flow<List<Category>>
    @Upsert suspend fun save(task: Task)
    @Upsert suspend fun save(category: Category)
    @Delete suspend fun delete(task: Task)
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("SELECT * FROM tasks ORDER BY createdAt, id") suspend fun snapshotTasks(): List<Task>
    @Query("SELECT * FROM categories ORDER BY rowid") suspend fun snapshotCategories(): List<Category>
}
@Database(entities = [Task::class, Category::class], version = 2, exportSchema = true)
abstract class AfazeresDatabase : RoomDatabase() { abstract fun dao(): AfazeresDao }
class TaskRepository(private val db: AfazeresDatabase) {
    val tasks = db.dao().tasks()
    val categories = db.dao().categories()
    suspend fun save(task: Task) { require(task.title.isNotBlank() && task.title.length <= 200 && task.note.length <= 4000); db.dao().save(task) }
    suspend fun save(category: Category) { require(category.name.isNotBlank() && category.name.length <= 60); db.dao().save(category) }
    suspend fun delete(task: Task) = db.dao().delete(task)
    suspend fun snapshot(): Backup = db.withTransaction { Backup(db.dao().snapshotCategories(), db.dao().snapshotTasks()) }
    suspend fun restore(backup: Backup) = db.withTransaction {
        db.dao().clearTasks(); db.dao().clearCategories()
        backup.categories.forEach { db.dao().save(it) }; backup.tasks.forEach { db.dao().save(it) }
    }
}

// Preserve existing tasks while removing the former planning period.
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE tasks_new (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, note TEXT NOT NULL, categoryId TEXT, priority INTEGER NOT NULL, done INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
        db.execSQL("INSERT INTO tasks_new SELECT id, title, note, categoryId, priority, done, createdAt, updatedAt FROM tasks")
        db.execSQL("DROP TABLE tasks")
        db.execSQL("ALTER TABLE tasks_new RENAME TO tasks")
        db.execSQL("CREATE INDEX index_tasks_categoryId ON tasks(categoryId)")
    }
}
