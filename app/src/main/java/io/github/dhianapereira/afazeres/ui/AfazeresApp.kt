package io.github.dhianapereira.afazeres.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.core.net.toUri
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dhianapereira.afazeres.R
import io.github.dhianapereira.afazeres.data.*
import io.github.dhianapereira.afazeres.ui.theme.*
import java.time.LocalDate
import java.util.UUID

private val LocalOperationError = staticCompositionLocalOf<Int?> { null }

@Composable
fun AfazeresApp(vm: AfazeresViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val operationError by vm.operationError.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val automaticClassification by vm.automaticClassification.collectAsStateWithLifecycle()
    val training by vm.training.collectAsStateWithLifecycle()
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var quickTitle by rememberSaveable { mutableStateOf("") }
    var settingsPage by rememberSaveable { mutableStateOf("main") }
    var managedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingTask by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var priorityFilter by rememberSaveable { mutableStateOf<Int?>(null) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreUri by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(sheet, restoreUri, editingTask) { vm.clearError() }
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(vm, resources) { vm.events.collect { snackbar.showSnackbar(resources.getString(it)) } }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::export) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreUri = it?.toString() }
    val task = tasks.find { it.id == selected }
    val managedCategory = categories.find { it.id == managedCategoryId }
    val addTask: () -> Unit = {
        if (!busy && quickTitle.isNotBlank()) {
            val submittedTitle = quickTitle
            vm.create(Task(UUID.randomUUID().toString(), submittedTitle.trim())) {
                if (quickTitle == submittedTitle) quickTitle = ""
                priorityFilter = null
                categoryFilter = null
                tab = 0
            }
        }
    }
    BackHandler(selected != null || categoryFilter != null || tab != 0 || settingsPage != "main") {
        when { editingTask -> { if (!busy) editingTask = false }; selected != null -> selected = null; tab == 2 && settingsPage != "main" -> settingsPage = settingsParent(settingsPage); categoryFilter != null -> categoryFilter = null; else -> tab = 0 }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (selected == null) NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                listOf(R.string.home to Icons.Outlined.Home, R.string.categories to Icons.Outlined.GridView, R.string.settings to Icons.Outlined.Settings).forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index; categoryFilter = null }, icon = { Icon(icon, null) }, label = { Text(stringResource(label)) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent, selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 680.dp).fillMaxSize()) {
                when {
                    selected != null && task != null && editingTask -> TaskEditor(task, categories, busy, operationError, { editingTask = false }) { updated, confirmCategory, confirmPriority ->
                        vm.save(updated, confirmCategory, confirmPriority) { editingTask = false; if (updated.done != task.done) selected = null }
                    }
                    selected != null && task != null -> Detail(task, categories, { selected = null }, { editingTask = true }, { vm.save(task.copy(done = !task.done)) { selected = null } }, { sheet = "delete" }, busy)
                    else -> Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp)) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 88.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (tab == 2 && settingsPage != "main") IconButton(onClick = { settingsPage = settingsParent(settingsPage) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
                            if (tab == 1 && categoryFilter != null) IconButton(onClick = { categoryFilter = null }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
                            Text(if (tab == 1 && categoryFilter != null) categories.find { it.id == categoryFilter }?.let { categoryName(it) }.orEmpty() else if (tab == 2) stringResource(settingsTitle(settingsPage)) else stringResource(listOf(R.string.app_name, R.string.categories, R.string.settings)[tab]), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                            if (tab == 0 || tab == 1 && categoryFilter != null) {
                                val activeCount = (if (priorityFilter != null) 1 else 0) + (if (categoryFilter != null) 1 else 0)
                                IconButton(onClick = { sheet = "filters" }) {
                                    BadgedBox(badge = { if (activeCount > 0) Badge { Text(java.text.NumberFormat.getIntegerInstance().format(activeCount)) } }) {
                                        Icon(Icons.Outlined.Tune, stringResource(if (activeCount > 0) R.string.filters_active else R.string.filters))
                                    }
                                }
                            }
                            if (tab == 1 && categoryFilter != null) IconButton(onClick = { managedCategoryId = categoryFilter; sheet = "category_actions" }) { Icon(Icons.Outlined.MoreHoriz, stringResource(R.string.category_actions)) }
                            if (tab == 1 && categoryFilter == null) IconButton(onClick = { managedCategoryId = null; sheet = "category" }, enabled = !busy) { Icon(Icons.Outlined.Add, stringResource(R.string.new_category)) }
                        }
                        when {
                            tab == 0 || categoryFilter != null -> {
                                OutlinedTextField(
                                    value = quickTitle,
                                    onValueChange = { if (it.length <= 200) quickTitle = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text(stringResource(R.string.capture_hint)) },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium,
                                    trailingIcon = {
                                        IconButton(onClick = addTask, enabled = !busy && quickTitle.isNotBlank()) {
                                            Icon(Icons.Outlined.Add, stringResource(R.string.add_task))
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { addTask() }),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = .5f),
                                    ),
                                )
                                Spacer(Modifier.height(16.dp))
                                val filtered = tasks.filter { !it.done && (priorityFilter == null || it.priority == priorityFilter) && (categoryFilter == null || it.categoryId == categoryFilter) }
                                Text(
                                    stringResource(R.string.pending_total, tasks.count { !it.done }),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                                key(tab, categoryFilter, priorityFilter) {
                                    TaskList(
                                        tasks = filtered, categories = categories, busy = busy, error = operationError,
                                        archived = false, emptyLabel = if (priorityFilter != null || categoryFilter != null) R.string.empty_filtered else R.string.empty_tasks,
                                        open = { selected = it }, changeStatus = vm::completeTasks, delete = vm::deletePending,
                                    )
                                }
                            }
                            tab == 1 && categories.isEmpty() -> EmptyState(R.string.empty_categories)
                            tab == 1 -> {
                                var categoryPage by rememberSaveable { mutableStateOf(0) }
                                LaunchedEffect(categories.size) { categoryPage = validPage(categoryPage, categories.size) }
                                val categoryListState = androidx.compose.foundation.lazy.rememberLazyListState()
                                LaunchedEffect(categoryPage) { categoryListState.scrollToItem(0) }
                                LazyColumn(state = categoryListState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                                item { Pagination(categoryPage, categories.size) { categoryPage = it } }
                                items(pageItems(categories, categoryPage), key = { it.id }) { category ->
                                    Surface(onClick = { categoryFilter = category.id; priorityFilter = null }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            CategoryIcon(category)
                                            Text(categoryName(category), Modifier.weight(1f).padding(horizontal = 16.dp))
                                            Text(java.text.NumberFormat.getIntegerInstance().format(tasks.count { it.categoryId == category.id && !it.done }), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            IconButton(onClick = { managedCategoryId = category.id; sheet = "category_actions" }) {
                                                Icon(Icons.Outlined.MoreHoriz, stringResource(R.string.category_options, categoryName(category)))
                                            }
                                        }
                                    }
                                }
                            }
                            }
                            tab == 2 && settingsPage == "archived" -> {
                                TaskList(
                                    tasks = tasks.filter { it.done }, categories = categories, busy = busy, error = operationError,
                                    archived = true, emptyLabel = R.string.empty_archived,
                                    open = { selected = it }, changeStatus = vm::reopenArchived, delete = vm::deleteArchived,
                                )
                            }
                            tab == 2 && settingsPage.startsWith("learning") -> LearningScreen(
                                page = settingsPage, categories = categories, training = training,
                                automatic = automaticClassification, busy = busy, error = operationError, analysis = analysis,
                                onPage = { settingsPage = it }, onTask = { selected = it },
                                onToggle = vm::automaticClassification, onAnalyze = vm::analyze, onClearAnalysis = vm::clearAnalysis,
                                onReset = vm::resetLearning,
                            )
                            else -> SettingsContent(
                                page = settingsPage,
                                theme = theme,
                                busy = busy,
                                onPage = { settingsPage = it },
                                onSheet = { sheet = it },
                                onExport = { export.launch("afazeres-${LocalDate.now()}.json") },
                                onImport = { restore.launch(arrayOf("application/json", "text/plain")) },
                            )
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
    CompositionLocalProvider(LocalOperationError provides operationError) {
    if (sheet == "filters") FiltersSheet(priorityFilter, categoryFilter, categories, { sheet = null }) { priority, category ->
        priorityFilter = priority
        categoryFilter = category
        sheet = null
    }
    if (sheet == "category") CategoryEditor(null, "", busy, { sheet = null }) { vm.save(it) { sheet = null } }
    if (sheet == "category_edit" && managedCategory != null) CategoryEditor(managedCategory, categoryName(managedCategory), busy, { sheet = null }) { vm.save(it) { sheet = null } }
    if (sheet == "category_actions" && managedCategory != null) AppSheet(R.string.category_actions, { sheet = null }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CategoryIcon(managedCategory)
            Text(categoryName(managedCategory), style = MaterialTheme.typography.titleLarge)
        }
        SettingsRow(Icons.Outlined.ListAlt, R.string.view_category_tasks, "") { categoryFilter = managedCategory.id; priorityFilter = null; tab = 1; sheet = null }
        SettingsRow(Icons.Outlined.Edit, R.string.edit_category, "") { sheet = "category_edit" }
        TextButton(onClick = { sheet = "category_delete" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.delete_category), color = MaterialTheme.colorScheme.error) }
    }
    if (sheet == "category_delete" && managedCategory != null) AppSheet(R.string.delete_category, { if (!busy) sheet = null }) {
        val inUse = tasks.any { it.categoryId == managedCategory.id }
        Text(stringResource(if (inUse) R.string.category_in_use else R.string.delete_category_confirmation, categoryName(managedCategory)))
        if (!inUse) Button(
            onClick = { vm.delete(managedCategory) { if (categoryFilter == managedCategory.id) categoryFilter = null; managedCategoryId = null; sheet = null } },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.delete_category)) }
        TextButton(onClick = { sheet = null }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
    }
    if (sheet == "theme") AppSheet(R.string.theme, { sheet = null }) {
        listOf("system" to R.string.system, "light" to R.string.light, "dark" to R.string.dark).forEach { (value, label) ->
            SheetChoice(stringResource(label), theme == value, when (value) { "dark" -> Icons.Outlined.DarkMode; "light" -> Icons.Outlined.LightMode; else -> Icons.Outlined.SettingsBrightness }) { if (!busy) { vm.theme(value); sheet = null } }
        }
    }
    if (sheet == "language") AppSheet(R.string.language, { sheet = null }) {
        listOf("" to R.string.system, "pt" to R.string.portuguese, "en" to R.string.english).forEach { (tag, label) ->
            SheetChoice(stringResource(label), AppCompatDelegate.getApplicationLocales().toLanguageTags() == tag, Icons.Outlined.Language) { sheet = null; AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }
        }
    }
    if (sheet == "delete" && task != null) AppSheet(R.string.delete_task, { if (!busy) sheet = null }) {
        Text(stringResource(R.string.delete_confirmation))
        Button(onClick = { vm.delete(task) { selected = null; sheet = null } }, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.delete_task)) }
        Text(stringResource(R.string.delete_learning_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { vm.delete(task, true) { selected = null; sheet = null } }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.delete_and_forget)) }
        TextButton(onClick = { sheet = null }, enabled = !busy) { Text(stringResource(R.string.cancel)) }
    }
    restoreUri?.let { uri -> AppSheet(R.string.import_data, { if (!busy) restoreUri = null }) {
        Text(stringResource(R.string.restore_confirmation))
        Button(onClick = { vm.restore(uri.toUri()) { restoreUri = null; selected = null; categoryFilter = null } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.replace_data)) }
        TextButton(onClick = { restoreUri = null }, enabled = !busy) { Text(stringResource(R.string.cancel)) }
    } }
    }
}

