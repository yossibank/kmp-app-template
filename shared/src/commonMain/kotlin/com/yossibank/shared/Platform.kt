package com.yossibank.shared

interface Platform {
    val name: String
}

expect val platform: Platform
