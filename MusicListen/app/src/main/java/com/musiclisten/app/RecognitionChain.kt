package com.musiclisten.app

import java.io.File

interface SongSource {
    val id: String
    fun recognize(file: File): TrackResult?
}

data class Recognition(val track: TrackResult?, val provider: String)

/**
 * Tries providers in order. A "no song" answer stops the chain.
 * A quota error disables that provider and tries the next one.
 */
class RecognitionChain(
    private val sources: List<SongSource>,
    private val onQuota: (String) -> Unit = {},
) {
    fun recognize(file: File): Recognition {
        if (sources.isEmpty()) {
            throw IllegalStateException("Нет сервиса распознавания")
        }
        var blocked = 0
        var lastQuota = ""
        for (source in sources) {
            try {
                return Recognition(source.recognize(file), source.id)
            } catch (quota: ProviderQuotaException) {
                blocked++
                lastQuota = quota.message ?: source.id
                onQuota(source.id)
            }
        }
        if (blocked == sources.size) {
            throw IllegalStateException(lastQuota.ifBlank { "Лимит распознавания кончился" })
        }
        throw IllegalStateException("Не удалось распознать")
    }
}