private val priorityLabels = listOf(R.string.no_priority, R.string.low, R.string.medium, R.string.high)
private fun priorityLabel(priority: Int) = priorityLabels[priority + 1]
@Composable private fun priorityColor(priority: Int): Color = when (priority) { -1 -> MaterialTheme.colorScheme.onSurfaceVariant; 2 -> MaterialTheme.colorScheme.error; 1 -> if (MaterialTheme.colorScheme.background.red < .5f) Color(0xFFF2B54C) else Color(0xFF956000); else -> if (MaterialTheme.colorScheme.background.red < .5f) Color(0xFF62ADFF) else Color(0xFF1267B0) }
private fun priorityIcon(priority: Int) = when (priority) { -1 -> Icons.Outlined.Remove; 2 -> Icons.Outlined.ArrowUpward; 1 -> Icons.Outlined.Remove; else -> Icons.Outlined.ArrowDownward }
@Composable internal fun categoryName(category: Category): String = if (!category.builtIn) category.name else stringResource(when (category.id) { "work" -> R.string.work; "study" -> R.string.study; "personal" -> R.string.personal; "health" -> R.string.health; "reading" -> R.string.reading; "finance" -> R.string.finance; else -> R.string.others })
@Composable internal fun CategoryIcon(category: Category) {
    val color = Color(category.color)
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = .14f)) { Icon(categoryIcon(category), null, Modifier.padding(12.dp), tint = categoryContentColor(category)) }
}
@Composable private fun CategoryBadge(category: Category) {
    val color = Color(category.color)
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = .14f)) {
        Text(categoryName(category), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = categoryContentColor(category))
    }
}
@Composable internal fun TaskCard(task: Task, category: Category?, enabled: Boolean = true, onLongClick: () -> Unit, onClick: () -> Unit) {
    Surface(modifier = Modifier.combinedClickable(enabled = enabled, onClick = onClick, onLongClickLabel = stringResource(R.string.select_tasks), onLongClick = onLongClick), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.padding(top = 6.dp).size(11.dp).background(category?.let { Color(it.color) } ?: priorityColor(task.priority), CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { category?.let { CategoryBadge(it) } }
                    Text(stringResource(priorityLabel(task.priority)), color = priorityColor(task.priority), style = MaterialTheme.typography.labelMedium)
                    Icon(priorityIcon(task.priority), null, Modifier.size(16.dp), tint = priorityColor(task.priority))
                }
            }
        }
    }
}
@Composable private fun Detail(task: Task, categories: List<Category>, back: () -> Unit, edit: () -> Unit, complete: () -> Unit, delete: () -> Unit, busy: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
            IconButton(onClick = edit, enabled = !busy) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit_task)) }
        }
        Surface(shape = CircleShape, color = priorityColor(task.priority).copy(alpha = .13f)) { Icon(priorityIcon(task.priority), null, Modifier.padding(20.dp).size(32.dp), tint = priorityColor(task.priority)) }
        Text(stringResource(priorityLabel(task.priority)), color = priorityColor(task.priority))
        if (task.priority >= 0 && !task.priorityConfirmed) Text(stringResource(R.string.priority_automatic), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(task.title, style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(if (task.done) R.string.task_completed else R.string.task_pending), color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
        Text(stringResource(R.string.category), color = MaterialTheme.colorScheme.onSurfaceVariant)
        categories.find { it.id == task.categoryId }?.let { CategoryBadge(it) } ?: Text(stringResource(R.string.no_category))
        if (task.categoryId != null && !task.categoryConfirmed) Text(stringResource(R.string.category_automatic), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
        Text(stringResource(R.string.note_label), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(task.note.ifBlank { stringResource(R.string.no_note) })
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
        Button(onClick = complete, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Icon(Icons.Outlined.Check, null); Spacer(Modifier.width(12.dp)); Text(stringResource(if (task.done) R.string.reopen else R.string.mark_done)) }
        TextButton(onClick = delete, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.delete_task), color = MaterialTheme.colorScheme.error) }
    }
}
@Composable internal fun SettingsRow(icon: ImageVector, title: Int, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null)
            Column(Modifier.weight(1f)) { Text(stringResource(title)); if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Outlined.ChevronRight, null)
        }
    }
}
@Composable private fun EmptyState(label: Int) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Outlined.TaskAlt, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AppSheet(title: Int, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val focus = LocalFocusManager.current
    val scrollState = rememberScrollState()
    var scrollTitle by rememberSaveable { mutableIntStateOf(title) }
    LaunchedEffect(title) {
        if (scrollTitle != title) {
            scrollState.scrollTo(0)
            scrollTitle = title
        }
    }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 640.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.safeDrawing },
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 8.dp).size(36.dp, 4.dp).background(MaterialTheme.colorScheme.outline, CircleShape)) },
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().pointerInput(focus) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation()
                    if (up != null && !up.isConsumed) focus.clearFocus()
                }
            }.verticalScroll(scrollState).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.cancel), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            LocalOperationError.current?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Text(stringResource(it), Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            content()
        }
    }
}

