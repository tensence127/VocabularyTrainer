package com.example.cardapp.data

/**
 * Слово-карточка.
 *
 * intervalDays/nextReviewAt — интервальное повторение (упрощённый SM-2):
 * «помню» удваивает интервал, «не помню» сбрасывает его, и слово снова
 * становится доступным к повторению сразу.
 */
data class Word(
    val id: String,
    val term: String,
    val translations: List<String>,
    val createdAt: Long,
    val intervalDays: Int = 0,
    val nextReviewAt: Long = 0L,
    /** Необязательное пояснение к слову (может быть многострочным). */
    val description: String = "",
)

/** Одно нажатие «помню/не помню» — из этих событий строится статистика. */
data class ReviewEvent(
    val wordId: String,
    val timestamp: Long,
    val remembered: Boolean,
)

/**
 * Профиль пользователя — публичная часть данных (документ users/{uid}):
 * его могут читать другие пользователи (поиск по нику, профили друзей).
 * Почта сюда не попадает никогда. Аватарка хранится прямо в документе
 * в виде Base64-строки (сжатый JPEG 512×512). Статистика дублируется
 * готовыми числами, чтобы друзья видели её без доступа к сырой истории.
 */
data class UserProfile(
    val nickname: String = "",
    val avatarBase64: String = "",
    val wordCount: Int = 0,
    val answersTotal: Int = 0,
    val answersCorrect: Int = 0,
    val friendCount: Int = 0,
)

/** Публичный профиль другого пользователя (друг или результат поиска). */
data class FriendProfile(
    val uid: String,
    val nickname: String = "",
    val avatarBase64: String = "",
    val wordCount: Int = 0,
    val answersTotal: Int = 0,
    val answersCorrect: Int = 0,
    val friendCount: Int = 0,
)

/** Входящая заявка в друзья. */
data class FriendRequest(
    val fromUid: String,
    val fromNickname: String,
)

/**
 * Единый формат текста карточки: первая буква заглавная, остальное строчными.
 * Для фраз заглавной становится только первая буква первого слова:
 * «долгое НЕЖНОЕ объятие» → «Долгое нежное объятие».
 */
fun normalizeCardText(raw: String): String {
    val locale = java.util.Locale.getDefault()
    return raw.trim().lowercase(locale).replaceFirstChar { it.titlecase(locale) }
}

/** Нормализует список переводов: формат, без пустых, без дубликатов. */
fun normalizeTranslations(raw: List<String>): List<String> =
    raw.map(::normalizeCardText).filter { it.isNotEmpty() }.distinct()
