package com.yossibank.shared

import kotlin.test.Test
import kotlin.test.assertTrue

class GreetingTest {
    @Test
    fun greeting_includes_platform_name() {
        val greeting = Greeting().greet()
        assertTrue(greeting.startsWith("Hello, "), "actual: $greeting")
        assertTrue(greeting.contains(platform.name), "actual: $greeting")
    }
}
