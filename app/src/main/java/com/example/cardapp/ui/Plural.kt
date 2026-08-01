package com.example.cardapp.ui

/**
 * Русская форма существительного при числительном:
 * plural(1, "слово", "слова", "слов") == "слово", 2 — «слова», 5 — «слов»,
 * 11–14 всегда «слов». Для родительного падежа («из 41 слова», «из 43 слов»)
 * передаются формы ("слова", "слов", "слов").
 */
fun plural(n: Int, one: String, few: String, many: String): String {
    val abs = if (n < 0) -n else n
    if (abs % 100 in 11..14) return many
    return when (abs % 10) {
        1 -> one
        2, 3, 4 -> few
        else -> many
    }
}
