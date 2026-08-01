package com.example.cardapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardapp.ui.components.Avatar
import com.example.cardapp.ui.components.ConfirmDialog
import com.example.cardapp.ui.components.LiquidGlass
import com.example.cardapp.ui.components.LocalLiquidGlassEnabled
import com.example.cardapp.ui.components.SLIDE_FAST_MS
import com.example.cardapp.ui.components.SLIDE_SLOW_MS
import com.example.cardapp.ui.components.DayNightIndicator
import com.example.cardapp.ui.components.SlideContent
import com.example.cardapp.ui.components.SlideEaseOut
import com.example.cardapp.ui.components.glassBarColor
import com.example.cardapp.ui.screens.AuthScreen
import com.example.cardapp.ui.screens.FriendsScreen
import com.example.cardapp.ui.screens.ProfileScreen
import com.example.cardapp.ui.screens.ReviewScreen
import com.example.cardapp.ui.screens.StatsScreen
import com.example.cardapp.ui.screens.VerifyEmailScreen
import com.example.cardapp.ui.screens.WordsScreen
import kotlinx.coroutines.launch

enum class AppDestination(val label: String) {
    Review("Учить"),
    Words("Слова"),
    Stats("Статистика"),
}

private val AppDestination.icon: ImageVector
    get() = when (this) {
        AppDestination.Review -> Icons.Default.PlayArrow
        AppDestination.Words -> Icons.AutoMirrored.Filled.List
        AppDestination.Stats -> Icons.Default.Info
    }


