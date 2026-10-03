package com.yossibank.shared.auth

import com.yossibank.shared.core.ApiFailure

sealed interface LoginResult {
    data object LoggedIn : LoginResult

    data object Rejected : LoginResult

    data class Failed(
        val failure: ApiFailure,
    ) : LoginResult
}
