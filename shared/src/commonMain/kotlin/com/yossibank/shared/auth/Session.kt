package com.yossibank.shared.auth

import kotlin.concurrent.Volatile

object Session {
    @Volatile
    private var backend: Backend? = null

    internal val current: Backend
        get() = checkNotNull(backend) { "Session.configure を先に呼んでください" }

    internal fun start(backend: Backend) {
        this.backend = backend
    }

    val isLoggedIn: Boolean
        get() = current.isLoggedIn

    suspend fun login(
        username: String,
        password: String,
    ): LoginResult = current.login(username, password)

    fun logout() = current.logout()
}
