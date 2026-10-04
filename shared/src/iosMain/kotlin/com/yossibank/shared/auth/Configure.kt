package com.yossibank.shared.auth

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun Session.configure(baseUrl: String) {
    val store = KeychainTokenStore()
    val files = NSFileManager.defaultManager
    val marker = files
        .URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
        ?.URLByAppendingPathComponent("com.yossibank.shared.installed")
        ?.path

    if (marker != null && !files.fileExistsAtPath(marker)) {
        store.clear()
        files.createFileAtPath(marker, null, null)
    }

    start(Backend(baseUrl, store))
}
