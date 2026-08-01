package com.example.cardapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.cardapp.data.FriendProfile
import com.example.cardapp.ui.AppViewModel
import com.example.cardapp.ui.components.Avatar
import com.example.cardapp.ui.components.AvatarViewer
import com.example.cardapp.ui.components.ConfirmDialog
import com.example.cardapp.ui.components.DayNightIndicator
import com.example.cardapp.ui.components.LiquidGlass
import com.example.cardapp.ui.components.SlideContent
import com.example.cardapp.ui.components.WideStatCard
import com.example.cardapp.ui.plural

/** Куда сейчас смотрит пользователь внутри раздела «Друзья». */
private sealed interface FriendDest {
    data class Profile(val friend: FriendProfile) : FriendDest
    data class FriendsOf(val uid: String, val nickname: String) : FriendDest
}

/** Снимок навигации: глубина (для направления слайда) + текущий пункт. */
private data class FriendNavState(val depth: Int, val dest: FriendDest?)

/**
 * Раздел «Друзья» с внутренней навигацией: список моих друзей → профиль →
 * его список друзей → чужой профиль → … Жест «назад» на каждом уровне
 * возвращает на предыдущий, сохраняя прокрутку (состояния прокрутки живут
 * в общей карте на всё время открытия раздела).
 */
