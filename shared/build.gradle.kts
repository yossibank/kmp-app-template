import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    `maven-publish`
}

group = "com.yossibank"
version = "0.1.0"

kotlin {
    // AGP 9 以降、KMP の Android ターゲットは kotlin { android { } } で設定する。
    // トップレベルの android { } ブロックは使わない。
    android {
        namespace = "com.yossibank.shared"
        compileSdk = 37
        minSdk = 24

        withHostTestBuilder {}.configure {}

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // iOS へは XCFramework 1 枚だけを公開する。
    val xcframework = XCFramework("Shared")
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            xcframework.add(this)
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
