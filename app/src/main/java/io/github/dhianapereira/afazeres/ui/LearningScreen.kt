package io.github.dhianapereira.afazeres.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.dhianapereira.afazeres.R
import io.github.dhianapereira.afazeres.data.Category
import io.github.dhianapereira.afazeres.model.nlp.*

@Composable
internal fun LearningScreen(
    page: String, categories: List<Category>, training: TrainingExamples.Summary,
    automatic: Boolean, busy: Boolean, error: Int?, analysis: AfazeresViewModel.Analysis?,
    onPage: (String) -> Unit, onTask: (String) -> Unit, onToggle: (Boolean) -> Unit,
    onAnalyze: (String) -> Unit, onClearAnalysis: () -> Unit,
    onReset: (LearningTarget, () -> Unit) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var resetting by rememberSaveable { mutableStateOf(false) }
    var target by rememberSaveable { mutableStateOf(LearningTarget.BOTH) }
    val focus = LocalFocusManager.current
    val automaticLabel = stringResource(R.string.automatic_classification)
    LaunchedEffect(training, categories) { onClearAnalysis() }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        when (page) {
            "learning" -> {
                item {
                    Surface(shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.automatic_classification), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.automatic_classification_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = automatic, onCheckedChange = onToggle, enabled = !busy,
                                modifier = Modifier.semantics { contentDescription = automaticLabel })
                        }
                    }
                }
                item { Text(stringResource(R.string.learning_how), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item {
                    SettingsRow(Icons.Outlined.GridView, R.string.category_examples, pluralStringResource(R.plurals.training_examples, training.categoryCount, training.categoryCount)) { onPage("learning_categories") }
                }
                item {
                    SettingsRow(Icons.Outlined.Flag, R.string.priority_examples, pluralStringResource(R.plurals.training_examples, training.priorityCount, training.priorityCount)) { onPage("learning_priorities") }
                }
                item { SettingsRow(Icons.Outlined.Science, R.string.simulate_learning, stringResource(R.string.simulate_hint)) { onPage("learning_simulate") } }
                item {
                    TextButton(onClick = { resetting = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.reset_learning), color = MaterialTheme.colorScheme.error)
                    }
                    error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                }
            }
            "learning_categories", "learning_priorities" -> {
                val categoryMode = page == "learning_categories"
                val examples = if (categoryMode) training.categories else training.priorities
                item { Text(stringResource(R.string.training_examples_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                val deleted = if (categoryMode) training.deletedCategories else training.deletedPriorities
                if (examples.isEmpty() && deleted.isEmpty()) item { Text(stringResource(R.string.no_training_examples), Modifier.padding(vertical = 24.dp)) }
                val groups = examples.groupBy { if (categoryMode) it.categoryId else it.priority.toString() }
                (groups.keys + deleted.keys).forEach { label ->
                    val tasks = groups[label].orEmpty()
                    item(key = "$page:group:$label") {
                        TrainingGroup(trainingLabel(label, categoryMode, categories), tasks, deleted[label] ?: 0, onTask)
                    }
                }
            }
            "learning_simulate" -> {
                item {
                    Text(stringResource(R.string.simulate_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!automatic) Text(stringResource(R.string.simulate_disabled_hint), Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    OutlinedTextField(value = query, onValueChange = { if (it.length <= 200) { query = it; onClearAnalysis() } },
                        label = { Text(stringResource(R.string.simulate_title)) }, shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(), minLines = 2)
                }
                item {
                    Button(onClick = { focus.clearFocus(); onAnalyze(query) }, enabled = !busy && query.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.analyze_title))
                    }
                    error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                }
                if (analysis?.title == query) {
                    item { ExplanationCard(analysis.explanation.category, true, categories) }
                    item { ExplanationCard(analysis.explanation.priority, false, categories) }
                }
            }
        }
    }
    if (resetting) AppSheet(R.string.reset_learning, { if (!busy) resetting = false }) {
        Text(stringResource(R.string.reset_learning_confirmation))
        LearningTarget.entries.forEach { value ->
            Surface(onClick = { target = value }, enabled = !busy, shape = MaterialTheme.shapes.medium,
                color = if (target == value) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadioButton(selected = target == value, onClick = null)
                    Text(stringResource(when (value) { LearningTarget.CATEGORY -> R.string.category; LearningTarget.PRIORITY -> R.string.filter_priority; LearningTarget.BOTH -> R.string.learning_both }))
                }
            }
        }
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onReset(target) { resetting = false } }, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.confirm_reset_learning))
        }
    }
}

@Composable
private fun trainingLabel(label: String?, category: Boolean, categories: List<Category>): String = if (category) {
    categories.find { it.id == label }?.let { categoryName(it) } ?: stringResource(R.string.no_category)
} else stringResource(when (label?.toIntOrNull()) { 2 -> R.string.high; 1 -> R.string.medium; 0 -> R.string.low; else -> R.string.no_priority })

@Composable
private fun ExplanationCard(explanation: NaiveBayesClassifier.Explanation, category: Boolean, categories: List<Category>) {
    val prediction = explanation.prediction
    val label = if (category) prediction?.label?.takeIf { it.startsWith("category:") }?.removePrefix("category:") else prediction?.label
    Surface(shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (category) R.string.category else R.string.filter_priority), style = MaterialTheme.typography.titleMedium)
            Text(if (prediction == null) stringResource(R.string.no_prediction) else trainingLabel(label, category, categories), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(when (explanation.reason) {
                NaiveBayesClassifier.Reason.NO_WORDS -> R.string.reason_no_words
                NaiveBayesClassifier.Reason.INSUFFICIENT_EXAMPLES -> R.string.reason_few_examples
                NaiveBayesClassifier.Reason.SINGLE_LABEL -> R.string.reason_single_label
                NaiveBayesClassifier.Reason.UNKNOWN_WORDS -> R.string.reason_unknown_words
                NaiveBayesClassifier.Reason.LOW_COVERAGE -> R.string.reason_low_coverage
                NaiveBayesClassifier.Reason.WEAK_SUPPORT -> R.string.reason_weak_support
                NaiveBayesClassifier.Reason.WEAK_EVIDENCE -> R.string.reason_weak_evidence
                NaiveBayesClassifier.Reason.LOW_SCORE -> R.string.reason_low_score
                NaiveBayesClassifier.Reason.ACCEPTED -> R.string.reason_accepted
            }))
            Text(pluralStringResource(R.plurals.training_examples, explanation.exampleCount, explanation.exampleCount), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.recognized_words, explanation.knownWords.joinToString(", ").ifEmpty { stringResource(R.string.no_recognized_words) }), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@Composable
private fun TrainingGroup(label: String, tasks: List<io.github.dhianapereira.afazeres.data.Task>, deletedCount: Int, onTask: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(tasks.size) { page = validPage(page, tasks.size) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(onClick = { expanded = !expanded }, shape = MaterialTheme.shapes.medium) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    Text(pluralStringResource(R.plurals.training_examples, tasks.size + deletedCount, tasks.size + deletedCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    stringResource(if (expanded) R.string.collapse_group else R.string.expand_group))
            }
        }
        if (expanded) {
            if (deletedCount > 0) Text(pluralStringResource(R.plurals.deleted_training_examples, deletedCount, deletedCount),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Pagination(page, tasks.size) { page = it }
            pageItems(tasks, page).forEach { task ->
                key(task.id) {
                    Surface(onClick = { onTask(task.id) }, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(task.title)
                            Text(stringResource(if (task.done) R.string.view_archived_training_task else R.string.view_training_task), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Pagination(page, tasks.size) { page = it }
        }
    }
}
