package com.example.cardapp.data

import kotlinx.coroutines.flow.StateFlow

/**
 * Источник данных приложения: [LocalWordRepository] (на устройстве) и
 * [FirestoreWordRepository] (облако) реализуют один интерфейс, поэтому
 * остальной код от способа хранения не зависит.
 */
interface WordRepository {
    val words: StateFlow<List<Word>>
    val events: StateFlow<List<ReviewEvent>>
    val profile: StateFlow<UserProfile>

    fun updateProfile(profile: UserProfile)

    fun addWord(term: String, translations: List<String>, description: String)
    fun updateWord(word: Word)
    fun deleteWord(id: String)
    fun addEvent(event: ReviewEvent)

    /**
     * Полный сброс прогресса: удаляет всю историю ответов и обнуляет
     * расписание повторений (все слова — в сегодняшнее повторение).
     * Сами слова не трогает.
     */
    fun resetProgress()
}
