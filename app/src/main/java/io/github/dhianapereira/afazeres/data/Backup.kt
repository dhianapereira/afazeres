package io.github.dhianapereira.afazeres.data

import org.json.JSONArray
import org.json.JSONObject

 data class Backup(val categories: List<Category>, val tasks: List<Task>)
class BackupException(val reason: String) : IllegalArgumentException(reason)
object BackupCodec {
    const val MAX_BYTES = 5 * 1024 * 1024
    fun encode(backup: Backup): String = JSONObject().put("format", "afazeres").put("version", 2)
        .put("categories", JSONArray().apply { backup.categories.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("builtIn", it.builtIn)) } })
        .put("tasks", JSONArray().apply { backup.tasks.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("note", it.note).put("categoryId", it.categoryId ?: JSONObject.NULL).put("priority", it.priority).put("done", it.done).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt)) } }).toString(2)
    fun decode(text: String): Backup {
        if (text.isBlank()) throw BackupException("empty")
        if (text.toByteArray().size > MAX_BYTES) throw BackupException("large")
        try {
            val root = JSONObject(text)
            require(root.getString("format") == "afazeres")
            if (root.getInt("version") !in 1..2) throw BackupException("version")
            val cs = root.getJSONArray("categories"); val ts = root.getJSONArray("tasks")
            if (cs.length() > 1000 || ts.length() > 10000) throw BackupException("large")
            val categories = List(cs.length()) { i -> cs.getJSONObject(i).let { Category(it.getString("id"), it.getString("name"), it.getInt("color"), it.getBoolean("builtIn")) } }
            val tasks = List(ts.length()) { i -> ts.getJSONObject(i).let { Task(it.getString("id"), it.getString("title"), it.getString("note"), if (it.isNull("categoryId")) null else it.getString("categoryId"), it.getInt("priority"), it.getBoolean("done"), it.getLong("createdAt"), it.getLong("updatedAt")) } }
            require(categories.map { it.id }.toSet().size == categories.size && tasks.map { it.id }.toSet().size == tasks.size)
            val ids = categories.map { it.id }.toSet()
            require(categories.all { it.id.isNotBlank() && it.id.length <= 100 && it.name.isNotBlank() && it.name.length <= 60 && it.color in 0..6 && (!it.builtIn || it.id in setOf("work", "study", "personal", "health", "reading", "finance", "others")) })
            require(tasks.all { it.id.isNotBlank() && it.id.length <= 100 && it.title.isNotBlank() && it.title.length <= 200 && it.note.length <= 4000 && it.priority in -1..2 && (it.categoryId == null || it.categoryId in ids) && it.createdAt >= 0 && it.updatedAt >= it.createdAt })
            return Backup(categories, tasks)
        } catch (e: BackupException) { throw e } catch (_: Exception) { throw BackupException("invalid") }
    }
}
