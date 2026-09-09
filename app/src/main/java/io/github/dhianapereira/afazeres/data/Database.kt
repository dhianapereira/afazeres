package io.github.dhianapereira.afazeres.data

import androidx.room.*
import io.github.dhianapereira.afazeres.model.CategoryAppearance
import io.github.dhianapereira.afazeres.model.TaskRules
import io.github.dhianapereira.afazeres.model.nlp.TaskClassifier
import io.github.dhianapereira.afazeres.model.nlp.LearningTarget
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories")
data class Category(@PrimaryKey val id: String, val name: String, val color: Long = CategoryAppearance.colors.first(), val builtIn: Boolean = false, @ColumnInfo(defaultValue = "'work'") val icon: String = "work")
@Entity(tableName = "tasks", foreignKeys = [ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("categoryId")])
data class Task(
    @PrimaryKey val id: String,
    val title: String,
    val note: String = "",
    val categoryId: String? = null,
    val priority: Int = -1,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    @ColumnInfo(defaultValue = "0") val categoryConfirmed: Boolean = categoryId != null,
    @ColumnInfo(defaultValue = "0") val priorityConfirmed: Boolean = priority >= 0,
    @ColumnInfo(defaultValue = "0") val categoryTrainingExcluded: Boolean = false,
    @ColumnInfo(defaultValue = "0") val priorityTrainingExcluded: Boolean = false,
)
@Entity(tableName = "training_examples")
data class TrainingRecord(@PrimaryKey val id: String, val tokens: String, val categoryId: String?, val priority: Int, val categoryConfirmed: Boolean, val priorityConfirmed: Boolean) {
    fun counts(): Map<String, Int> = TokenCounts.decode(tokens)
}
internal fun Task.trainingRecord() = TrainingRecord(id, TokenCounts.encode(io.github.dhianapereira.afazeres.model.nlp.TextTokenizer.counts(title)), categoryId, priority,
    categoryConfirmed && !categoryTrainingExcluded, priorityConfirmed && !priorityTrainingExcluded)

@Dao
interface AfazeresDao {
    @Query("SELECT * FROM training_examples ORDER BY id") fun training(): Flow<List<TrainingRecord>>
    @Query("SELECT * FROM training_examples ORDER BY id") suspend fun snapshotTraining(): List<TrainingRecord>
    @Upsert suspend fun save(record: TrainingRecord)
    @Query("DELETE FROM training_examples WHERE id IN (:ids)") suspend fun forgetTasks(ids: List<String>)
    @Query("DELETE FROM training_examples") suspend fun clearTraining()
    @Query("UPDATE training_examples SET categoryConfirmed = 0, categoryId = NULL") suspend fun forgetCategories()
    @Query("UPDATE training_examples SET priorityConfirmed = 0, priority = -1") suspend fun forgetPriorities()
    @Query("DELETE FROM training_examples WHERE categoryConfirmed = 0 AND priorityConfirmed = 0") suspend fun pruneTraining()
    @Query("UPDATE training_examples SET categoryConfirmed = 0, categoryId = NULL WHERE categoryId = :id") suspend fun forgetCategory(id: String)

