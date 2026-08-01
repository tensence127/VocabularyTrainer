package com.example.cardapp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cardapp.data.Word
import com.example.cardapp.ui.AppViewModel
import com.example.cardapp.ui.components.ConfirmDialog
import com.example.cardapp.ui.components.LiquidGlass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WordsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val words by vm.words.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingWord by remember { mutableStateOf<Word?>(null) }
    var deletingWord by remember { mutableStateOf<Word?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    val visibleWords = remember(words, query) {
        val q = query.trim()
        val sorted = words.sortedByDescending { it.createdAt }
        if (q.isEmpty()) sorted
        else sorted.filter { word ->
            word.term.contains(q, ignoreCase = true) ||
                word.translations.any { it.contains(q, ignoreCase = true) }
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Заголовок «Мои слова — N» вынесен в верхнюю панель (App.kt).
            if (words.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Пока пусто — добавь первое слово кнопкой «+»",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Поиск") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Очистить поиск")
                            }
                        }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
                if (visibleWords.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Ничего не найдено",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 280.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(items = visibleWords, key = { it.id }) { word ->
                            WordCard(
                                word = word,
                                onEdit = { editingWord = word },
                                onDeleteRequest = { deletingWord = word },
                            )
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Добавить слово")
        }
    }

    if (showAddDialog) {
        WordDialog(
            onConfirm = { term, translations, description ->
                vm.addWord(term, translations, description)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
    editingWord?.let { word ->
        WordDialog(
            initial = word,
            onConfirm = { term, translations, description ->
                vm.editWord(word.id, term, translations, description)
                editingWord = null
            },
            onDismiss = { editingWord = null },
        )
    }
    deletingWord?.let { word ->
        ConfirmDialog(
            title = "Удалить слово?",
            message = "«${word.term}» будет удалено из словаря.",
            onConfirm = {
                vm.deleteWord(word.id)
                deletingWord = null
            },
            onDismiss = { deletingWord = null },
        )
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WordCard(word: Word, onEdit: () -> Unit, onDeleteRequest: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val dueLabel =
        if (word.nextReviewAt <= now) "к повторению"
        else "повтор " + SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(word.nextReviewAt))

    LiquidGlass(shape = RoundedCornerShape(16.dp)) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onEdit,
                        onLongClick = { menuExpanded = true },
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = word.term,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = word.translations.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (word.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = word.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = dueLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Редактировать") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Удалить") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onDeleteRequest()
                    },
                )
            }
        }
    }
}

/** Диалог создания (initial == null) или редактирования слова. */
@Composable
private fun WordDialog(
    onConfirm: (term: String, translations: List<String>, description: String) -> Unit,
    onDismiss: () -> Unit,
    initial: Word? = null,
) {
    var term by rememberSaveable { mutableStateOf(initial?.term ?: "") }
    val translations: SnapshotStateList<String> = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) {
        (initial?.translations?.takeIf { it.isNotEmpty() } ?: listOf("")).toMutableStateList()
    }
    var description by rememberSaveable { mutableStateOf(initial?.description ?: "") }
    var showDescription by rememberSaveable { mutableStateOf(!initial?.description.isNullOrBlank()) }
    var deletingTranslation by remember { mutableStateOf<Int?>(null) }
    val canConfirm = term.isNotBlank() && translations.any { it.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новое слово" else "Редактирование") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = term,
                    onValueChange = { term = it },
                    label = { Text("Слово (англ.)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                translations.forEachIndexed { index, value ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { translations[index] = it },
                        label = {
                            Text(if (index == 0) "Перевод" else "Перевод ${index + 1}")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = if (translations.size > 1) {
                            {
                                IconButton(onClick = {
                                    // Пустое поле убираем сразу, заполненное — с подтверждением.
                                    if (translations[index].isBlank()) translations.removeAt(index)
                                    else deletingTranslation = index
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Убрать перевод",
                                    )
                                }
                            }
                        } else null,
                    )
                }
                TextButton(onClick = { translations.add("") }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Ещё перевод")
                }
                // Описание — одно на слово, ниже всех переводов, многострочное.
                if (showDescription) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Описание") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    TextButton(onClick = { showDescription = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Добавить описание")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(term, translations.toList(), description) },
                enabled = canConfirm,
            ) {
                Text(if (initial == null) "Добавить" else "Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )

    deletingTranslation?.let { index ->
        ConfirmDialog(
            title = "Убрать перевод?",
            message = "«${translations.getOrNull(index).orEmpty()}» будет убран из списка переводов.",
            onConfirm = {
                if (index < translations.size) translations.removeAt(index)
                deletingTranslation = null
            },
            onDismiss = { deletingTranslation = null },
        )
    }
}
