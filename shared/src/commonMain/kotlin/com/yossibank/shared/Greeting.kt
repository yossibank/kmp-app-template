package com.yossibank.shared

/**
 * 共通コアの公開 API のサンプル。
 *
 * iOS へは umbrella（この shared モジュール）が XCFramework として 1 枚だけ公開される。
 * 公開する型は data class / sealed interface / enum に限り、例外は投げない方針とする。
 */
class Greeting {
    fun greet(): String = "Hello, ${platform.name}!"
}
