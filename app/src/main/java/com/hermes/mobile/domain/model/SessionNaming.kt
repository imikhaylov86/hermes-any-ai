package com.hermes.mobile.domain.model

/**
 * Интеллектуальная генерация краткого и емкого названия чата по первому сообщению (Industry Best Practice)
 */
fun generateSessionTitle(content: String, attachments: List<Attachment> = emptyList()): String {
    val raw = content
        .replace(Regex("""@file:[^\s]+"""), "")
        .replace(Regex("""^/[a-zA-Z0-9_\-]+"""), "")
        .trim()

    val sourceText = if (raw.isNotBlank()) {
        raw
    } else if (attachments.isNotEmpty()) {
        "Файл: ${attachments.first().name}"
    } else {
        "Новая беседа"
    }

    val firstLine = sourceText.lines().firstOrNull { it.isNotBlank() }?.trim() ?: sourceText
    val clean = firstLine.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    
    return if (clean.length > 34) {
        val truncated = clean.take(34)
        val lastSpace = truncated.lastIndexOf(' ')
        if (lastSpace > 16) {
            truncated.take(lastSpace).trimEnd(',', '.', '!', '?', ';', ':') + "…"
        } else {
            truncated.trimEnd(',', '.', '!', '?', ';', ':') + "…"
        }
    } else {
        clean.trimEnd(',', ';', ':')
    }
}
