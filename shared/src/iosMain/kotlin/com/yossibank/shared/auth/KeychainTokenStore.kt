package com.yossibank.shared.auth

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.serialization.json.Json
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class KeychainTokenStore(
    private val service: String = "com.yossibank.shared.tokens",
) : TokenStore {
    override fun load(): Tokens? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = query(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne) {
            SecItemCopyMatching(it, result.ptr)
        }

        if (status != errSecSuccess) return null

        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        val text = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString() ?: return null

        runCatching { Json.decodeFromString<Tokens>(text) }.getOrNull()
    }

    override fun save(tokens: Tokens) {
        clear()

        val data = NSString.create(string = Json.encodeToString(tokens)).dataUsingEncoding(NSUTF8StringEncoding)

        query(kSecValueData to data, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly) {
            SecItemAdd(it, null)
        }
    }

    override fun clear() {
        query { SecItemDelete(it) }
    }

    private inline fun <T> query(
        vararg attributes: Pair<CFStringRef?, Any?>,
        block: (CFDictionaryRef?) -> T,
    ): T {
        val retained = mutableListOf<COpaquePointer?>()
        val dictionary = CFDictionaryCreateMutable(
            null,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )
        val entries = listOf<Pair<CFStringRef?, Any?>>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service,
        ) + attributes

        for ((key, value) in entries) {
            val cfValue: COpaquePointer? = if (value is CPointer<*>) value else CFBridgingRetain(value).also { retained += it }
            CFDictionaryAddValue(dictionary, key, cfValue)
        }

        return try {
            block(dictionary)
        } finally {
            retained.forEach { CFRelease(it) }
            CFRelease(dictionary)
        }
    }
}
