package com.yossibank.shared.core

sealed interface ApiFailure {
    val canRetry: Boolean

    data object Offline : ApiFailure {
        override val canRetry = true
    }

    data object Timeout : ApiFailure {
        override val canRetry = true
    }

    data class Server(
        val statusCode: Int,
    ) : ApiFailure {
        override val canRetry = statusCode == 429 || statusCode >= 500
    }

    data object Unreadable : ApiFailure {
        override val canRetry = false
    }

    data object Closed : ApiFailure {
        override val canRetry = false
    }
}
