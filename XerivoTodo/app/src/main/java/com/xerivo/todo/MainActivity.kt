package com.xerivo.todo

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xerivo.todo.ui.theme.XerivoTodoTheme
import java.util.TimeZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XerivoTodoTheme(dynamicColor = false) {
                TodoCommandCenter()
            }
        }
    }
}

@Composable
private fun TodoCommandCenter() {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var tasks by remember { mutableStateOf<List<TodoTask>>(emptyList()) }
    var categories by remember { mutableStateOf(defaultCategories()) }
    var nextTaskId by remember { mutableStateOf(1) }
    var nextCategoryId by remember { mutableStateOf(1) }
    var hasLoaded by remember { mutableStateOf(false) }

    var titleDraft by rememberSaveable { mutableStateOf("") }
    var detailsDraft by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedPriorityName by rememberSaveable { mutableStateOf(TaskPriority.Medium.name) }
    var selectedRepeatName by rememberSaveable { mutableStateOf(RepeatRule.None.name) }
    var selectedDueName by rememberSaveable { mutableStateOf(DueOption.Today.name) }
    var selectedViewName by rememberSaveable { mutableStateOf(TaskView.All.name) }
    var listOnlyMode by rememberSaveable { mutableStateOf(false) }
    var editingTaskId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showCategoryManager by rememberSaveable { mutableStateOf(false) }

    val selectedPriority = enumValueOrDefault(selectedPriorityName, TaskPriority.Medium)
    val selectedRepeat = enumValueOrDefault(selectedRepeatName, RepeatRule.None)
    val selectedDue = enumValueOrDefault(selectedDueName, DueOption.Today)
    val selectedView = enumValueOrDefault(selectedViewName, TaskView.All)
    val selectedSort = SortMode.DueSoon
    val today = epochDayNow()

    LaunchedEffect(context) {
        val loaded = TodoStorage.load(context)
        if (loaded == null) {
            val seededCategories = defaultCategories()
            val seededTasks = sampleTasks()
            categories = seededCategories
            tasks = seededTasks
            nextTaskId = (seededTasks.maxOfOrNull { it.id } ?: 0) + 1
            nextCategoryId = (seededCategories.maxOfOrNull { it.id } ?: 0) + 1
        } else {
            val loadedCategories = if (loaded.categories.isEmpty()) defaultCategories() else loaded.categories
            categories = loadedCategories
            tasks = loaded.tasks
            nextTaskId = max(loaded.nextTaskId, (loaded.tasks.maxOfOrNull { it.id } ?: 0) + 1)
            nextCategoryId = max(
                loaded.nextCategoryId,
                (loadedCategories.maxOfOrNull { it.id } ?: 0) + 1
            )
        }
        hasLoaded = true
    }

    LaunchedEffect(tasks, categories, nextTaskId, nextCategoryId, hasLoaded) {
        if (hasLoaded) {
            TodoStorage.save(
                context = context,
                state = PersistedState(
                    tasks = tasks,
                    categories = categories,
                    nextTaskId = nextTaskId,
                    nextCategoryId = nextCategoryId
                )
            )
        }
    }
    val nonArchived = tasks.filterNot { it.archived }
    val completedCount = nonArchived.count { it.completed }
    val activeCount = nonArchived.count { !it.completed }
    val overdueCount = nonArchived.count { isTaskOverdue(it, today) }
    val completionRatio = if (nonArchived.isEmpty()) 0f else completedCount.toFloat() / nonArchived.size
    val animatedCompletionRatio by animateFloatAsState(
        targetValue = completionRatio,
        label = "completionRatio"
    )

    val visibleTasks = sortTasks(
        tasks = tasks.filter { task ->
            val matchesView = when (selectedView) {
                TaskView.All -> !task.archived
                TaskView.Active -> !task.archived && !task.completed
                TaskView.Completed -> !task.archived && task.completed
                TaskView.Today -> !task.archived && !task.completed && task.dueEpochDay == today
                TaskView.Upcoming -> {
                    !task.archived && !task.completed &&
                        task.dueEpochDay != null && task.dueEpochDay > today
                }
                TaskView.Overdue -> isTaskOverdue(task, today)
                TaskView.Archived -> task.archived
            }
            matchesView
        },
        sortMode = selectedSort
    )

    fun showUndo(message: String, onUndo: () -> Unit) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
                withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed) {
                onUndo()
            }
        }
    }

    fun toggleTaskCompletion(task: TodoTask) {
        val wasCompleted = task.completed
        val nowCompleted = !wasCompleted
        tasks = tasks.map { current ->
            if (current.id == task.id) current.copy(completed = nowCompleted) else current
        }

        if (!wasCompleted && nowCompleted && !task.archived && task.repeat != RepeatRule.None) {
            val baseDue = task.dueEpochDay ?: today
            val nextTask = task.copy(
                id = nextTaskId,
                completed = false,
                dueEpochDay = baseDue + task.repeat.intervalDays,
                createdAt = System.currentTimeMillis()
            )
            nextTaskId += 1
            tasks = tasks + nextTask
            scope.launch {
                snackbarHostState.showSnackbar("Created next ${task.repeat.label.lowercase()} task")
            }
        }
    }

    fun addTaskFromQuickComposer() {
        val cleanTitle = titleDraft.trim()
        if (cleanTitle.isEmpty()) return
        val newTask = TodoTask(
            id = nextTaskId,
            title = cleanTitle,
            details = detailsDraft.trim(),
            dueEpochDay = selectedDue.resolve(today),
            categoryId = selectedCategoryId,
            priority = selectedPriority,
            completed = false,
            archived = false,
            repeat = selectedRepeat,
            createdAt = System.currentTimeMillis()
        )
        nextTaskId += 1
        tasks = listOf(newTask) + tasks
        selectedViewName = TaskView.All.name
        titleDraft = ""
        detailsDraft = ""
        scope.launch {
            listState.animateScrollToItem(2)
            snackbarHostState.showSnackbar("Task added")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    DashboardHeader(
                        totalCount = nonArchived.size,
                        activeCount = activeCount,
                        doneCount = completedCount,
                        overdueCount = overdueCount,
                        completionRatio = animatedCompletionRatio,
                        selectedView = selectedView,
                        listOnlyMode = listOnlyMode,
                        onShowComposer = {
                            listOnlyMode = false
                            scope.launch {
                                listState.animateScrollToItem(1)
                            }
                        },
                        onStatSelected = {
                            listOnlyMode = true
                            selectedViewName = it.name
                            scope.launch {
                                listState.animateScrollToItem(1)
                            }
                        }
                    )
                }

                if (!listOnlyMode) {
                    item {
                        QuickAddCard(
                            titleDraft = titleDraft,
                            detailsDraft = detailsDraft,
                            selectedDue = selectedDue,
                            selectedPriority = selectedPriority,
                            selectedRepeat = selectedRepeat,
                            selectedCategoryId = selectedCategoryId,
                            categories = categories,
                            onTitleChanged = { titleDraft = it },
                            onDetailsChanged = { detailsDraft = it },
                            onDueChanged = { selectedDueName = it.name },
                            onPriorityChanged = { selectedPriorityName = it.name },
                            onRepeatChanged = { selectedRepeatName = it.name },
                            onCategoryChanged = { selectedCategoryId = it },
                            onManageCategories = { showCategoryManager = true },
                            onAddTask = { addTaskFromQuickComposer() }
                        )
                    }
                }

                if (visibleTasks.isEmpty()) {
                    item {
                        EmptyStateCard(label = selectedView.label)
                    }
                } else {
                    itemsIndexed(visibleTasks, key = { _, task -> task.id }) { index, task ->
                        var itemVisible by remember(task.id) { mutableStateOf(false) }
                        LaunchedEffect(task.id) {
                            delay((index * 35L).coerceAtMost(210L))
                            itemVisible = true
                        }
                        AnimatedVisibility(
                            visible = itemVisible,
                            enter = fadeIn(animationSpec = tween(260)) + expandVertically(
                                animationSpec = tween(260)
                            )
                        ) {
                            TodoTaskCard(
                                task = task,
                                category = categories.firstOrNull { it.id == task.categoryId },
                                today = today,
                                onToggleComplete = { toggleTaskCompletion(task) },
                                onEdit = { editingTaskId = task.id },
                                onArchiveToggle = {
                                    val previous = task
                                    tasks = tasks.map { current ->
                                        if (current.id == task.id) {
                                            current.copy(archived = !current.archived)
                                        } else {
                                            current
                                        }
                                    }
                                    showUndo(
                                        message = if (previous.archived) {
                                            "Task restored"
                                        } else {
                                            "Task archived"
                                        }
                                    ) {
                                        tasks = tasks.map { current ->
                                            if (current.id == previous.id) previous else current
                                        }
                                    }
                                },
                                onDelete = {
                                    val removed = task
                                    tasks = tasks.filterNot { it.id == task.id }
                                    showUndo("Task deleted") {
                                        tasks = listOf(removed) + tasks
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    val taskToEdit = tasks.firstOrNull { it.id == editingTaskId }
    if (taskToEdit != null) {
        EditTaskDialog(
            task = taskToEdit,
            categories = categories,
            onDismiss = { editingTaskId = null },
            onSave = { updated ->
                tasks = tasks.map { if (it.id == updated.id) updated else it }
                editingTaskId = null
            }
        )
    }

    if (showCategoryManager) {
        CategoryManagerDialog(
            categories = categories,
            onDismiss = { showCategoryManager = false },
            onAddCategory = { name ->
                val cleanName = name.trim()
                if (cleanName.isEmpty()) return@CategoryManagerDialog
                if (categories.any { it.name.equals(cleanName, ignoreCase = true) }) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Category already exists")
                    }
                    return@CategoryManagerDialog
                }
                val color = CATEGORY_PALETTE[(nextCategoryId - 1) % CATEGORY_PALETTE.size]
                categories = categories + TaskCategory(
                    id = nextCategoryId,
                    name = cleanName,
                    colorHex = color
                )
                nextCategoryId += 1
            },
            onDeleteCategory = { category ->
                categories = categories.filterNot { it.id == category.id }
                tasks = tasks.map { task ->
                    if (task.categoryId == category.id) task.copy(categoryId = null) else task
                }
                if (selectedCategoryId == category.id) {
                    selectedCategoryId = null
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TodoPreview() {
    XerivoTodoTheme(dynamicColor = false) {
        TodoCommandCenter()
    }
}

@Composable
private fun DashboardHeader(
    totalCount: Int,
    activeCount: Int,
    doneCount: Int,
    overdueCount: Int,
    completionRatio: Float,
    selectedView: TaskView,
    listOnlyMode: Boolean,
    onShowComposer: () -> Unit,
    onStatSelected: (TaskView) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Xerivo To-Do",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Plan clearly, track progress, and finish with focus.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatPill(
                        label = "All",
                        value = totalCount.toString(),
                        selected = selectedView == TaskView.All,
                        onClick = { onStatSelected(TaskView.All) }
                    )
                    StatPill(
                        label = "Active",
                        value = activeCount.toString(),
                        selected = selectedView == TaskView.Active,
                        onClick = { onStatSelected(TaskView.Active) }
                    )
                    StatPill(
                        label = "Done",
                        value = doneCount.toString(),
                        selected = selectedView == TaskView.Completed,
                        onClick = { onStatSelected(TaskView.Completed) }
                    )
                    StatPill(
                        label = "Overdue",
                        value = overdueCount.toString(),
                        selected = selectedView == TaskView.Overdue,
                        onClick = { onStatSelected(TaskView.Overdue) }
                    )
                }
            }
            if (listOnlyMode) {
                Button(
                    onClick = onShowComposer,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Back To Composer")
                        Text(">")
                    }
                }
            }
            LinearProgressIndicator(
                progress = { completionRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(12.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${(completionRatio * 100).toInt()}% completed",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${(totalCount - doneCount).coerceAtLeast(0)} remaining",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun QuickAddCard(
    titleDraft: String,
    detailsDraft: String,
    selectedDue: DueOption,
    selectedPriority: TaskPriority,
    selectedRepeat: RepeatRule,
    selectedCategoryId: Int?,
    categories: List<TaskCategory>,
    onTitleChanged: (String) -> Unit,
    onDetailsChanged: (String) -> Unit,
    onDueChanged: (DueOption) -> Unit,
    onPriorityChanged: (TaskPriority) -> Unit,
    onRepeatChanged: (RepeatRule) -> Unit,
    onCategoryChanged: (Int?) -> Unit,
    onManageCategories: () -> Unit,
    onAddTask: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Quick Capture",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = titleDraft,
                onValueChange = onTitleChanged,
                singleLine = true,
                label = { Text("Task") },
                placeholder = { Text("Prepare sprint demo") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = detailsDraft,
                onValueChange = onDetailsChanged,
                singleLine = true,
                label = { Text("Notes (optional)") },
                placeholder = { Text("Include success metrics") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Due",
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DueOption.entries.forEach { option ->
                    FilterChip(
                        selected = option == selectedDue,
                        onClick = { onDueChanged(option) },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
            Text(
                text = "Priority",
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskPriority.entries.forEach { priority ->
                    AssistChip(
                        onClick = { onPriorityChanged(priority) },
                        label = { Text(priority.label) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (priority == selectedPriority) {
                                priority.tint.copy(alpha = 0.22f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            },
                            labelColor = if (priority == selectedPriority) {
                                priority.tint
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    )
                }
            }
            Text(
                text = "Repeat",
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RepeatRule.entries.forEach { repeat ->
                    FilterChip(
                        selected = repeat == selectedRepeat,
                        onClick = { onRepeatChanged(repeat) },
                        label = { Text(repeat.label) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onManageCategories) {
                    Text("Manage")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { onCategoryChanged(null) },
                    label = { Text("Uncategorized") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (selectedCategoryId == null) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        },
                        labelColor = if (selectedCategoryId == null) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                )
                categories.forEach { category ->
                    val selected = selectedCategoryId == category.id
                    AssistChip(
                        onClick = { onCategoryChanged(category.id) },
                        label = { Text(category.name) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (selected) {
                                category.tint.copy(alpha = 0.22f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            },
                            labelColor = if (selected) {
                                category.tint
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    )
                }
            }
            Button(
                onClick = onAddTask,
                enabled = titleDraft.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Add Task")
            }
        }
    }
}

@Composable
private fun TodoTaskCard(
    task: TodoTask,
    category: TaskCategory?,
    today: Long,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val overdue = isTaskOverdue(task, today)
    val containerColor by animateColorAsState(
        targetValue = when {
            task.archived -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            overdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
            task.completed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            else -> MaterialTheme.colorScheme.surface
        },
        label = "taskCardColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (task.completed) 1.dp else 5.dp
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (overdue) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Checkbox(
                    checked = task.completed,
                    onCheckedChange = { onToggleComplete() },
                    enabled = !task.archived
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (task.completed) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        }
                    )
                    if (task.details.isNotBlank()) {
                        Text(
                            text = task.details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TagPill(
                    label = category?.name ?: "Uncategorized",
                    tint = category?.tint ?: MaterialTheme.colorScheme.secondary
                )
                TagPill(label = task.priority.label, tint = task.priority.tint)
                TagPill(
                    label = dueLabel(task.dueEpochDay, today),
                    tint = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                )
                if (task.repeat != RepeatRule.None) {
                    TagPill(label = task.repeat.label, tint = MaterialTheme.colorScheme.primary)
                }
                if (task.archived) {
                    TagPill(label = "Archived", tint = MaterialTheme.colorScheme.outline)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onEdit, enabled = !task.archived) {
                    Text("Edit")
                }
                TextButton(onClick = onArchiveToggle) {
                    Text(if (task.archived) "Restore" else "Archive")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun TagPill(label: String, tint: Color) {
    Surface(
        shape = CircleShape,
        color = tint.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.28f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = tint
        )
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .widthIn(min = 84.dp)
            .heightIn(min = 42.dp),
        shape = RoundedCornerShape(12.dp),
        label = {
            Text(
                text = "$value  $label",
                style = MaterialTheme.typography.labelMedium
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
private fun EmptyStateCard(label: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No tasks for $label",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Try another filter or add a new task from Quick Add.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun EditTaskDialog(
    task: TodoTask,
    categories: List<TaskCategory>,
    onDismiss: () -> Unit,
    onSave: (TodoTask) -> Unit
) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var details by remember(task.id) { mutableStateOf(task.details) }
    var categoryId by remember(task.id) { mutableStateOf(task.categoryId) }
    var priority by remember(task.id) { mutableStateOf(task.priority) }
    var repeat by remember(task.id) { mutableStateOf(task.repeat) }
    var dueEpochDay by remember(task.id) { mutableStateOf(task.dueEpochDay) }
    val today = epochDayNow()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Task") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text("Task") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    singleLine = true,
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Due",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val dueChoices = listOf(
                        "No Date" to null,
                        "Today" to today,
                        "Tomorrow" to today + 1,
                        "+7d" to today + 7
                    )
                    dueChoices.forEach { choice ->
                        FilterChip(
                            selected = dueEpochDay == choice.second,
                            onClick = { dueEpochDay = choice.second },
                            label = { Text(choice.first) }
                        )
                    }
                }
                Text(
                    text = "Priority",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TaskPriority.entries.forEach { candidate ->
                        FilterChip(
                            selected = candidate == priority,
                            onClick = { priority = candidate },
                            label = { Text(candidate.label) }
                        )
                    }
                }
                Text(
                    text = "Repeat",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RepeatRule.entries.forEach { candidate ->
                        FilterChip(
                            selected = candidate == repeat,
                            onClick = { repeat = candidate },
                            label = { Text(candidate.label) }
                        )
                    }
                }
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = categoryId == null,
                        onClick = { categoryId = null },
                        label = { Text("Uncategorized") }
                    )
                    categories.forEach { category ->
                        FilterChip(
                            selected = categoryId == category.id,
                            onClick = { categoryId = category.id },
                            label = { Text(category.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanTitle = title.trim()
                    if (cleanTitle.isNotEmpty()) {
                        onSave(
                            task.copy(
                                title = cleanTitle,
                                details = details.trim(),
                                dueEpochDay = dueEpochDay,
                                categoryId = categoryId,
                                priority = priority,
                                repeat = repeat
                            )
                        )
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CategoryManagerDialog(
    categories: List<TaskCategory>,
    onDismiss: () -> Unit,
    onAddCategory: (String) -> Unit,
    onDeleteCategory: (TaskCategory) -> Unit
) {
    var nameDraft by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Categories") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    singleLine = true,
                    label = { Text("New category") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        onAddCategory(nameDraft)
                        nameDraft = ""
                    },
                    enabled = nameDraft.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Category")
                }
                categories.forEach { category ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TagPill(label = category.name, tint = category.tint)
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { onDeleteCategory(category) }) {
                            Text("Delete")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

private data class TodoTask(
    val id: Int,
    val title: String,
    val details: String,
    val dueEpochDay: Long?,
    val categoryId: Int?,
    val priority: TaskPriority,
    val completed: Boolean,
    val archived: Boolean,
    val repeat: RepeatRule,
    val createdAt: Long
)

private data class TaskCategory(
    val id: Int,
    val name: String,
    val colorHex: Long
) {
    val tint: Color
        get() = Color(colorHex)
}

private enum class TaskPriority(val label: String, val weight: Int, val colorHex: Long) {
    High("High", 3, 0xFFE76F51),
    Medium("Medium", 2, 0xFFE9C46A),
    Low("Low", 1, 0xFF2A9D8F);

    val tint: Color
        get() = Color(colorHex)
}

private enum class RepeatRule(val label: String, val intervalDays: Long) {
    None("No Repeat", 0),
    Daily("Daily", 1),
    Weekly("Weekly", 7)
}

private enum class DueOption(val label: String, val offsetDays: Long?) {
    NoDate("No Date", null),
    Today("Today", 0),
    Tomorrow("Tomorrow", 1),
    NextWeek("+7d", 7);

    fun resolve(todayEpochDay: Long): Long? {
        return offsetDays?.let { todayEpochDay + it }
    }
}

private enum class TaskView(val label: String) {
    All("All"),
    Active("Active"),
    Completed("Completed"),
    Today("Today"),
    Upcoming("Upcoming"),
    Overdue("Overdue"),
    Archived("Archived")
}

private enum class SortMode(val label: String) {
    DueSoon("Due Soon"),
    PriorityHigh("Priority"),
    Newest("Newest")
}

private data class PersistedState(
    val tasks: List<TodoTask>,
    val categories: List<TaskCategory>,
    val nextTaskId: Int,
    val nextCategoryId: Int
)
private object TodoStorage {
    private const val PREFS_NAME = "xerivo_todo_prefs"
    private const val STATE_KEY = "state_v2"
    private const val LEGACY_TASKS_KEY = "tasks"

    fun load(context: Context): PersistedState? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawState = prefs.getString(STATE_KEY, null)
        if (rawState != null) {
            return parseV2(rawState)
        }
        val legacy = prefs.getString(LEGACY_TASKS_KEY, null) ?: return null
        return parseLegacy(legacy)
    }

    fun save(context: Context, state: PersistedState) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val root = JSONObject().apply {
            put("version", 2)
            put("nextTaskId", state.nextTaskId)
            put("nextCategoryId", state.nextCategoryId)
            put(
                "categories",
                JSONArray().apply {
                    state.categories.forEach { category ->
                        put(
                            JSONObject().apply {
                                put("id", category.id)
                                put("name", category.name)
                                put("colorHex", category.colorHex)
                            }
                        )
                    }
                }
            )
            put(
                "tasks",
                JSONArray().apply {
                    state.tasks.forEach { task ->
                        put(
                            JSONObject().apply {
                                put("id", task.id)
                                put("title", task.title)
                                put("details", task.details)
                                put("dueEpochDay", task.dueEpochDay ?: JSONObject.NULL)
                                put("categoryId", task.categoryId ?: JSONObject.NULL)
                                put("priority", task.priority.name)
                                put("completed", task.completed)
                                put("archived", task.archived)
                                put("repeat", task.repeat.name)
                                put("createdAt", task.createdAt)
                            }
                        )
                    }
                }
            )
        }
        prefs.edit().putString(STATE_KEY, root.toString()).apply()
    }

    private fun parseV2(raw: String): PersistedState? {
        return runCatching {
            val root = JSONObject(raw)
            val categoriesArray = root.optJSONArray("categories") ?: JSONArray()
            val tasksArray = root.optJSONArray("tasks") ?: JSONArray()

            val categories = buildList {
                for (index in 0 until categoriesArray.length()) {
                    val item = categoriesArray.getJSONObject(index)
                    add(
                        TaskCategory(
                            id = item.optInt("id"),
                            name = item.optString("name"),
                            colorHex = item.optLong("colorHex", CATEGORY_PALETTE[index % CATEGORY_PALETTE.size])
                        )
                    )
                }
            }

            val tasks = buildList {
                for (index in 0 until tasksArray.length()) {
                    val item = tasksArray.getJSONObject(index)
                    add(
                        TodoTask(
                            id = item.optInt("id"),
                            title = item.optString("title"),
                            details = item.optString("details"),
                            dueEpochDay = if (item.isNull("dueEpochDay")) null else item.optLong("dueEpochDay"),
                            categoryId = if (item.isNull("categoryId")) null else item.optInt("categoryId"),
                            priority = enumValueOrDefault(
                                item.optString("priority"),
                                TaskPriority.Medium
                            ),
                            completed = item.optBoolean("completed"),
                            archived = item.optBoolean("archived"),
                            repeat = enumValueOrDefault(
                                item.optString("repeat"),
                                RepeatRule.None
                            ),
                            createdAt = item.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            val safeCategories = if (categories.isEmpty()) defaultCategories() else categories
            PersistedState(
                tasks = tasks,
                categories = safeCategories,
                nextTaskId = max(
                    root.optInt("nextTaskId", 1),
                    (tasks.maxOfOrNull { it.id } ?: 0) + 1
                ),
                nextCategoryId = max(
                    root.optInt("nextCategoryId", 1),
                    (safeCategories.maxOfOrNull { it.id } ?: 0) + 1
                )
            )
        }.getOrNull()
    }
    private fun parseLegacy(raw: String): PersistedState? {
        return runCatching {
            val array = JSONArray(raw)
            val categories = defaultCategories()
            val today = epochDayNow()
            val tasks = buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val dueLabel = item.optString("dueLabel")
                    val dueEpochDay = when {
                        dueLabel.startsWith("Today", ignoreCase = true) -> today
                        dueLabel.startsWith("Tomorrow", ignoreCase = true) -> today + 1
                        else -> null
                    }
                    add(
                        TodoTask(
                            id = item.optInt("id", index + 1),
                            title = item.optString("title"),
                            details = item.optString("details"),
                            dueEpochDay = dueEpochDay,
                            categoryId = legacyCategoryId(item.optString("category")),
                            priority = enumValueOrDefault(
                                item.optString("priority"),
                                TaskPriority.Medium
                            ),
                            completed = item.optBoolean("completed"),
                            archived = false,
                            repeat = RepeatRule.None,
                            createdAt = System.currentTimeMillis() - ((array.length() - index) * 1000L)
                        )
                    )
                }
            }
            PersistedState(
                tasks = tasks,
                categories = categories,
                nextTaskId = (tasks.maxOfOrNull { it.id } ?: 0) + 1,
                nextCategoryId = (categories.maxOfOrNull { it.id } ?: 0) + 1
            )
        }.getOrNull()
    }

    private fun legacyCategoryId(raw: String): Int? {
        return when (raw) {
            "DeepWork", "Deep Work" -> 1
            "Design" -> 2
            "Health" -> 3
            "Planning" -> 4
            else -> null
        }
    }
}

private fun defaultCategories(): List<TaskCategory> {
    return listOf(
        TaskCategory(id = 1, name = "Deep Work", colorHex = CATEGORY_PALETTE[0]),
        TaskCategory(id = 2, name = "Design", colorHex = CATEGORY_PALETTE[1]),
        TaskCategory(id = 3, name = "Health", colorHex = CATEGORY_PALETTE[2]),
        TaskCategory(id = 4, name = "Planning", colorHex = CATEGORY_PALETTE[3])
    )
}

private fun sampleTasks(): List<TodoTask> {
    val now = System.currentTimeMillis()
    val today = epochDayNow()
    return listOf(
        TodoTask(
            id = 1,
            title = "Ship onboarding motion pass",
            details = "Staggered reveal + CTA timing polish",
            dueEpochDay = today,
            categoryId = 2,
            priority = TaskPriority.High,
            completed = false,
            archived = false,
            repeat = RepeatRule.None,
            createdAt = now - 4_000L
        ),
        TodoTask(
            id = 2,
            title = "45-minute deep work sprint",
            details = "Architecture write-up and risk list",
            dueEpochDay = today,
            categoryId = 1,
            priority = TaskPriority.Medium,
            completed = false,
            archived = false,
            repeat = RepeatRule.Daily,
            createdAt = now - 3_000L
        ),
        TodoTask(
            id = 3,
            title = "Hydration + short walk",
            details = "Reset before next block",
            dueEpochDay = today - 1,
            categoryId = 3,
            priority = TaskPriority.Low,
            completed = true,
            archived = false,
            repeat = RepeatRule.None,
            createdAt = now - 2_000L
        ),
        TodoTask(
            id = 4,
            title = "Prep tomorrow top 3",
            details = "Decide non-negotiables",
            dueEpochDay = today + 1,
            categoryId = 4,
            priority = TaskPriority.Medium,
            completed = false,
            archived = false,
            repeat = RepeatRule.Weekly,
            createdAt = now - 1_000L
        )
    )
}

private fun sortTasks(tasks: List<TodoTask>, sortMode: SortMode): List<TodoTask> {
    return when (sortMode) {
        SortMode.DueSoon -> {
            tasks.sortedWith(
                compareBy<TodoTask> { it.dueEpochDay ?: Long.MAX_VALUE }
                    .thenByDescending { it.priority.weight }
                    .thenByDescending { it.createdAt }
            )
        }
        SortMode.PriorityHigh -> {
            tasks.sortedWith(
                compareByDescending<TodoTask> { it.priority.weight }
                    .thenBy { it.dueEpochDay ?: Long.MAX_VALUE }
                    .thenByDescending { it.createdAt }
            )
        }
        SortMode.Newest -> tasks.sortedByDescending { it.createdAt }
    }
}

private fun isTaskOverdue(task: TodoTask, today: Long): Boolean {
    return !task.archived && !task.completed && task.dueEpochDay != null && task.dueEpochDay < today
}

private fun dueLabel(dueEpochDay: Long?, today: Long): String {
    if (dueEpochDay == null) return "No due"
    val diff = dueEpochDay - today
    return when {
        diff == 0L -> "Today"
        diff == 1L -> "Tomorrow"
        diff > 1L -> "In ${diff}d"
        diff == -1L -> "1d overdue"
        else -> "${-diff}d overdue"
    }
}

private fun epochDayNow(): Long {
    val now = System.currentTimeMillis()
    val offset = TimeZone.getDefault().getOffset(now)
    return Math.floorDiv(now + offset, MILLIS_PER_DAY)
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T {
    return runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
}

private const val MILLIS_PER_DAY = 86_400_000L

private val CATEGORY_PALETTE = listOf(
    0xFF4F86F7,
    0xFFF06C5C,
    0xFF2A9D8F,
    0xFF9A6DFF,
    0xFFE9C46A,
    0xFF457B9D
)