@Composable
private fun SlidingBottomBar(
    destination: AppDestination,
    dueCount: Int,
    durationMs: Int,
    onSelect: (AppDestination) -> Unit,
) {
    val pos by animateFloatAsState(
        targetValue = destination.ordinal.toFloat(),
        animationSpec = tween(durationMillis = durationMs, easing = SlideEaseOut),
        label = "bottomNavHighlight",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(80.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val itemWidth = maxWidth / AppDestination.entries.size
            LiquidGlass(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .offset(x = itemWidth * pos)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {}
            Row(Modifier.fillMaxSize()) {
                AppDestination.entries.forEach { d ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onSelect(d) },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DestinationIcon(d, dueCount)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = d.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (d == destination) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Иконка вкладки; на «Учить» — с бейджем числа карточек к повторению. */
@Composable
private fun DestinationIcon(destination: AppDestination, dueCount: Int) {
    BadgedBox(
        badge = {
            if (destination == AppDestination.Review && dueCount > 0) {
                Badge { Text("$dueCount") }
            }
        },
    ) {
        Icon(destination.icon, contentDescription = destination.label)
    }
}

/** Экран верхнего уровня. depth задаёт направление слайд-перехода. */
private enum class TopScreen(val depth: Int) { MAIN(0), PROFILE(1), FRIENDS(2) }


@Composable
fun CardApp(vm: AppViewModel = viewModel()) {
    val liquidGlass by vm.liquidGlass.collectAsState()
    CompositionLocalProvider(LocalLiquidGlassEnabled provides liquidGlass) {
        CardAppInner(vm)
    }
}

@Composable
private fun CardAppInner(vm: AppViewModel) {
    val user by vm.user.collectAsState()
    val emailVerified by vm.emailVerified.collectAsState()
    if (user == null) {
        AuthScreen(vm)
        return
    }
    if (!emailVerified) {
        VerifyEmailScreen(vm)
        return
    }

    var showFriends by rememberSaveable { mutableStateOf(false) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    val top = when {
        showFriends -> TopScreen.FRIENDS
        showProfile -> TopScreen.PROFILE
        else -> TopScreen.MAIN
    }
    val stateHolder = rememberSaveableStateHolder()

    SlideContent(
        target = top,
        forward = { from, to -> to.depth > from.depth },
    ) { screen ->
        stateHolder.SaveableStateProvider(screen) {
            when (screen) {
                TopScreen.PROFILE -> ProfileScreen(
                    vm = vm,
                    onBack = { showProfile = false },
                    onOpenFriends = { showFriends = true },
                )
                TopScreen.FRIENDS -> FriendsScreen(vm = vm, onBack = { showFriends = false })
                TopScreen.MAIN -> MainScaffold(
                    vm = vm,
                    onOpenProfile = { showProfile = true },
                    onOpenFriends = { showFriends = true },
                )
            }
        }
    }
}

/**
 * Главный экран: выдвижное меню, верхняя панель и три вкладки внизу.
 * Переключение вкладок — «перелистывание» (медленнее открытия), жест «назад»
 * между вкладками — быстрый слайд вправо.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    vm: AppViewModel,
    onOpenProfile: () -> Unit,
    onOpenFriends: () -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.Review) }
    val backStack = rememberSaveable(
        saver = listSaver(
            save = { it.map(AppDestination::name) },
            restore = { it.map(AppDestination::valueOf).toMutableStateList() },
        ),
    ) { mutableStateListOf<AppDestination>() }
    var lastNavWasBack by remember { mutableStateOf(false) }
    val navigateTo: (AppDestination) -> Unit = { d ->
        if (d != destination) {
            backStack.add(destination)
            lastNavWasBack = false
            destination = d
        }
    }

    val words by vm.words.collectAsState()
    val friendRequests by vm.friendRequests.collectAsState()
    val liquidGlass by vm.liquidGlass.collectAsState()
    val dueCount = remember(words) {
        val now = System.currentTimeMillis()
        words.count { it.nextReviewAt <= now }
    }
    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp >= 600
    val drawerWidth = minOf((configuration.screenWidthDp * 0.55f).dp, 320.dp)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showResetConfirm by remember { mutableStateOf(false) }
    var showResetPhrase by remember { mutableStateOf(false) }
    val destHolder = rememberSaveableStateHolder()

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    BackHandler(enabled = !drawerState.isOpen && backStack.isNotEmpty()) {
        lastNavWasBack = true
        destination = backStack.removeAt(backStack.size - 1)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(drawerWidth),
                drawerContainerColor = Color.Transparent,
                drawerTonalElevation = 0.dp,
            ) {
                val profile by vm.profile.collectAsState()
                LiquidGlass(
                    shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                  Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(16.dp)) {
                    Avatar(base64 = profile.avatarBase64, size = 56.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = profile.nickname.ifBlank { "Без ника" },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                NavigationDrawerItem(
                    label = { Text("Профиль") },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenProfile()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                NavigationDrawerItem(
                    label = { Text("Друзья") },
                    icon = { Icon(Icons.Default.Face, contentDescription = null) },
                    badge = { if (friendRequests.isNotEmpty()) Text("${friendRequests.size}") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenFriends()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { vm.toggleLiquidGlass() }
                        .padding(start = 16.dp, end = 16.dp)
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Жидкое стекло",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Switch(checked = liquidGlass, onCheckedChange = null)
                }
                NavigationDrawerItem(
                    label = { Text("Сброс статистики") },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showResetConfirm = true
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                NavigationDrawerItem(
                    label = { Text("Выйти") },
                    icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        vm.signOut()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                  }
                }
            }
        },
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                    title = {
                        Text(
                            when (destination) {
                                AppDestination.Review -> "Карточки"
                                AppDestination.Words ->
                                    "Мои слова" + if (words.isNotEmpty()) " — ${words.size}" else ""
                                AppDestination.Stats -> "Статистика"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            BadgedBox(
                                badge = {
                                    if (friendRequests.isNotEmpty()) {
                                        Badge { Text("${friendRequests.size}") }
                                    }
                                },
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Меню")
                            }
                        }
                    },
                    actions = { DayNightIndicator() },
                )
            },
            bottomBar = {
                if (!isWide) {
                    SlidingBottomBar(
                        destination = destination,
                        dueCount = dueCount,
                        durationMs = if (lastNavWasBack) SLIDE_FAST_MS else SLIDE_SLOW_MS,
                        onSelect = { navigateTo(it) },
                    )
                }
            },
        ) { innerPadding ->
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (isWide) {
                    NavigationRail(containerColor = glassBarColor()) {
                        Column(
                            modifier = Modifier.fillMaxHeight(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppDestination.entries.forEach { d ->
                                NavigationRailItem(
                                    selected = destination == d,
                                    onClick = { navigateTo(d) },
                                    icon = { DestinationIcon(d, dueCount) },
                                    label = { Text(d.label) },
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
                SlideContent(
                    target = destination,
                    forward = { from, to -> to.ordinal > from.ordinal },
                    duration = { _, _ -> if (lastNavWasBack) SLIDE_FAST_MS else SLIDE_SLOW_MS },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) { dest ->
                    destHolder.SaveableStateProvider(dest) {
                        when (dest) {
                            AppDestination.Review -> ReviewScreen(
                                vm = vm,
                                onGoToWords = { navigateTo(AppDestination.Words) },
                                modifier = Modifier.fillMaxSize(),
                            )
                            AppDestination.Words -> WordsScreen(vm, Modifier.fillMaxSize())
                            AppDestination.Stats -> StatsScreen(vm, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        ConfirmDialog(
            title = "Сбросить статистику?",
            message = "Вся история ответов будет удалена, а все слова вернутся " +
                "в сегодняшнее повторение. Сами слова и переводы останутся.",
            onConfirm = {
                showResetConfirm = false
                showResetPhrase = true
            },
            onDismiss = { showResetConfirm = false },
        )
    }
    if (showResetPhrase) {
        ResetPhraseDialog(
            onConfirm = {
                vm.resetStatistics()
                showResetPhrase = false
            },
            onDismiss = { showResetPhrase = false },
        )
    }
}

private const val RESET_PHRASE = "сбросить статистику"

/** Второй этап сброса: нужно вручную ввести подтверждающую фразу. */
@Composable
private fun ResetPhraseDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var phrase by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подтверди сброс") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Чтобы не потерять статистику случайно, введи «$RESET_PHRASE».")
                OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onDismiss) { Text("Отмена") }
                TextButton(
                    onClick = onConfirm,
                    enabled = phrase.trim().lowercase() == RESET_PHRASE,
                ) {
                    Text("Подтвердить")
                }
            }
        },
    )
}
