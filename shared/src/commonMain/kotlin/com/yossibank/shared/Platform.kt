package com.yossibank.shared

/**
 * expect/actual は OS の薄いシムに限定する（実行環境名、時刻、UUID、ファイルパスなど）。
 * ビジネスロジックの分岐には使わず、interface + DI で注入する。
 */
interface Platform {
    val name: String
}

expect val platform: Platform