    @Query("SELECT * FROM tasks ORDER BY priority DESC, createdAt DESC, id ASC") fun tasks(): Flow<List<Task>>
    @Query("SELECT * FROM categories ORDER BY rowid") fun categories(): Flow<List<Category>>
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun task(id: String): Task?
    @Upsert suspend fun save(task: Task)
    @Upsert suspend fun save(category: Category)
    @Delete suspend fun delete(task: Task)
    @Delete suspend fun delete(category: Category)
    @Query("SELECT COUNT(*) FROM tasks WHERE categoryId = :categoryId") suspend fun categoryTaskCount(categoryId: String): Int
    @Query("UPDATE tasks SET done = 1, updatedAt = :updatedAt WHERE done = 0 AND id IN (:ids)")
    suspend fun completeTasks(ids: List<String>, updatedAt: Long)
    @Query("DELETE FROM tasks WHERE done = 0 AND id IN (:ids)")
    suspend fun deletePending(ids: List<String>)
    @Query("UPDATE tasks SET done = 0, updatedAt = :updatedAt WHERE done = 1 AND id IN (:ids)")
    suspend fun reopenArchived(ids: List<String>, updatedAt: Long)
    @Query("DELETE FROM tasks WHERE done = 1 AND id IN (:ids)")
    suspend fun deleteArchived(ids: List<String>)
    @Query("UPDATE tasks SET categoryTrainingExcluded = 1") suspend fun resetCategoryLearning()
    @Query("UPDATE tasks SET priorityTrainingExcluded = 1") suspend fun resetPriorityLearning()
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("SELECT * FROM tasks ORDER BY createdAt, id") suspend fun snapshotTasks(): List<Task>
    @Query("SELECT * FROM categories ORDER BY rowid") suspend fun snapshotCategories(): List<Category>
}
@Database(entities = [Task::class, Category::class, TrainingRecord::class], version = 7, exportSchema = true)
abstract class AfazeresDatabase : RoomDatabase() { abstract fun dao(): AfazeresDao }
class TaskRepository(private val db: AfazeresDatabase) {
    val training = db.dao().training()
    val tasks = db.dao().tasks()
    val categories = db.dao().categories()
    suspend fun create(task: Task, automatic: Boolean = true) = db.withTransaction {
        require(TaskRules.valid(task.title, task.note, task.priority))
        require(db.dao().task(task.id) == null)
        val classified = if (automatic) {
            val categories = db.dao().snapshotCategories().map { it.id }.toSet()
            TaskClassifier.fromRecords(db.dao().snapshotTraining(), categories).fill(task)
        } else task
        db.dao().save(classified)
        db.dao().save(classified.trainingRecord())
        db.dao().pruneTraining()
    }
    suspend fun save(task: Task, confirmCategory: Boolean = false, confirmPriority: Boolean = false) = db.withTransaction {
        require(TaskRules.valid(task.title, task.note, task.priority))
        val stored = db.dao().task(task.id)
        val updated = task.copy(
            categoryConfirmed = task.categoryConfirmed || confirmCategory,
            priorityConfirmed = task.priorityConfirmed || confirmPriority,
            categoryTrainingExcluded = if (confirmCategory) false else stored?.categoryTrainingExcluded ?: task.categoryTrainingExcluded,
            priorityTrainingExcluded = if (confirmPriority) false else stored?.priorityTrainingExcluded ?: task.priorityTrainingExcluded,
        )
        db.dao().save(updated)
        db.dao().save(updated.trainingRecord())
        db.dao().pruneTraining()
    }
    suspend fun resetLearning(target: LearningTarget) = db.withTransaction {
        if (target != LearningTarget.PRIORITY) { db.dao().resetCategoryLearning(); db.dao().forgetCategories() }
        if (target != LearningTarget.CATEGORY) { db.dao().resetPriorityLearning(); db.dao().forgetPriorities() }
        db.dao().pruneTraining()
    }
    suspend fun explain(title: String): TaskClassifier.Explanation = db.withTransaction {
        TaskClassifier.fromRecords(db.dao().snapshotTraining(), db.dao().snapshotCategories().map { it.id }.toSet()).explain(title)
    }
    suspend fun save(category: Category) { require(category.name.isNotBlank() && category.name.length <= 60 && CategoryAppearance.validColor(category.color) && category.icon in CategoryAppearance.icons); db.dao().save(category) }
    suspend fun delete(task: Task, forget: Boolean = false) = db.withTransaction {
        if (forget) db.dao().forgetTasks(listOf(task.id))
        db.dao().delete(task)
    }
    suspend fun delete(category: Category) = db.withTransaction {
        if (db.dao().categoryTaskCount(category.id) > 0) throw CategoryInUseException()
        db.dao().forgetCategory(category.id)
        db.dao().pruneTraining()
        db.dao().delete(category)
    }
    suspend fun completeTasks(ids: List<String>) = db.withTransaction {
        val updatedAt = System.currentTimeMillis()
        ids.distinct().chunked(900).forEach { db.dao().completeTasks(it, updatedAt) }
    }
    suspend fun deletePending(ids: List<String>, forget: Boolean = false) = db.withTransaction {
        if (forget) forgetMatching(ids, false)
        ids.distinct().chunked(900).forEach { db.dao().deletePending(it) }
    }
    suspend fun reopenArchived(ids: List<String>) = db.withTransaction {
        val updatedAt = System.currentTimeMillis()
        ids.distinct().chunked(900).forEach { db.dao().reopenArchived(it, updatedAt) }
    }
    suspend fun deleteArchived(ids: List<String>, forget: Boolean = false) = db.withTransaction {
        if (forget) forgetMatching(ids, true)
        ids.distinct().chunked(900).forEach { db.dao().deleteArchived(it) }
    }
    private suspend fun forgetMatching(ids: List<String>, done: Boolean) {
        val requested = ids.toSet()
        db.dao().snapshotTasks().filter { it.id in requested && it.done == done }.map { it.id }
            .chunked(900).forEach { db.dao().forgetTasks(it) }
    }
    suspend fun snapshot(): Backup = db.withTransaction { Backup(db.dao().snapshotCategories(), db.dao().snapshotTasks(), db.dao().snapshotTraining()) }
    suspend fun restore(backup: Backup) = db.withTransaction {
        db.dao().clearTraining()
        db.dao().clearTasks(); db.dao().clearCategories()
        backup.categories.forEach { db.dao().save(it) }; backup.tasks.forEach { db.dao().save(it) }
        backup.training.forEach { db.dao().save(it) }
        db.dao().pruneTraining()
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

val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN icon TEXT NOT NULL DEFAULT 'work'")
        CategoryAppearance.colors.forEachIndexed { index, color ->
            db.execSQL("UPDATE categories SET color = ?, icon = ? WHERE color = ?", arrayOf(color, CategoryAppearance.legacyIcon(index), index))
        }
        db.execSQL("CREATE TABLE tasks_new (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, note TEXT NOT NULL, categoryId TEXT, priority INTEGER NOT NULL, done INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("INSERT INTO tasks_new SELECT id, title, note, categoryId, priority, done, createdAt, updatedAt FROM tasks")
        db.execSQL("DROP TABLE tasks")
        db.execSQL("ALTER TABLE tasks_new RENAME TO tasks")
        db.execSQL("CREATE INDEX index_tasks_categoryId ON tasks(categoryId)")
    }
}

class CategoryInUseException : IllegalStateException()

// All existing nonempty labels predate automatic classification and were chosen manually.
val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN categoryConfirmed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN priorityConfirmed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE tasks SET categoryConfirmed = 1 WHERE categoryId IS NOT NULL")
        db.execSQL("UPDATE tasks SET priorityConfirmed = 1 WHERE priority >= 0")
    }
}

