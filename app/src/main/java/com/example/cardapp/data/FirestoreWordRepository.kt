package com.example.cardapp.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Облачное хранилище на Cloud Firestore: данные лежат под users/{uid}/…,
 * изменения приходят в реальном времени на все устройства аккаунта.
 * Офлайн-кэш Firestore включён по умолчанию: без сети приложение работает
 * с локальной копией, изменения синхронизируются при появлении интернета.
 */
class FirestoreWordRepository(uid: String) : WordRepository {

    private val db = FirebaseFirestore.getInstance()
    private val userDoc = db.collection("users").document(uid)
    private val wordsCollection = userDoc.collection("words")
    private val eventsCollection = userDoc.collection("events")

    private val _words = MutableStateFlow<List<Word>>(emptyList())
    override val words: StateFlow<List<Word>> = _words.asStateFlow()

    private val _events = MutableStateFlow<List<ReviewEvent>>(emptyList())
    override val events: StateFlow<List<ReviewEvent>> = _events.asStateFlow()

    private val wordsListener: ListenerRegistration =
        wordsCollection.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                _words.value = snapshot.documents.mapNotNull { it.toWordOrNull() }
            }
        }

    private val eventsListener: ListenerRegistration =
        eventsCollection.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                _events.value = snapshot.documents.mapNotNull { it.toEventOrNull() }
            }
        }

    private val _profile = MutableStateFlow(UserProfile())
    override val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val profileListener: ListenerRegistration =
        userDoc.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                _profile.value = UserProfile(
                    nickname = snapshot.getString("nickname") ?: "",
                    avatarBase64 = snapshot.getString("avatarBase64") ?: "",
                    wordCount = (snapshot.getLong("wordCount") ?: 0L).toInt(),
                    answersTotal = (snapshot.getLong("answersTotal") ?: 0L).toInt(),
                    answersCorrect = (snapshot.getLong("answersCorrect") ?: 0L).toInt(),
                    friendCount = (snapshot.getLong("friendCount") ?: 0L).toInt(),
                )
            }
        }

    override fun updateProfile(profile: UserProfile) {
        userDoc.set(
            mapOf(
                "nickname" to profile.nickname,
                "avatarBase64" to profile.avatarBase64,
            ),
            SetOptions.merge(),
        )
    }

    /**
     * Полная (некадрированная) аватарка — отдельным документом, чтобы не
     * раздувать публичный профиль, который целиком качают все друзья:
     * полную версию клиент забирает только при открытии просмотра.
     */
    fun updateAvatarFull(base64: String) {
        userDoc.collection("avatar").document("full").set(mapOf("imageBase64" to base64))
    }

    /** Публичные агрегаты статистики — их видят друзья. */
    fun updateStats(wordCount: Int, answersTotal: Int, answersCorrect: Int) {
        userDoc.set(
            mapOf(
                "wordCount" to wordCount,
                "answersTotal" to answersTotal,
                "answersCorrect" to answersCorrect,
            ),
            SetOptions.merge(),
        )
    }

    /** Публичное число друзей — его видят другие в профиле. */
    fun updateFriendCount(count: Int) {
        userDoc.set(mapOf("friendCount" to count), SetOptions.merge())
    }

    /**
     * Занимает ник в общем реестре nicknames/{ник в нижнем регистре}.
     * Уникальность гарантируют правила Firestore: перезаписать документ
     * чужого ника сервер не даст, и set() завершится ошибкой.
     */
    fun claimNickname(nickname: String, currentNickname: String, onResult: (Boolean) -> Unit) {
        val cleaned = nickname.trim()
        val newKey = cleaned.lowercase()
        val oldKey = currentNickname.trim().lowercase()
        if (newKey == oldKey) {

            db.collection("nicknames").document(newKey).set(mapOf("uid" to userDoc.id))
            updateProfile(_profile.value.copy(nickname = cleaned))
            onResult(true)
            return
        }
        db.collection("nicknames").document(newKey)
            .set(mapOf("uid" to userDoc.id))
            .addOnSuccessListener {
                if (oldKey.isNotEmpty()) {
                    db.collection("nicknames").document(oldKey).delete()
                }
                updateProfile(_profile.value.copy(nickname = cleaned))
                onResult(true)
            }
            .addOnFailureListener { onResult(false) }
    }

    override fun addWord(term: String, translations: List<String>, description: String) {
        val cleanedTerm = normalizeCardText(term)
        val cleaned = normalizeTranslations(translations)
        if (cleanedTerm.isEmpty() || cleaned.isEmpty()) return
        updateWord(
            Word(
                id = UUID.randomUUID().toString(),
                term = cleanedTerm,
                translations = cleaned,
                createdAt = System.currentTimeMillis(),
                description = description.trim(),
            )
        )
    }

    override fun updateWord(word: Word) {
        wordsCollection.document(word.id).set(
            mapOf(
                "term" to word.term,
                "translations" to word.translations,
                "createdAt" to word.createdAt,
                "intervalDays" to word.intervalDays,
                "nextReviewAt" to word.nextReviewAt,
                "description" to word.description,
            )
        )
    }

    override fun deleteWord(id: String) {
        wordsCollection.document(id).delete()
    }

    override fun addEvent(event: ReviewEvent) {
        eventsCollection.add(
            mapOf(
                "wordId" to event.wordId,
                "timestamp" to event.timestamp,
                "remembered" to event.remembered,
            )
        )
    }

    override fun resetProgress() {
        // Firestore не умеет удалять коллекцию одним запросом — забираем
        // документы и удаляем пачками (лимит батча — 500 операций).
        eventsCollection.get().addOnSuccessListener { snapshot ->
            snapshot.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit()
            }
        }
        wordsCollection.get().addOnSuccessListener { snapshot ->
            snapshot.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc ->
                    batch.update(doc.reference, mapOf("intervalDays" to 0, "nextReviewAt" to 0L))
                }
                batch.commit()
            }
        }
    }

    /** Отписка от обновлений — вызывается при выходе из аккаунта. */
    fun close() {
        wordsListener.remove()
        eventsListener.remove()
        profileListener.remove()
    }
}

private fun DocumentSnapshot.toWordOrNull(): Word? {
    val term = getString("term") ?: return null
    val translations = (get("translations") as? List<*>)?.map { it.toString() } ?: return null
    return Word(
        id = id,
        term = term,
        translations = translations,
        createdAt = getLong("createdAt") ?: 0L,
        intervalDays = (getLong("intervalDays") ?: 0L).toInt(),
        nextReviewAt = getLong("nextReviewAt") ?: 0L,
        description = getString("description") ?: "",
    )
}

private fun DocumentSnapshot.toEventOrNull(): ReviewEvent? {
    return ReviewEvent(
        wordId = getString("wordId") ?: return null,
        timestamp = getLong("timestamp") ?: return null,
        remembered = getBoolean("remembered") ?: return null,
    )
}
