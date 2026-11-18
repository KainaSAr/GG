package com.musiclisten.app

/** The provider refused because its account or trial quota is used up. */
class ProviderQuotaException(
    val provider: String,
    message: String,
) : IllegalStateException(message)
