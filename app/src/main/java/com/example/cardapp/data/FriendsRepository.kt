package com.example.cardapp.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Друзья: поиск по нику, заявки, список друзей и их живые профили.
 *
 * Структура в Firestore:
 * - nicknames/{ник} → { uid } — реестр занятых ников (для уникальности и поиска);
 * - users/{uid}/requests/{отправитель} — входящие заявки;
 * - users/{uid}/friends/{друг} — список друзей (записи с обеих сторон).
 */
class FriendsRepository(private val uid: String) {

    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")

    private val _requests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val requests: StateFlow<List<FriendRequest>> = _requests.asStateFlow()

    private val _friends = MutableStateFlow<List<FriendProfile>>(emptyList())
    val friends: StateFlow<List<FriendProfile>> = _friends.asStateFlow()

    // Живые подписки на профиль каждого друга: ник, аватарка и статистика
    // обновляются у нас сами, когда друг что-то меняет.
    private val profileListeners = mutableMapOf<String, ListenerRegistration>()
    private val profiles = mutableMapOf<String, FriendProfile>()

    private val requestsListener: ListenerRegistration =
        usersCollection.document(uid).collection("requests")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _requests.value = snapshot.documents.map { doc ->
                        FriendRequest(
                            fromUid = doc.id,
                            fromNickname = doc.getString("nickname") ?: "",
                        )
                    }
                }
            }

    private val friendsListener: ListenerRegistration =
        usersCollection.document(uid).collection("friends")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                val ids = snapshot.documents.map { it.id }.toSet()
                (profileListeners.keys - ids).forEach { gone ->
                    profileListeners.remove(gone)?.remove()
                    profiles.remove(gone)
                }
                publish()
                ids.filterNot { profileListeners.containsKey(it) }.forEach { friendUid ->
                    profileListeners[friendUid] = usersCollection.document(friendUid)
                        .addSnapshotListener { doc, _ ->
                            if (doc != null) {
                                profiles[friendUid] = doc.toFriendProfile(friendUid)
                                publish()
                            }
                        }
                }
            }

    private fun publish() {
        _friends.value = profiles.values.sortedBy { it.nickname.lowercase() }
    }

    fun searchByNickname(query: String, onResult: (List<FriendProfile>) -> Unit) {
        val key = query.trim().lowercase()
        if (key.isEmpty()) {
            onResult(emptyList())
            return
        }
        db.collection("nicknames").document(key).get()
            .addOnSuccessListener { nickDoc ->
                val ownerUid = nickDoc.getString("uid")
                if (ownerUid == null) {
                    onResult(emptyList())
                } else {
                    usersCollection.document(ownerUid).get()
                        .addOnSuccessListener { doc ->
                            onResult(
                                if (doc.exists()) listOf(doc.toFriendProfile(ownerUid))
                                else emptyList()
                            )
                        }
                        .addOnFailureListener { onResult(emptyList()) }
                }
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    /**
     * Разовая загрузка списка друзей произвольного пользователя (для просмотра
     * друзей друга). Правила Firestore разрешают чтение чужого списка друзей
     * любому вошедшему; писать в него по-прежнему могут только двое из дружбы.
     */
    fun fetchFriendsOf(uid: String, onResult: (List<FriendProfile>) -> Unit) {
        usersCollection.document(uid).collection("friends").get()
            .addOnSuccessListener { snap ->
                val ids = snap.documents.map { it.id }
                if (ids.isEmpty()) {
                    onResult(emptyList())
                    return@addOnSuccessListener
                }
                val result = mutableListOf<FriendProfile>()
                var remaining = ids.size
                ids.forEach { fid ->
                    usersCollection.document(fid).get().addOnCompleteListener { task ->
                        val doc = task.result
                        if (doc != null && doc.exists()) result.add(doc.toFriendProfile(fid))
                        remaining--
                        if (remaining == 0) onResult(result.sortedBy { it.nickname.lowercase() })
                    }
                }
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    /**
     * Полная аватарка любого пользователя — качается только при открытии
     * просмотра. [cacheFirst] — сначала пробуем офлайн-кэш Firestore (для
     * своей аватарки: свежее, чем у нас, там быть не может, зато мгновенно
     * и без сети); чужие всегда тянем с сервера, чтобы не показать старую.
     */
    fun fetchAvatarFull(uid: String, cacheFirst: Boolean, onResult: (String?) -> Unit) {
        val doc = usersCollection.document(uid).collection("avatar").document("full")
        fun fromServer() {
            doc.get()
                .addOnSuccessListener { onResult(it.getString("imageBase64")) }
                .addOnFailureListener { onResult(null) }
        }
        if (!cacheFirst) {
            fromServer()
            return
        }
        doc.get(Source.CACHE)
            .addOnSuccessListener { snap ->
                val cached = snap.getString("imageBase64")
                if (cached != null) onResult(cached) else fromServer()
            }
            .addOnFailureListener { fromServer() }
    }

    /**
     * Уже отправлена ли мной заявка этому пользователю. Исходящая заявка —
     * это документ `users/{toUid}/requests/{myUid}`; читать его я вправе как
     * отправитель. Нужно, чтобы кнопка «Добавить» не позволяла слать заявку
     * повторно после ухода с экрана и возврата.
     */
    fun hasOutgoingRequest(toUid: String, onResult: (Boolean) -> Unit) {
        usersCollection.document(toUid).collection("requests").document(uid).get()
            .addOnSuccessListener { onResult(it.exists()) }
            .addOnFailureListener { onResult(false) }
    }

    fun sendRequest(toUid: String, myNickname: String) {
        usersCollection.document(toUid).collection("requests").document(uid)
            .set(
                mapOf(
                    "nickname" to myNickname,
                    "timestamp" to System.currentTimeMillis(),
                )
            )
    }

    fun acceptRequest(request: FriendRequest) {
        val since = mapOf("since" to System.currentTimeMillis())
        val batch = db.batch()
        batch.set(
            usersCollection.document(uid).collection("friends").document(request.fromUid),
            since,
        )
        batch.set(
            usersCollection.document(request.fromUid).collection("friends").document(uid),
            since,
        )
        batch.delete(usersCollection.document(uid).collection("requests").document(request.fromUid))
        batch.commit()
    }

    fun declineRequest(request: FriendRequest) {
        usersCollection.document(uid).collection("requests").document(request.fromUid).delete()
    }

    fun removeFriend(friendUid: String) {
        val batch = db.batch()
        batch.delete(usersCollection.document(uid).collection("friends").document(friendUid))
        batch.delete(usersCollection.document(friendUid).collection("friends").document(uid))
        batch.commit()
    }

    /** Отписка от всех обновлений — вызывается при выходе из аккаунта. */
    fun close() {
        requestsListener.remove()
        friendsListener.remove()
        profileListeners.values.forEach { it.remove() }
        profileListeners.clear()
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toFriendProfile(uid: String) =
    FriendProfile(
        uid = uid,
        nickname = getString("nickname") ?: "",
        avatarBase64 = getString("avatarBase64") ?: "",
        wordCount = (getLong("wordCount") ?: 0L).toInt(),
        answersTotal = (getLong("answersTotal") ?: 0L).toInt(),
        answersCorrect = (getLong("answersCorrect") ?: 0L).toInt(),
        friendCount = (getLong("friendCount") ?: 0L).toInt(),
    )
