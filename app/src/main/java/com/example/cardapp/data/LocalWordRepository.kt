package com.example.cardapp.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Хранит слова и историю ответов в SharedPreferences в виде JSON. */
class LocalWordRepository(context: Context) : WordRepository {

    private val prefs = context.getSharedPreferences("cardapp_data", Context.MODE_PRIVATE)

    private val _words = MutableStateFlow(loadWords())
    override val words: StateFlow<List<Word>> = _words.asStateFlow()

    private val _events = MutableStateFlow(loadEvents())
    override val events: StateFlow<List<ReviewEvent>> = _events.asStateFlow()

    private val _profile = MutableStateFlow(loadProfile())
    override val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    override fun updateProfile(profile: UserProfile) {
        _profile.value = profile
        prefs.edit {
            putString(KEY_PROFILE, JSONObject().apply {
                put("nickname", profile.nickname)
                put("avatarBase64", profile.avatarBase64)
            }.toString())
        }
    }

    private fun loadProfile(): UserProfile {
        val raw = prefs.getString(KEY_PROFILE, null) ?: return UserProfile()
        return try {
            val obj = JSONObject(raw)
            UserProfile(
                nickname = obj.optString("nickname"),
                avatarBase64 = obj.optString("avatarBase64"),
            )
        } catch (_: Exception) {
            UserProfile()
        }
    }

    override fun addWord(term: String, translations: List<String>, description: String) {
        val cleanedTerm = normalizeCardText(term)
        val cleaned = normalizeTranslations(translations)
        if (cleanedTerm.isEmpty() || cleaned.isEmpty()) return
        val word = Word(
            id = UUID.randomUUID().toString(),
            term = cleanedTerm,
            translations = cleaned,
            createdAt = System.currentTimeMillis(),
            description = description.trim(),
        )
        _words.value = _words.value + word
        saveWords()
    }

    override fun updateWord(word: Word) {
        _words.value = _words.value.map { if (it.id == word.id) word else it }
        saveWords()
    }

    override fun deleteWord(id: String) {
        _words.value = _words.value.filterNot { it.id == id }
        saveWords()
    }

    override fun addEvent(event: ReviewEvent) {
        _events.value = _events.value + event
        saveEvents()
    }

    override fun resetProgress() {
        _events.value = emptyList()
        saveEvents()
        _words.value = _words.value.map { it.copy(intervalDays = 0, nextReviewAt = 0L) }
        saveWords()
    }

    private fun saveWords() {
        val array = JSONArray()
        _words.value.forEach { word ->
            array.put(JSONObject().apply {
                put("id", word.id)
                put("term", word.term)
                put("translations", JSONArray(word.translations))
                put("createdAt", word.createdAt)
                put("intervalDays", word.intervalDays)
                put("nextReviewAt", word.nextReviewAt)
                put("description", word.description)
            })
        }
        prefs.edit { putString(KEY_WORDS, array.toString()) }
    }

    private fun loadWords(): List<Word> {
        val raw = prefs.getString(KEY_WORDS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                // Старый формат хранил один перевод в поле "translation" —
                // читаем и его, чтобы не потерять уже добавленные слова.
                val translations = obj.optJSONArray("translations")
                    ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                    ?: listOfNotNull(obj.optString("translation").takeIf { it.isNotBlank() })
                Word(
                    id = obj.getString("id"),
                    term = obj.getString("term"),
                    translations = translations,
                    createdAt = obj.getLong("createdAt"),
                    intervalDays = obj.optInt("intervalDays", 0),
                    nextReviewAt = obj.optLong("nextReviewAt", 0L),
                    description = obj.optString("description", ""),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveEvents() {
        val array = JSONArray()
        _events.value.forEach { event ->
            array.put(JSONObject().apply {
                put("wordId", event.wordId)
                put("timestamp", event.timestamp)
                put("remembered", event.remembered)
            })
        }
        prefs.edit { putString(KEY_EVENTS, array.toString()) }
    }

    private fun loadEvents(): List<ReviewEvent> {
        val raw = prefs.getString(KEY_EVENTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ReviewEvent(
                    wordId = obj.getString("wordId"),
                    timestamp = obj.getLong("timestamp"),
                    remembered = obj.getBoolean("remembered"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private companion object {
        const val KEY_WORDS = "words"
        const val KEY_EVENTS = "events"
        const val KEY_PROFILE = "profile"
    }
}
