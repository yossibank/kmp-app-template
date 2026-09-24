package com.yossibank.shared

sealed interface PokemonFailure {
    val canRetry: Boolean

    data object Offline : PokemonFailure {
        override val canRetry = true
    }

    data object Timeout : PokemonFailure {
        override val canRetry = true
    }

    data class Server(
        val statusCode: Int,
    ) : PokemonFailure {
        override val canRetry = statusCode == 429 || statusCode >= 500
    }

    data object Unexpected : PokemonFailure {
        override val canRetry = false
    }

    data object Closed : PokemonFailure {
        override val canRetry = false
    }
}