val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN categoryTrainingExcluded INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN priorityTrainingExcluded INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS training_examples (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, categoryId TEXT, priority INTEGER NOT NULL, categoryConfirmed INTEGER NOT NULL, priorityConfirmed INTEGER NOT NULL)")
        db.execSQL("INSERT INTO training_examples SELECT id, title, categoryId, priority, categoryConfirmed AND NOT categoryTrainingExcluded, priorityConfirmed AND NOT priorityTrainingExcluded FROM tasks WHERE (categoryConfirmed AND NOT categoryTrainingExcluded) OR (priorityConfirmed AND NOT priorityTrainingExcluded)")
    }
}

// Keep only unordered word counts. No task title or description survives in this table.
val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE training_new (id TEXT NOT NULL PRIMARY KEY, tokens TEXT NOT NULL, categoryId TEXT, priority INTEGER NOT NULL, categoryConfirmed INTEGER NOT NULL, priorityConfirmed INTEGER NOT NULL)")
        db.query("SELECT id, title, categoryId, priority, categoryConfirmed, priorityConfirmed FROM training_examples").use { cursor ->
            while (cursor.moveToNext()) {
                val tokens = TokenCounts.encode(io.github.dhianapereira.afazeres.model.nlp.TextTokenizer.counts(cursor.getString(1)))
                db.execSQL("INSERT INTO training_new VALUES (?, ?, ?, ?, ?, ?)", arrayOf(cursor.getString(0), tokens, if (cursor.isNull(2)) null else cursor.getString(2), cursor.getInt(3), cursor.getInt(4), cursor.getInt(5)))
            }
        }
        db.execSQL("DROP TABLE training_examples")
        db.execSQL("ALTER TABLE training_new RENAME TO training_examples")
    }
}
