package com.yossibank.shared.auth

fun Session.configure(baseUrl: String) = start(Backend(baseUrl, KeychainTokenStore()))
