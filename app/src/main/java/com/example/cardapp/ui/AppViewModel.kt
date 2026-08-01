package com.example.cardapp.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.util.Base64
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardapp.data.FirestoreWordRepository
import com.example.cardapp.data.FriendProfile
import com.example.cardapp.data.FriendRequest
import com.example.cardapp.data.FriendsRepository
import com.example.cardapp.data.LocalWordRepository
import com.example.cardapp.data.ReviewEvent
import com.example.cardapp.data.UserProfile
import com.example.cardapp.data.Word
import com.example.cardapp.data.WordRepository
import com.example.cardapp.data.normalizeCardText
import com.example.cardapp.data.normalizeTranslations
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

const val DAY_MS = 24 * 60 * 60 * 1000L

/** Режим показа карточек: что на лицевой стороне, что на обороте. */
enum class CardMode {
    /** Слово спереди, перевод сзади. */
    MAIN,

    /** Перевод спереди, слово сзади. */
    REVERSE,

    /** Случайно на каждой карточке — основной или реверс. */
    COMBO,
}

/** Длительность съезда подсветки режима, мс (общая для VM и экрана). */
internal const val MODE_HL_DURATION_MS = 300L

/**
 * Атомарный снимок анимации подсветки режима: откуда, куда, на сколько прошла.
 * [gen] — поколение: увеличивается при каждом выборе, чтобы «хвост» прошлой
 * анимации не мог записать свой устаревший прогресс поверх нового.
 */
data class ModeHighlight(val start: Float, val target: Float, val elapsedMs: Long, val gen: Int)

