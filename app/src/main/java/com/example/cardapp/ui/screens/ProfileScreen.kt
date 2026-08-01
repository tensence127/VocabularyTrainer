package com.example.cardapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.cardapp.ui.AppViewModel
import com.example.cardapp.ui.components.Avatar
import com.example.cardapp.ui.components.AvatarCropper
import com.example.cardapp.ui.components.AvatarViewer
import com.example.cardapp.ui.components.DayNightIndicator
import com.example.cardapp.ui.components.LiquidGlass
import com.example.cardapp.ui.components.WideStatCard
import com.example.cardapp.ui.plural

/**
 * Профиль: аватарка (тап — открыть на весь экран, круглый карандашик —
 * сменить фото), ник, почта и статистика за всё время.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenFriends: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile by vm.profile.collectAsState()
    val words by vm.words.collectAsState()
    val events by vm.events.collectAsState()
    val friends by vm.friends.collectAsState()
    val user by vm.user.collectAsState()
    val profileMessage by vm.profileMessage.collectAsState()
    val avatarDraft by vm.avatarDraft.collectAsState()

    var showNickDialog by remember { mutableStateOf(false) }
    var showAvatarViewer by remember { mutableStateOf(false) }

    val pickAvatar = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let(vm::startAvatarEdit)
    }
    fun launchAvatarPicker() {
        pickAvatar.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                title = { Text("Профиль") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = { DayNightIndicator() },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box {
                Avatar(
                    base64 = profile.avatarBase64,
                    size = 120.dp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            if (profile.avatarBase64.isNotBlank()) showAvatarViewer = true
                            else launchAvatarPicker()
                        },
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { launchAvatarPicker() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Сменить аватарку",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = profile.nickname.ifBlank { "Без ника" },
                    style = MaterialTheme.typography.headlineSmall,
                )
                IconButton(onClick = { showNickDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Изменить ник",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = user?.email ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            profileMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(),
            ) {
                val correct = events.count { it.remembered }
                val percent =
                    if (events.isNotEmpty()) "${correct * 100 / events.size}%" else "—"
                ProfileStat(
                    value = "${words.size}",
                    label = plural(words.size, "слово в словаре", "слова в словаре", "слов в словаре"),
                    modifier = Modifier.weight(1f),
                )
                ProfileStat(
                    value = "${events.size}",
                    label = plural(events.size, "ответ за всё время", "ответа за всё время", "ответов за всё время"),
                    modifier = Modifier.weight(1f),
                )
                ProfileStat(value = percent, label = "верных ответов", modifier = Modifier.weight(1f))
            }
            WideStatCard(
                value = "${friends.size}",
                label = plural(friends.size, "друг", "друга", "друзей"),
                onClick = onOpenFriends,
                modifier = Modifier.widthIn(max = 480.dp),
            )
        }
    }

    if (showAvatarViewer) {
        AvatarViewer(
            thumbBase64 = profile.avatarBase64,
            fetchFull = { onResult -> vm.fetchAvatarFull(user?.uid.orEmpty(), onResult) },
            onDismiss = { showAvatarViewer = false },
        )
    }

    avatarDraft?.let { draft ->
        AvatarCropper(
            source = draft,
            onConfirm = vm::confirmAvatar,
            onCancel = vm::cancelAvatarEdit,
        )
    }

    if (showNickDialog) {
        var nickname by remember { mutableStateOf(profile.nickname) }
        AlertDialog(
            onDismissRequest = { showNickDialog = false },
            title = { Text("Ник") },
            text = {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Как тебя называть?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearProfileMessage()
                        vm.setNickname(nickname)
                        showNickDialog = false
                    },
                    enabled = nickname.isNotBlank(),
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNickDialog = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier = Modifier) {
    LiquidGlass(shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
