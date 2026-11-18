package com.musiclisten.app

import android.content.Context
import java.io.File

fun buildRecognitionChain(context: Context): RecognitionChain {
    val sources = mutableListOf<SongSource>()
    val audd = TokenStore.get(context)
    if (audd.isNotBlank() && !ProviderState.isExhausted(context, "AudD")) {
        sources += object : SongSource {
            override val id = "AudD"
            override fun recognize(file: File) = AuddClient(audd).recognize(file)
        }
    }
    sources += object : SongSource {
        override val id = "Song Finder"
        override fun recognize(file: File) = SongFinderClient().recognize(file)
    }
    return RecognitionChain(sources) { provider ->
        if (provider != "Song Finder") ProviderState.markExhausted(context, provider)
    }
}