@Composable
fun FriendsScreen(vm: AppViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val myFriends by vm.friends.collectAsState()
    val requests by vm.friendRequests.collectAsState()
    val search by vm.friendSearch.collectAsState()
    val profile by vm.profile.collectAsState()
    val user by vm.user.collectAsState()

    val stack = remember { mutableStateListOf<FriendDest>() }
    val scrolls = remember { mutableMapOf<String, ScrollState>() }
    fun scrollFor(key: String) = scrolls.getOrPut(key) { ScrollState(0) }
    var friendsFilter by remember { mutableStateOf("") }

    SlideContent(
        target = FriendNavState(stack.size, stack.lastOrNull()),
        forward = { from, to -> to.depth > from.depth },
    ) { state ->
        when (val top = state.dest) {
            null -> RootFriends(
                myFriends = myFriends,
                requests = requests,
                search = search,
                filter = friendsFilter,
                onFilterChange = { friendsFilter = it },
                hasNickname = profile.nickname.isNotBlank(),
                myUid = user?.uid,
                scroll = scrollFor("root"),
                onBack = onBack,
                onSearch = vm::searchFriend,
                onSend = vm::sendFriendRequest,
                onCheckRequest = vm::checkOutgoingRequest,
                onAccept = vm::acceptFriendRequest,
                onDecline = vm::declineFriendRequest,
                onOpenProfile = { stack.add(FriendDest.Profile(it)) },
            )

            is FriendDest.Profile -> {
                val live = myFriends.find { it.uid == top.friend.uid } ?: top.friend
                FriendProfileView(
                    friend = live,
                    isMyFriend = myFriends.any { it.uid == top.friend.uid },
                    isMe = user?.uid == top.friend.uid,
                    fetchAvatarFull = vm::fetchAvatarFull,
                    onCheckRequest = vm::checkOutgoingRequest,
                    scroll = scrollFor("profile:${top.friend.uid}"),
                    onOpenFriends = { stack.add(FriendDest.FriendsOf(top.friend.uid, live.nickname)) },
                    onSendRequest = { vm.sendFriendRequest(top.friend.uid) },
                    onRemove = {
                        vm.removeFriend(top.friend.uid)
                        stack.removeAt(stack.lastIndex)
                    },
                    onBack = { stack.removeAt(stack.lastIndex) },
                )
            }

            is FriendDest.FriendsOf -> FriendListView(
                vm = vm,
                uid = top.uid,
                ownerNickname = top.nickname,
                scroll = scrollFor("friendsof:${top.uid}"),
                onOpenProfile = { stack.add(FriendDest.Profile(it)) },
                onBack = { stack.removeAt(stack.lastIndex) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootFriends(
    myFriends: List<FriendProfile>,
    requests: List<com.example.cardapp.data.FriendRequest>,
    search: AppViewModel.FriendSearch,
    filter: String,
    onFilterChange: (String) -> Unit,
    hasNickname: Boolean,
    myUid: String?,
    scroll: ScrollState,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onSend: (String) -> Unit,
    onCheckRequest: (String, (Boolean) -> Unit) -> Unit,
    onAccept: (com.example.cardapp.data.FriendRequest) -> Unit,
    onDecline: (com.example.cardapp.data.FriendRequest) -> Unit,
    onOpenProfile: (FriendProfile) -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                title = { Text("Друзья") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = { DayNightIndicator() },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!hasNickname) {
                LiquidGlass(shape = RoundedCornerShape(16.dp)) {
                    Text(
                        text = "Сначала задай ник в профиле — по нему друзья смогут тебя найти.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                SearchCard(
                    search = search,
                    friends = myFriends,
                    myUid = myUid,
                    onSearch = onSearch,
                    onSend = onSend,
                    onCheckRequest = onCheckRequest,
                    onOpen = onOpenProfile,
                )
            }

            if (requests.isNotEmpty()) {
                LiquidGlass(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Заявки в друзья", style = MaterialTheme.typography.titleMedium)
                        requests.forEach { request ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = request.fromNickname.ifBlank { "Без ника" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { onDecline(request) }) { Text("Отклонить") }
                                Button(onClick = { onAccept(request) }) { Text("Принять") }
                            }
                        }
                    }
                }
            }

            LiquidGlass(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Друзья" + if (myFriends.isNotEmpty()) " — ${myFriends.size}" else "",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (myFriends.isEmpty()) {
                        Text(
                            text = "Пока никого нет",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        OutlinedTextField(
                            value = filter,
                            onValueChange = onFilterChange,
                            placeholder = { Text("Поиск по нику") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        )
                        val filtered = myFriends.filter {
                            it.nickname.contains(filter.trim(), ignoreCase = true)
                        }
                        if (filtered.isEmpty()) {
                            Text(
                                text = "Никого не нашлось",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        filtered.forEach { friend ->
                            FriendRow(friend) { onOpenProfile(friend) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendRow(friend: FriendProfile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(friend.avatarBase64, 40.dp)
        Text(
            text = friend.nickname.ifBlank { "Без ника" },
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SearchCard(
    search: AppViewModel.FriendSearch,
    friends: List<FriendProfile>,
    myUid: String?,
    onSearch: (String) -> Unit,
    onSend: (String) -> Unit,
    onCheckRequest: (String, (Boolean) -> Unit) -> Unit,
    onOpen: (FriendProfile) -> Unit,
) {
    LiquidGlass(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Добавить друга", style = MaterialTheme.typography.titleMedium)
            var query by rememberSaveable { mutableStateOf("") }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Ник друга") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onSearch(query) },
                    enabled = query.isNotBlank() && !search.searching,
                ) {
                    Text("Найти")
                }
            }
            when {
                search.searching -> Text(
                    text = "Ищем…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                search.notFound -> Text(
                    text = "Никого с ником «${search.query}» не нашлось",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> search.results.forEach { result ->
                    SearchResultRow(
                        result = result,
                        isMe = result.uid == myUid,
                        isFriend = friends.any { it.uid == result.uid },
                        onSend = onSend,
                        onCheckRequest = onCheckRequest,
                        onOpen = onOpen,
                    )
                }
            }
        }
    }
}

/** Строка выдачи поиска: тап открывает профиль, кнопка шлёт заявку. */
@Composable
private fun SearchResultRow(
    result: FriendProfile,
    isMe: Boolean,
    isFriend: Boolean,
    onSend: (String) -> Unit,
    onCheckRequest: (String, (Boolean) -> Unit) -> Unit,
    onOpen: (FriendProfile) -> Unit,
) {
    var requestSent by remember(result.uid) { mutableStateOf(false) }
    LaunchedEffect(result.uid) {
        if (!isMe && !isFriend) onCheckRequest(result.uid) { if (it) requestSent = true }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(result) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(result.avatarBase64, 40.dp)
        Text(
            text = result.nickname,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        when {
            isMe -> Text(
                text = "Это ты",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            isFriend -> Text(
                text = "Уже в друзьях",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            requestSent -> Text(
                text = "Заявка отправлена",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            else -> Button(onClick = {
                onSend(result.uid)
                requestSent = true
            }) {
                Text("Добавить")
            }
        }
    }
}

/** Профиль (свой друг или друг друга): аватар, ник, статистика, число друзей. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendProfileView(
    friend: FriendProfile,
    isMyFriend: Boolean,
    isMe: Boolean,
    fetchAvatarFull: (String, (String?) -> Unit) -> Unit,
    onCheckRequest: (String, (Boolean) -> Unit) -> Unit,
    scroll: ScrollState,
    onOpenFriends: () -> Unit,
    onSendRequest: () -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }
    var requestSent by remember(friend.uid) { mutableStateOf(false) }
    var showAvatar by remember { mutableStateOf(false) }
    LaunchedEffect(friend.uid) {
        if (!isMe && !isMyFriend) onCheckRequest(friend.uid) { if (it) requestSent = true }
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
                title = { Text(friend.nickname.ifBlank { "Профиль" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = { DayNightIndicator() },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scroll)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Avatar(
                base64 = friend.avatarBase64,
                size = 120.dp,
                // Тап открывает фото на весь экран (если оно есть).
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = friend.avatarBase64.isNotBlank()) { showAvatar = true },
            )
            Text(
                text = friend.nickname.ifBlank { "Без ника" },
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(),
            ) {
                val percent =
                    if (friend.answersTotal > 0) "${friend.answersCorrect * 100 / friend.answersTotal}%"
                    else "—"
                FriendStat(
                    value = "${friend.wordCount}",
                    label = plural(friend.wordCount, "слово в словаре", "слова в словаре", "слов в словаре"),
                    modifier = Modifier.weight(1f),
                )
                FriendStat(
                    value = "${friend.answersTotal}",
                    label = plural(friend.answersTotal, "ответ за всё время", "ответа за всё время", "ответов за всё время"),
                    modifier = Modifier.weight(1f),
                )
                FriendStat(value = percent, label = "верных ответов", modifier = Modifier.weight(1f))
            }
            WideStatCard(
                value = "${friend.friendCount}",
                label = plural(friend.friendCount, "друг", "друга", "друзей"),
                onClick = onOpenFriends,
                modifier = Modifier.widthIn(max = 480.dp),
            )
            Spacer(Modifier.height(8.dp))
            when {
                isMyFriend -> TextButton(
                    onClick = { confirmRemove = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                ) {
                    Text("Удалить из друзей")
                }
                isMe -> Text(
                    text = "Это ты",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                requestSent -> Text(
                    text = "Заявка отправлена",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                else -> Button(onClick = {
                    onSendRequest()
                    requestSent = true
                }) {
                    Text("Добавить в друзья")
                }
            }
        }
    }

    if (showAvatar) {
        AvatarViewer(
            thumbBase64 = friend.avatarBase64,
            fetchFull = { onResult -> fetchAvatarFull(friend.uid, onResult) },
            onDismiss = { showAvatar = false },
        )
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = "Удалить из друзей?",
            message = "«${friend.nickname.ifBlank { "Без ника" }}» будет удалён из твоего списка друзей.",
            onConfirm = {
                confirmRemove = false
                onRemove()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

/** Список друзей произвольного пользователя (разовая загрузка). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendListView(
    vm: AppViewModel,
    uid: String,
    ownerNickname: String,
    scroll: ScrollState,
    onOpenProfile: (FriendProfile) -> Unit,
    onBack: () -> Unit,
) {
    var list by remember(uid) { mutableStateOf<List<FriendProfile>?>(null) }
    LaunchedEffect(uid) { vm.loadFriendsOf(uid) { list = it } }

    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                title = {
                    Text(if (ownerNickname.isBlank()) "Друзья" else "Друзья: $ownerNickname")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = { DayNightIndicator() },
            )
        },
    ) { innerPadding ->
        val data = list
        when {
            data == null -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            data.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Друзей пока нет",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scroll)
                    .padding(16.dp),
            ) {
                data.forEach { friend ->
                    FriendRow(friend) { onOpenProfile(friend) }
                }
            }
        }
    }
}

@Composable
private fun FriendStat(value: String, label: String, modifier: Modifier = Modifier) {
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
