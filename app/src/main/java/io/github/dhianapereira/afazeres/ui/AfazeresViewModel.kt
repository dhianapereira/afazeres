package io.github.dhianapereira.afazeres.ui
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.dhianapereira.afazeres.AfazeresApplication
import io.github.dhianapereira.afazeres.R
import io.github.dhianapereira.afazeres.data.*
import io.github.dhianapereira.afazeres.model.nlp.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

class AfazeresViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AfazeresApplication
    private val repository = app.repository
    val tasks = repository.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val theme = app.preferences.theme.stateIn(viewModelScope, SharingStarted.Eagerly, "system")
    val automaticClassification = app.preferences.automaticClassification.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val training = combine(repository.training, tasks, categories) { records, tasks, categories ->
        TrainingExamples.summarize(records, tasks, categories.map { it.id }.toSet())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrainingExamples.Summary())
    data class Analysis(val title: String, val explanation: TaskClassifier.Explanation)
    val analysis = MutableStateFlow<Analysis?>(null)
    fun clearAnalysis() { analysis.value = null }
    fun analyze(title: String) = perform { analysis.value = Analysis(title, repository.explain(title)) }
    fun automaticClassification(value: Boolean) = perform { app.preferences.automaticClassification(value) }
    fun resetLearning(target: LearningTarget, success: () -> Unit) = perform(success) { repository.resetLearning(target); clearAnalysis() }
    val busy = MutableStateFlow(false)
    val operationError = MutableStateFlow<Int?>(null)
    fun clearError() { operationError.value = null }
    private val messages = Channel<Int>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private fun perform(success: (() -> Unit)? = null, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        operationError.value = null
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() }; success?.invoke() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val message = if (e is CategoryInUseException) R.string.category_in_use else when ((e as? BackupException)?.reason) { "empty" -> R.string.backup_empty; "large" -> R.string.backup_large; "version" -> R.string.backup_version; "invalid" -> R.string.backup_invalid; else -> R.string.operation_error }
                operationError.value = message
                messages.send(message)
            }
            finally { busy.value = false }
        }
    }
    fun create(task: Task, success: () -> Unit) = perform(success) { repository.create(task.copy(title = task.title.trim(), updatedAt = System.currentTimeMillis()), app.preferences.automaticClassification.first()) }
    fun save(task: Task, confirmCategory: Boolean = false, confirmPriority: Boolean = false, success: () -> Unit) = perform(success) { repository.save(task.copy(title = task.title.trim(), updatedAt = System.currentTimeMillis()), confirmCategory, confirmPriority) }
    fun save(category: Category, success: () -> Unit) = perform(success) { repository.save(category.copy(name = category.name.trim())) }
    fun delete(task: Task, forget: Boolean = false, success: () -> Unit) = perform(success) { repository.delete(task, forget) }
    fun delete(category: Category, success: () -> Unit) = perform(success) { repository.delete(category) }
    fun completeTasks(ids: List<String>, success: () -> Unit) = perform(success) { repository.completeTasks(ids) }
    fun deletePending(ids: List<String>, forget: Boolean, success: () -> Unit) = perform(success) { repository.deletePending(ids, forget) }
    fun reopenArchived(ids: List<String>, success: () -> Unit) = perform(success) { repository.reopenArchived(ids) }
    fun deleteArchived(ids: List<String>, forget: Boolean, success: () -> Unit) = perform(success) { repository.deleteArchived(ids, forget) }
    fun theme(value: String) = perform { app.preferences.theme(value) }
    fun export(uri: Uri) = perform {
        val text = BackupCodec.encode(repository.snapshot())
        app.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) } ?: error("output")
        messages.send(R.string.export_success)
    }
    fun restore(uri: Uri, success: () -> Unit) = perform(success) {
        val bytes = app.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > BackupCodec.MAX_BYTES) throw BackupException("large")
                output.write(buffer, 0, count)
            }
            output.toByteArray() } ?: error("input")
        if (bytes.size > BackupCodec.MAX_BYTES) throw BackupException("large")
        repository.restore(BackupCodec.decode(bytes.toString(Charsets.UTF_8)))
        messages.send(R.string.import_success)
    }
}