/** Плавность для подсветки режима (smoothstep) — одна и та же в VM и на экране. */
internal fun cardModeEase(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** Тип сессии повторения. */
enum class SessionMode {
    /** «Начать повторение» — по расписанию, двигает интервалы слов. */
    DUE,

    /** «Повторить все» — тренировочная. */
    ALL,

    /** «Повторить N случайных» — тренировочная. */
    RANDOM,
}

/**
 * Текущая сессия повторения: очередь карточек и счётчики ответов.
 * Каждая карточка показывается один раз; слово с ответом «не помню»
 * остаётся «к повторению» и вернётся в следующей сессии.
 *
 * Расписание повторений двигает только сессия [SessionMode.DUE];
 * тренировочные идут в статистику, но интервалы слов не трогают.
 */
data class ReviewSession(
    val queue: List<Word>,
    val answered: Int = 0,
    val correct: Int = 0,
    val finished: Boolean = false,
    val mode: SessionMode = SessionMode.DUE,
) {
    val affectsSchedule: Boolean get() = mode == SessionMode.DUE
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val auth = FirebaseAuth.getInstance()

    // --- Аккаунт ---

    private val _user = MutableStateFlow(auth.currentUser)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    /** Информационные сообщения («письмо отправлено» и т.п.). */
    private val _authInfo = MutableStateFlow<String?>(null)
    val authInfo: StateFlow<String?> = _authInfo.asStateFlow()

    /** Подтверждена ли почта — без этого приложение не пускает дальше. */
    private val _emailVerified = MutableStateFlow(auth.currentUser?.isEmailVerified == true)
    val emailVerified: StateFlow<Boolean> = _emailVerified.asStateFlow()

    // --- Данные ---

    private val _words = MutableStateFlow<List<Word>>(emptyList())
    val words: StateFlow<List<Word>> = _words.asStateFlow()

    private val _events = MutableStateFlow<List<ReviewEvent>>(emptyList())
    val events: StateFlow<List<ReviewEvent>> = _events.asStateFlow()

    private val _profile = MutableStateFlow(UserProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    /** Сообщения экрана профиля («ник занят» и т.п.). */
    private val _profileMessage = MutableStateFlow<String?>(null)
    val profileMessage: StateFlow<String?> = _profileMessage.asStateFlow()

    // --- Друзья ---

    private val _friends = MutableStateFlow<List<FriendProfile>>(emptyList())
    val friends: StateFlow<List<FriendProfile>> = _friends.asStateFlow()

    private val _friendRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val friendRequests: StateFlow<List<FriendRequest>> = _friendRequests.asStateFlow()

    /** Состояние поиска друга по нику. */
    data class FriendSearch(
        val query: String = "",
        val searching: Boolean = false,
        val results: List<FriendProfile> = emptyList(),
        val notFound: Boolean = false,
    )

    private val _friendSearch = MutableStateFlow(FriendSearch())
    val friendSearch: StateFlow<FriendSearch> = _friendSearch.asStateFlow()

    private val sessionPrefs =
        app.getSharedPreferences("cardapp_session", Context.MODE_PRIVATE)

    // Локальные настройки этого устройства (не уходят в облако).
    private val localPrefs =
        app.getSharedPreferences("cardapp_local", Context.MODE_PRIVATE)

    // Жидкое стекло (преломление/блики/прозрачность) — можно выключить,
    // тогда поверхности становятся непрозрачными. Фон-меш остаётся всегда.
    private val _liquidGlass = MutableStateFlow(localPrefs.getBoolean(KEY_LIQUID_GLASS, true))
    val liquidGlass: StateFlow<Boolean> = _liquidGlass.asStateFlow()

    fun toggleLiquidGlass() {
        val v = !_liquidGlass.value
        _liquidGlass.value = v
        localPrefs.edit { putBoolean(KEY_LIQUID_GLASS, v) }
    }

    // --- Режим карточек ---

    private val _cardMode = MutableStateFlow(loadCardMode())
    val cardMode: StateFlow<CardMode> = _cardMode.asStateFlow()

    // Подсветка выбранного режима: положение (0..2) как функция времени.
    // Все три величины — в ОДНОМ объекте, чтобы экран всегда читал их
    // согласованным снимком (иначе при спаме успевала считаться новая цель со
    // старым прогрессом → подсветка прыгала). Прогресс хранится в ViewModel,
    // как у карточки-награды, чтобы анимация переживала уход с экрана.
    private val _modeHighlight = MutableStateFlow(
        ModeHighlight(
            start = _cardMode.value.ordinal.toFloat(),
            target = _cardMode.value.ordinal.toFloat(),
            elapsedMs = MODE_HL_DURATION_MS,
            gen = 0,
        )
    )
    val modeHighlight: StateFlow<ModeHighlight> = _modeHighlight.asStateFlow()

    fun selectCardMode(mode: CardMode) {
        if (mode == _cardMode.value) return
        // Текущее видимое положение подсветки становится новой стартовой точкой.
        val h = _modeHighlight.value
        val f = cardModeEase(h.elapsedMs.toFloat() / MODE_HL_DURATION_MS)
        val current = h.start + (h.target - h.start) * f
        _modeHighlight.value = ModeHighlight(current, mode.ordinal.toFloat(), 0L, h.gen + 1)
        _cardMode.value = mode
        localPrefs.edit { putString(KEY_CARD_MODE, mode.name) }
    }

    fun updateModeHlElapsed(ms: Long, gen: Int) {
        val h = _modeHighlight.value
        // Применяем прогресс только от актуальной анимации.
        if (h.gen == gen) _modeHighlight.value = h.copy(elapsedMs = ms)
    }

    private fun loadCardMode(): CardMode = try {
        CardMode.valueOf(localPrefs.getString(KEY_CARD_MODE, CardMode.MAIN.name) ?: CardMode.MAIN.name)
    } catch (_: Exception) {
        CardMode.MAIN
    }

    private val _session = MutableStateFlow<ReviewSession?>(null)
    val session: StateFlow<ReviewSession?> = _session.asStateFlow()

    private var repo: FirestoreWordRepository? = null
    private var friendsRepo: FriendsRepository? = null
    private var repoJob: Job? = null

    // Сессию прошлого запуска можно восстановить только после того,
    // как из Firestore (или его офлайн-кэша) приедут слова.
    private var sessionRestorePending = true

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val currentUser = firebaseAuth.currentUser
        _user.value = currentUser
        _emailVerified.value = currentUser?.isEmailVerified == true
        switchRepository(currentUser)
    }

    init {
        // Слушатель срабатывает сразу при регистрации — подхватит и сохранённый вход.
        auth.addAuthStateListener(authListener)
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
        repoJob?.cancel()
        repo?.close()
        friendsRepo?.close()
    }

    /** Пересоздаёт хранилища при входе/выходе из аккаунта. */
    private fun switchRepository(user: FirebaseUser?) {
        repoJob?.cancel()
        repo?.close()
        friendsRepo?.close()
        repo = null
        friendsRepo = null
        avatarFullCache.clear()
        if (user == null) {
            _words.value = emptyList()
            _events.value = emptyList()
            _profile.value = UserProfile()
            _friends.value = emptyList()
            _friendRequests.value = emptyList()
            _friendSearch.value = FriendSearch()
            setSession(null)
            sessionRestorePending = false
            return
        }
        val newRepo = FirestoreWordRepository(user.uid)
        val newFriendsRepo = FriendsRepository(user.uid)
        repo = newRepo
        friendsRepo = newFriendsRepo
        repoJob = viewModelScope.launch {
            launch {
                newRepo.words.collect { list ->
                    _words.value = list
                    if (sessionRestorePending && list.isNotEmpty()) {
                        sessionRestorePending = false
                        if (_session.value == null) {
                            loadSession(list)?.let { _session.value = it }
                        }
                    }
                }
            }
            launch { newRepo.events.collect { _events.value = it } }
            launch { newRepo.profile.collect { _profile.value = it } }
            launch {
                newFriendsRepo.friends.collect { list ->
                    _friends.value = list
                    // Держим публичное число друзей в профиле актуальным.
                    if (_profile.value.friendCount != list.size) {
                        newRepo.updateFriendCount(list.size)
                    }
                }
            }
            launch { newFriendsRepo.requests.collect { _friendRequests.value = it } }
            // Держим публичные агрегаты статистики в профиле актуальными —
            // именно их видят друзья.
            launch {
                combine(newRepo.words, newRepo.events, newRepo.profile) { w, e, p ->
                    Triple(w.size, e.size, e.count { it.remembered }) to p
                }.collect { (stats, currentProfile) ->
                    val (wordCount, total, correct) = stats
                    if (currentProfile.wordCount != wordCount ||
                        currentProfile.answersTotal != total ||
                        currentProfile.answersCorrect != correct
                    ) {
                        newRepo.updateStats(wordCount, total, correct)
                    }
                }
            }
        }
        migrateLocalData(newRepo)
    }

    /**
     * Разовый перенос данных, накопленных до подключения Firebase,
     * из локального хранилища в облако.
     */
    private fun migrateLocalData(target: WordRepository) {
        val app = getApplication<Application>()
        val prefs = app.getSharedPreferences("cardapp_data", Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val local = LocalWordRepository(app)
        local.words.value.forEach { target.updateWord(it) }
        local.events.value.forEach { target.addEvent(it) }
        prefs.edit { putBoolean(KEY_MIGRATED, true) }
    }

    // --- Вход и регистрация ---

    fun signIn(email: String, password: String) {
        _authBusy.value = true
        _authError.value = null
        _authInfo.value = null
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                _authBusy.value = false
                if (!task.isSuccessful) _authError.value = authErrorMessage(task.exception)
            }
    }

    fun signUp(email: String, password: String) {
        _authBusy.value = true
        _authError.value = null
        _authInfo.value = null
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                _authBusy.value = false
                if (task.isSuccessful) {
                    auth.currentUser?.sendEmailVerification()
                } else {
                    _authError.value = authErrorMessage(task.exception)
                }
            }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _authError.value = "Введи почту в поле выше — отправлю письмо для сброса пароля"
            return
        }
        _authBusy.value = true
        _authError.value = null
        _authInfo.value = null
        auth.sendPasswordResetEmail(email.trim())
            .addOnCompleteListener { task ->
                _authBusy.value = false
                if (task.isSuccessful) {
                    _authInfo.value = "Письмо для сброса пароля отправлено на ${email.trim()}"
                } else {
                    _authError.value = authErrorMessage(task.exception)
                }
            }
    }

    fun resendVerification() {
        val currentUser = auth.currentUser ?: return
        _authBusy.value = true
        _authError.value = null
        _authInfo.value = null
        currentUser.sendEmailVerification()
            .addOnCompleteListener { task ->
                _authBusy.value = false
                if (task.isSuccessful) {
                    _authInfo.value = "Письмо отправлено на ${currentUser.email}"
                } else {
                    _authError.value = authErrorMessage(task.exception)
                }
            }
    }

    fun checkEmailVerified() {
        val currentUser = auth.currentUser ?: return
        _authBusy.value = true
        _authError.value = null
        _authInfo.value = null
        currentUser.reload().addOnCompleteListener {
            _authBusy.value = false
            val verified = auth.currentUser?.isEmailVerified == true
            _emailVerified.value = verified
            if (!verified) {
                _authInfo.value = "Почта ещё не подтверждена — проверь письмо (и папку «Спам»)"
            }
        }
    }

    fun signOut() {
        _authError.value = null
        _authInfo.value = null
        auth.signOut()
    }

    fun clearAuthError() {
        _authError.value = null
        _authInfo.value = null
    }

    private fun authErrorMessage(e: Exception?): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "Слишком простой пароль: нужно не меньше 6 символов"
        is FirebaseAuthUserCollisionException -> "Аккаунт с этой почтой уже существует"
        is FirebaseAuthInvalidUserException -> "Аккаунт не найден"
        is FirebaseAuthInvalidCredentialsException -> "Неверная почта или пароль"
        is FirebaseNetworkException -> "Нет соединения с интернетом"
        else -> e?.localizedMessage ?: "Неизвестная ошибка"
    }

    // --- Сессии повторения ---

    private fun setSession(session: ReviewSession?) {
        _session.value = session
        persistSession(session)
    }

    fun startSession(allWords: Boolean) {
        val now = System.currentTimeMillis()
        val pool =
            if (allWords) words.value
            else words.value.filter { it.nextReviewAt <= now }
        if (pool.isEmpty()) return
        // По расписанию — не больше 50 случайных за сессию: остальные
        // остаются «к повторению» и попадут в следующие сессии.
        val queue =
            if (allWords) pool.shuffled()
            else pool.shuffled().take(DUE_SESSION_LIMIT)
        setSession(
            ReviewSession(
                queue = queue,
                mode = if (allWords) SessionMode.ALL else SessionMode.DUE,
            )
        )
    }

    /**
     * Полный сброс статистики: история ответов удаляется, расписание
     * обнуляется (все слова — в сегодняшнее повторение). Слова остаются.
     */
    fun resetStatistics() {
        repo?.resetProgress()
        setSession(null)
    }

    // Сколько случайных слов брать в сессию «Повторить случайные» (10..50).
    private val _randomCount = MutableStateFlow(
        localPrefs.getInt(KEY_RANDOM_COUNT, 10).coerceIn(10, 50)
    )
    val randomCount: StateFlow<Int> = _randomCount.asStateFlow()

    fun setRandomCount(count: Int) {
        val v = count.coerceIn(10, 50)
        _randomCount.value = v
        localPrefs.edit { putInt(KEY_RANDOM_COUNT, v) }
    }

    /**
     * Сессия из [randomCount] полностью случайных слов — расписание повторений
     * не учитывается; если слов меньше, берутся все.
     */
    fun startRandomSession() {
        val pool = words.value.shuffled().take(_randomCount.value)
        if (pool.isEmpty()) return
        setSession(ReviewSession(queue = pool, mode = SessionMode.RANDOM))
    }

    fun answer(remembered: Boolean) {
        val repo = repo ?: return
        val session = _session.value ?: return
        val word = session.queue.firstOrNull() ?: return
        val now = System.currentTimeMillis()

        repo.addEvent(ReviewEvent(word.id, now, remembered))

        if (session.affectsSchedule) {
            val updated = if (remembered) {
                val interval = if (word.intervalDays <= 0) 1 else word.intervalDays * 2
                word.copy(intervalDays = interval, nextReviewAt = now + interval * DAY_MS)
            } else {
                word.copy(intervalDays = 0, nextReviewAt = now)
            }
            repo.updateWord(updated)
        }

        val rest = session.queue.drop(1)
        setSession(
            session.copy(
                queue = rest,
                answered = session.answered + 1,
                correct = session.correct + if (remembered) 1 else 0,
                finished = rest.isEmpty(),
            )
        )
    }

    fun closeSession() {
        setSession(null)
    }

    // --- Операции со словами ---

    fun addWord(term: String, translations: List<String>, description: String) {
        repo?.addWord(term, translations, description)
    }

    /** Меняет текст, переводы и описание слова, не сбрасывая расписание. */
    fun editWord(id: String, term: String, translations: List<String>, description: String) {
        val repo = repo ?: return
        val word = words.value.find { it.id == id } ?: return
        val cleanedTerm = normalizeCardText(term)
        val cleaned = normalizeTranslations(translations)
        if (cleanedTerm.isEmpty() || cleaned.isEmpty()) return
        val updated = word.copy(term = cleanedTerm, translations = cleaned, description = description.trim())
        repo.updateWord(updated)
        // Если слово сейчас в очереди повторения — обновляем и там.
        setSession(_session.value?.let { session ->
            session.copy(queue = session.queue.map { if (it.id == id) updated else it })
        })
    }

    fun deleteWord(id: String) {
        repo?.deleteWord(id)
        setSession(_session.value?.let { session ->
            val queue = session.queue.filterNot { it.id == id }
            if (queue.isEmpty() && session.answered == 0) null
            else session.copy(queue = queue, finished = queue.isEmpty())
        })
    }

    // --- Профиль ---

    fun setNickname(nickname: String) {
        val repo = repo ?: return
        val cleaned = nickname.trim()
        _profileMessage.value = null
        when {
            cleaned.length < 3 -> {
                _profileMessage.value = "Ник — минимум 3 символа"
            }
            cleaned.contains("/") -> {
                _profileMessage.value = "Ник не может содержать «/»"
            }
            else -> repo.claimNickname(cleaned, _profile.value.nickname) { success ->
                if (!success) _profileMessage.value = "Ник «$cleaned» уже занят"
            }
        }
    }

    fun clearProfileMessage() {
        _profileMessage.value = null
    }

    // --- Друзья ---

    fun searchFriend(nickname: String) {
        val friendsRepo = friendsRepo ?: return
        _friendSearch.value = FriendSearch(query = nickname.trim(), searching = true)
        friendsRepo.searchByNickname(nickname) { results ->
            _friendSearch.value = FriendSearch(
                query = nickname.trim(),
                results = results,
                notFound = results.isEmpty(),
            )
        }
    }

    fun clearFriendSearch() {
        _friendSearch.value = FriendSearch()
    }

    fun sendFriendRequest(toUid: String) {
        friendsRepo?.sendRequest(toUid, _profile.value.nickname)
    }

    fun checkOutgoingRequest(toUid: String, onResult: (Boolean) -> Unit) {
        friendsRepo?.hasOutgoingRequest(toUid, onResult) ?: onResult(false)
    }

    fun acceptFriendRequest(request: FriendRequest) {
        friendsRepo?.acceptRequest(request)
    }

    fun declineFriendRequest(request: FriendRequest) {
        friendsRepo?.declineRequest(request)
    }

    fun removeFriend(friendUid: String) {
        friendsRepo?.removeFriend(friendUid)
    }

    fun loadFriendsOf(uid: String, onResult: (List<FriendProfile>) -> Unit) {
        friendsRepo?.fetchFriendsOf(uid, onResult) ?: onResult(emptyList())
    }

    // --- Аватарка ---

    /** Фото, выбранное для аватарки, — ждёт кадрирования на экране профиля. */
    private val _avatarDraft = MutableStateFlow<Bitmap?>(null)
    val avatarDraft: StateFlow<Bitmap?> = _avatarDraft.asStateFlow()

    /**
     * Читает выбранное фото (ужимая до [AVATAR_FULL_MAX_SIDE] по большей
     * стороне, пропорции сохраняются) и открывает кадрирование.
     */
    fun startAvatarEdit(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver

                // Сначала узнаём размеры, чтобы большие фото декодировать
                // сразу уменьшенными и не тратить память.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sampleSize = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= AVATAR_FULL_MAX_SIDE) {
                    sampleSize *= 2
                }

                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val source = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: return@launch

                // Точное ужатие до потолка по большей стороне.
                val maxSide = maxOf(source.width, source.height)
                _avatarDraft.value = if (maxSide > AVATAR_FULL_MAX_SIDE) {
                    val k = AVATAR_FULL_MAX_SIDE.toFloat() / maxSide
                    Bitmap.createScaledBitmap(
                        source,
                        (source.width * k).toInt().coerceAtLeast(1),
                        (source.height * k).toInt().coerceAtLeast(1),
                        true,
                    )
                } else {
                    source
                }
            } catch (_: Exception) {
                // Картинку не прочитать — оставляем как есть.
            }
        }
    }

    fun cancelAvatarEdit() {
        _avatarDraft.value = null
    }

    /**
     * Сохраняет выбранный кадр: квадрат [crop] уходит миниатюрой
     * [AVATAR_SIZE]×[AVATAR_SIZE] в публичный профиль (кружки и списки
     * друзей), а само фото целиком — отдельным документом, который клиенты
     * качают только при открытии полноэкранного просмотра.
     */
    fun confirmAvatar(crop: Rect) {
        val repo = repo ?: return
        val draft = _avatarDraft.value ?: return
        _avatarDraft.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val src = Rect(
                    crop.left.coerceIn(0, draft.width - 1),
                    crop.top.coerceIn(0, draft.height - 1),
                    crop.right.coerceIn(1, draft.width),
                    crop.bottom.coerceIn(1, draft.height),
                )
                val thumb = Bitmap.createBitmap(AVATAR_SIZE, AVATAR_SIZE, Bitmap.Config.ARGB_8888)
                Canvas(thumb).drawBitmap(
                    draft,
                    src,
                    Rect(0, 0, AVATAR_SIZE, AVATAR_SIZE),
                    Paint(Paint.FILTER_BITMAP_FLAG),
                )
                repo.updateProfile(
                    _profile.value.copy(avatarBase64 = encodeJpegAdaptive(thumb, AVATAR_MAX_BYTES))
                )
                val fullEncoded = encodeJpegAdaptive(draft, AVATAR_FULL_MAX_BYTES)
                repo.updateAvatarFull(fullEncoded)
                // Свежезалитая полная версия — сразу в кэш: просмотр своей
                // аватарки открывается без «мыла» и подгрузки.
                _user.value?.uid?.let { avatarFullCache[it] = fullEncoded }
            } catch (_: Exception) {
                // Картинку не обработать — оставляем как есть.
            }
        }
    }

    /** Кэш полных аватарок на время сессии: uid → base64. */
    private val avatarFullCache = mutableMapOf<String, String>()

    /** Полная аватарка пользователя (для просмотра); null в колбэке — её нет. */
    fun fetchAvatarFull(uid: String, onResult: (String?) -> Unit) {
        val friendsRepo = friendsRepo
        if (uid.isBlank() || friendsRepo == null) {
            onResult(null)
            return
        }
        avatarFullCache[uid]?.let {
            onResult(it)
            return
        }
        // Свою аватарку можно смело брать из офлайн-кэша Firestore — свежее,
        // чем у нас, там быть не может; чужие тянем с сервера.
        friendsRepo.fetchAvatarFull(uid, cacheFirst = uid == _user.value?.uid) { result ->
            if (result != null) avatarFullCache[uid] = result
            onResult(result)
        }
    }

    /** JPEG с подбором качества под потолок веса: документ Firestore ограничен 1 МиБ. */
    private fun encodeJpegAdaptive(bitmap: Bitmap, maxBytes: Int): String {
        var quality = 90
        var bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
        while (bytes.size > maxBytes && quality > 50) {
            quality -= 10
            bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // --- Сохранение сессии между запусками приложения ---

    private fun persistSession(session: ReviewSession?) {
        sessionPrefs.edit {
            // Завершённую сессию хранить незачем.
            if (session == null || session.finished) {
                remove(KEY_SESSION)
            } else {
                putString(KEY_SESSION, JSONObject().apply {
                    put("queue", JSONArray(session.queue.map { it.id }))
                    put("answered", session.answered)
                    put("correct", session.correct)
                    put("mode", session.mode.name)
                }.toString())
            }
        }
    }

    private fun loadSession(words: List<Word>): ReviewSession? {
        val raw = sessionPrefs.getString(KEY_SESSION, null) ?: return null
        return try {
            val obj = JSONObject(raw)
            val idsArray = obj.getJSONArray("queue")
            // Восстанавливаем слова по id из актуального словаря:
            // удалённые с прошлого запуска просто выпадают из очереди.
            val byId = words.associateBy { it.id }
            val queue = (0 until idsArray.length()).mapNotNull { byId[idsArray.getString(it)] }
            val mode = try {
                SessionMode.valueOf(obj.optString("mode", SessionMode.DUE.name))
            } catch (_: Exception) {
                SessionMode.DUE
            }
            if (queue.isEmpty()) null
            else ReviewSession(
                queue = queue,
                answered = obj.optInt("answered", 0),
                correct = obj.optInt("correct", 0),
                mode = mode,
            )
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val KEY_SESSION = "active_session"
        const val KEY_MIGRATED = "migrated_to_cloud"
        const val KEY_LIQUID_GLASS = "liquid_glass"
        const val KEY_CARD_MODE = "card_mode"
        const val KEY_RANDOM_COUNT = "random_count"

        /** Максимум карточек в одной сессии повторения по расписанию. */
        const val DUE_SESSION_LIMIT = 50

        /** Сторона квадрата миниатюры аватарки в пикселях. */
        const val AVATAR_SIZE = 512

        /** Потолок веса JPEG миниатюры (base64 получится примерно на треть больше). */
        const val AVATAR_MAX_BYTES = 220_000

        /** Большая сторона полной аватарки в пикселях. */
        const val AVATAR_FULL_MAX_SIDE = 1600

        /** Потолок веса полной аватарки: base64 (~+33%) должен влезть в документ 1 МиБ. */
        const val AVATAR_FULL_MAX_BYTES = 480_000
    }
}