@Composable private fun SheetChoice(label: String, selected: Boolean, icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.semantics { this.selected = selected; role = Role.RadioButton },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = tint)
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable internal fun SheetField(value: String, onChange: (String) -> Unit, label: Int, error: Boolean = false, lines: Int = 1) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        shape = MaterialTheme.shapes.medium,
        minLines = lines,
        isError = error,
        supportingText = if (error) { { Text(stringResource(R.string.required)) } } else null,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.background,
            focusedContainerColor = MaterialTheme.colorScheme.background,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = .5f),
        ),
    )
}

@Composable
private fun TaskEditor(task: Task, categories: List<Category>, busy: Boolean, error: Int?, dismiss: () -> Unit, save: (Task, Boolean, Boolean) -> Unit) {
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var note by rememberSaveable(task.id) { mutableStateOf(task.note) }
    var category by rememberSaveable(task.id) { mutableStateOf(task.categoryId) }
    var priority by rememberSaveable(task.id) { mutableIntStateOf(task.priority) }
    var categoryTouched by rememberSaveable(task.id) { mutableStateOf(false) }
    var priorityTouched by rememberSaveable(task.id) { mutableStateOf(false) }
    var categoryConfirmed by rememberSaveable(task.id) { mutableStateOf(task.categoryConfirmed) }
    var priorityConfirmed by rememberSaveable(task.id) { mutableStateOf(task.priorityConfirmed) }
    var done by rememberSaveable(task.id) { mutableStateOf(task.done) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    Column(
        Modifier.fillMaxSize().imePadding().pointerInput(focus) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val up = waitForUpOrCancellation()
                if (up != null && !up.isConsumed) focus.clearFocus()
            }
        }.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = dismiss, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.edit_task), style = MaterialTheme.typography.headlineSmall)
        }
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        if ((category != null && !categoryConfirmed) || (priority >= 0 && !priorityConfirmed)) {
            Text(stringResource(R.string.automatic_edit_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SheetField(title, { if (it.length <= 200) title = it }, R.string.task_title, attempted && title.isBlank())
        SheetField(note, { if (it.length <= 4000) note = it }, R.string.note_optional, lines = 3)
        SettingsRow(Icons.Outlined.CheckCircle, R.string.task_status, stringResource(if (done) R.string.task_completed else R.string.task_pending), !busy) { focus.clearFocus(); picker = "status" }
        SettingsRow(Icons.Outlined.GridView, R.string.category_optional, categories.find { it.id == category }?.let { categoryName(it) } ?: stringResource(R.string.no_category), !busy) { focus.clearFocus(); picker = "category" }
        SettingsRow(priorityIcon(priority), R.string.priority, stringResource(priorityLabel(priority)), !busy) { focus.clearFocus(); picker = "priority" }
        Button(
            onClick = {
                attempted = true
                if (title.isNotBlank()) { focus.clearFocus(); save(task.copy(title = title, note = note, categoryId = category, priority = priority, done = done, categoryConfirmed = categoryConfirmed, priorityConfirmed = priorityConfirmed), categoryTouched, priorityTouched) }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Icon(Icons.Outlined.Check, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.save))
        }
    }
    picker?.let { page ->
        AppSheet(when (page) { "status" -> R.string.task_status; "category" -> R.string.category_optional; else -> R.string.priority }, { picker = null }) {
            when (page) {
                "status" -> listOf(false, true).forEach { value ->
                    SheetChoice(stringResource(if (value) R.string.task_completed else R.string.task_pending), done == value, if (value) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked) { done = value; picker = null }
                }
                "category" -> {
                    SheetChoice(stringResource(R.string.no_category), category == null, Icons.Outlined.Remove) { category = null; categoryConfirmed = true; categoryTouched = true; picker = null }
                    categories.forEach { item ->
                        SheetChoice(categoryName(item), category == item.id, categoryIcon(item), categoryContentColor(item)) { category = item.id; categoryConfirmed = true; categoryTouched = true; picker = null }
                    }
                }
                else -> listOf(2, 1, 0, -1).forEach { value ->
                    SheetChoice(stringResource(priorityLabel(value)), priority == value, priorityIcon(value), priorityColor(value)) { priority = value; priorityConfirmed = true; priorityTouched = true; picker = null }
                }
            }
        }
    }
}

@Composable private fun FiltersSheet(
    initialPriority: Int?,
    initialCategory: String?,
    categories: List<Category>,
    dismiss: () -> Unit,
    apply: (Int?, String?) -> Unit,
) {
    var priority by rememberSaveable { mutableStateOf(initialPriority) }
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    var page by rememberSaveable { mutableStateOf("main") }
    AppSheet(when (page) { "priority" -> R.string.filter_priority; "category" -> R.string.filter_category; else -> R.string.filters }, dismiss) {
        if (page != "main") TextButton(onClick = { page = "main" }) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.back))
        }
        when (page) {
            "main" -> {
                SettingsRow(Icons.Outlined.Flag, R.string.filter_priority, stringResource(priority?.let(::priorityLabel) ?: R.string.all_priorities)) { page = "priority" }
                SettingsRow(Icons.Outlined.GridView, R.string.category, category?.let { id -> categories.find { it.id == id }?.let { categoryName(it) } } ?: stringResource(R.string.all_categories)) { page = "category" }
                Button(onClick = { apply(priority, category) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.apply_filters)) }
                TextButton(onClick = { priority = null; category = null }, enabled = priority != null || category != null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.clear_filters)) }
            }
            "priority" -> {
                SheetChoice(stringResource(R.string.all_priorities), priority == null, Icons.Outlined.FilterList) { priority = null; page = "main" }
                listOf(2, 1, 0, -1).forEach { value ->
                    SheetChoice(stringResource(priorityLabel(value)), priority == value, priorityIcon(value), priorityColor(value)) { priority = value; page = "main" }
                }
            }
            "category" -> {
                SheetChoice(stringResource(R.string.all_categories), category == null, Icons.Outlined.GridView) { category = null; page = "main" }
                categories.forEach { item ->
                    SheetChoice(categoryName(item), category == item.id, categoryIcon(item), categoryContentColor(item)) { category = item.id; page = "main" }
                }
            }
        }
    }
}
