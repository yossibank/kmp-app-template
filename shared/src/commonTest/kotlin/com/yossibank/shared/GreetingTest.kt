package com.yossibank.shared

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * ビジネスロジックのテストは原則 commonTest に置く。
 * ここに書いたテストは Android と iOS の両方で実行される。
 */
class GreetingTest {
    @Test
    fun greeting_includes_platform_name() {
        val greeting = Greeting().greet()
        assertTrue(greeting.startsWith("Hello, "), "actual: $greeting")
        assertTrue(greeting.contains(platform.name), "actual: $greeting")
    }
}
