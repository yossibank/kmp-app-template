package com.yossibank.shared.auth

import android.content.Context

fun Session.configure(
    context: Context,
    baseUrl: String,
) = start(Backend(baseUrl, KeystoreTokenStore(context.applicationContext)))
