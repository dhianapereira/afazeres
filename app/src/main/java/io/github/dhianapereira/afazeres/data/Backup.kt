package io.github.dhianapereira.afazeres.data

import io.github.dhianapereira.afazeres.model.CategoryAppearance
import org.json.JSONArray
import org.json.JSONObject

 data class Backup(val categories: List<Category>, val tasks: List<Task>, val training: List<TrainingRecord> = tasks.map { it.trainingRecord() }.filter { it.categoryConfirmed || it.priorityConfirmed })
class BackupException(val reason: String) : IllegalArgumentException(reason)
object BackupCodec {
    const val MAX_BYTES = 5 * 1024 * 1024
    private const val MAX_CATEGORIES = 1000
    private const val MAX_TASKS = 10000
    fun encode(backup: Backup): String {
        checkCounts(backup.categories.size, backup.tasks.size)
        checkCounts(backup.categories.size, backup.training.size)
        val text = JSONObject().put("format", "afazeres").put("version", 7)
        .put("categories", JSONArray().apply { backup.categories.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("builtIn", it.builtIn).put("icon", it.icon)) } })
        .put("tasks", taskArray(backup.tasks)).put("training", JSONArray().apply { backup.training.forEach { record ->
            put(JSONObject().put("id", record.id).put("tokens", JSONObject(record.tokens)).put("categoryId", record.categoryId ?: JSONObject.NULL)
                .put("priority", record.priority).put("categoryConfirmed", record.categoryConfirmed).put("priorityConfirmed", record.priorityConfirmed))
        } }).toString(2)
        if (text.toByteArray().size > MAX_BYTES) throw BackupException("large")
        return text
    }
    private fun taskArray(tasks: List<Task>) = JSONArray().apply { tasks.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("note", it.note).put("categoryId", it.categoryId ?: JSONObject.NULL).put("priority", it.priority).put("done", it.done).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt).put("categoryConfirmed", it.categoryConfirmed).put("priorityConfirmed", it.priorityConfirmed).put("categoryTrainingExcluded", it.categoryTrainingExcluded).put("priorityTrainingExcluded", it.priorityTrainingExcluded)) } }
    private fun checkCounts(categories: Int, tasks: Int) {
        if (categories > MAX_CATEGORIES || tasks > MAX_TASKS) throw BackupException("large")
    }
    fun decode(text: String): Backup {
        if (text.isBlank()) throw BackupException("empty")
        if (text.toByteArray().size > MAX_BYTES) throw BackupException("large")
        try {
            val root = JSONObject(text)
            require(root.getString("format") == "afazeres")
            val version = root.getInt("version")
            if (version !in 1..7) throw BackupException("version")
            val cs = root.getJSONArray("categories"); val ts = root.getJSONArray("tasks")
            checkCounts(cs.length(), ts.length())
            val categories = List(cs.length()) { i ->
                val item = cs.getJSONObject(i)
                require(item.get("color") is Int || item.get("color") is Long)
                val color = item.getLong("color")
                if (version < 3) require(color in 0..6)
                Category(
                    item.getString("id"), item.getString("name"),
                    if (version < 3) CategoryAppearance.colors[color.toInt()] else color,
                    item.getBoolean("builtIn"),
                    if (version < 3) CategoryAppearance.legacyIcon(color.toInt()) else item.getString("icon"),
                )
            }
            val tasks = List(ts.length()) { i ->
                val item = ts.getJSONObject(i)
                val categoryId = if (item.isNull("categoryId")) null else item.getString("categoryId")
                val priority = item.getInt("priority")
                if (version >= 4) require(item.get("categoryConfirmed") is Boolean && item.get("priorityConfirmed") is Boolean)
                if (version >= 5) require(item.get("categoryTrainingExcluded") is Boolean && item.get("priorityTrainingExcluded") is Boolean)
                Task(
                    item.getString("id"), item.getString("title"), item.getString("note"), categoryId,
                    priority, item.getBoolean("done"), item.getLong("createdAt"), item.getLong("updatedAt"),
                    if (version >= 4) item.getBoolean("categoryConfirmed") else categoryId != null,
                    if (version >= 4) item.getBoolean("priorityConfirmed") else priority >= 0,
                    if (version >= 5) item.getBoolean("categoryTrainingExcluded") else false,
                    if (version >= 5) item.getBoolean("priorityTrainingExcluded") else false,
                )
            }
            require(categories.map { it.id }.toSet().size == categories.size && tasks.map { it.id }.toSet().size == tasks.size)
            val ids = categories.map { it.id }.toSet()
            require(categories.all { it.id.isNotBlank() && it.id.length <= 100 && it.name.isNotBlank() && it.name.length <= 60 && CategoryAppearance.validColor(it.color) && it.icon in CategoryAppearance.icons && (!it.builtIn || it.id in setOf("work", "study", "personal", "health", "reading", "finance", "others")) })
            require(tasks.all { it.id.isNotBlank() && it.id.length <= 100 && it.title.isNotBlank() && it.title.length <= 200 && it.note.length <= 4000 && it.priority in -1..2 && (it.categoryId == null || it.categoryId in ids) && it.createdAt >= 0 && it.updatedAt >= it.createdAt })
            val training = if (version >= 7) {
                val history = root.getJSONArray("training")
                checkCounts(categories.size, history.length())
                List(history.length()) { index ->
                    val record = history.getJSONObject(index)
                    require(record.get("categoryConfirmed") is Boolean && record.get("priorityConfirmed") is Boolean)
                    val tokens = TokenCounts.encode(TokenCounts.decode(record.getJSONObject("tokens").toString()))
                    TrainingRecord(record.getString("id"), tokens, if (record.isNull("categoryId")) null else record.getString("categoryId"),
                        record.getInt("priority"), record.getBoolean("categoryConfirmed"), record.getBoolean("priorityConfirmed"))
                }.also { records ->
                    require(records.map { it.id }.toSet().size == records.size)
                    require(records.all { it.id.isNotBlank() && it.id.length <= 100 && it.priority in -1..2 && (it.categoryId == null || it.categoryId in ids) })
                }
            } else if (version == 6) {
                val history = root.getJSONArray("training")
                // Reuse the same strict record validation without recursive history decoding.
                decode(JSONObject(root.toString()).apply { remove("training") }.put("version", 5).put("tasks", history).toString()).training
            } else Backup(categories, tasks).training
            return Backup(categories, tasks, training)
        } catch (e: BackupException) { throw e } catch (_: Exception) { throw BackupException("invalid") }
    }
}
