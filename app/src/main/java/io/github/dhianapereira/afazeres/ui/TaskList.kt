package io.github.dhianapereira.afazeres.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.dhianapereira.afazeres.R
import io.github.dhianapereira.afazeres.data.Category
import io.github.dhianapereira.afazeres.data.Task
import io.github.dhianapereira.afazeres.model.TaskSelection

@Composable
internal fun TaskList(
    tasks: List<Task>, categories: List<Category>, busy: Boolean, error: Int?,
    archived: Boolean, emptyLabel: Int,
    open: (String) -> Unit,
    changeStatus: (List<String>, () -> Unit) -> Unit,
    delete: (List<String>, Boolean, () -> Unit) -> Unit,
) {
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(0) }
    val visibleTasks = pageItems(tasks, page)
    val pageIds = visibleTasks.map { it.id }
    val ids = tasks.map { it.id }
    val current = TaskSelection.current(selected, ids)
    LaunchedEffect(tasks.size) { page = validPage(page, tasks.size) }
    LaunchedEffect(page) { listState.scrollToItem(0) }
    val finish = { selecting = false; selected = emptyList<String>(); confirmingDelete = false }
    BackHandler(selecting && !confirmingDelete) { if (!busy) finish() }
    LaunchedEffect(selecting) { if (selecting) listState.animateScrollToItem(0) }
    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Pagination(page, tasks.size, !busy) { page = it } }
        if (selecting || archived) item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (selecting) pluralStringResource(R.plurals.selected_tasks, current.size, current.size) else stringResource(R.string.archived_description),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selecting) TextButton(onClick = finish, enabled = !busy) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
        if (selecting) {
            item {
                TextButton(onClick = { selected = if (pageIds.all { it in current }) current - pageIds.toSet() else (current + pageIds).distinct() }, enabled = !busy && ids.isNotEmpty()) {
                    Icon(Icons.Outlined.Checklist, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (pageIds.all { it in current }) R.string.clear_page_selection else R.string.select_all))
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { changeStatus(current, finish) }, enabled = !busy && current.isNotEmpty(), modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp)) {
                        Text(stringResource(if (archived) R.string.unarchive_selected else R.string.complete_selected), textAlign = TextAlign.Center)
                    }
                    OutlinedButton(onClick = { confirmingDelete = true }, enabled = !busy && current.isNotEmpty(), modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text(stringResource(R.string.delete_selected), textAlign = TextAlign.Center)
                    }
                }
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
        if (tasks.isEmpty()) item { Text(stringResource(emptyLabel), Modifier.padding(vertical = 36.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(visibleTasks, key = { it.id }) { task ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) Checkbox(modifier = Modifier.semantics { contentDescription = task.title }, checked = task.id in current, enabled = !busy, onCheckedChange = { selected = TaskSelection.toggle(current, task.id) })
                Box(Modifier.weight(1f)) {
                    TaskCard(task, categories.find { it.id == task.categoryId }, enabled = !busy, onLongClick = {
                        focus.clearFocus()
                        selecting = true
                        if (task.id !in current) selected = current + task.id
                    }) {
                        if (!busy) { if (selecting) selected = TaskSelection.toggle(current, task.id) else open(task.id) }
                    }
                }
            }
        }
        item { Pagination(page, tasks.size, !busy) { page = it } }
    }
    if (confirmingDelete) AppSheet(R.string.delete_selected, { if (!busy) confirmingDelete = false }) {
        Text(pluralStringResource(R.plurals.delete_tasks_confirmation, current.size, current.size))
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        Button(onClick = { delete(current, false, finish) }, enabled = !busy && current.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
            Text(stringResource(R.string.delete_selected), textAlign = TextAlign.Center)
        }
        Text(stringResource(R.string.delete_learning_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { delete(current, true, finish) }, enabled = !busy && current.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.delete_and_forget), textAlign = TextAlign.Center)
        }
        TextButton(onClick = { confirmingDelete = false }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
    }
}
