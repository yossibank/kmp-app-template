import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.skie)
    `maven-publish`
}

group = "com.yossibank"
version = "0.6.0"

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/yossibank/kmp-app-template")
            credentials {
                username =
                    providers
                        .gradleProperty("gpr.user")
                        .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                        .orNull
                password =
                    providers
                        .gradleProperty("gpr.token")
                        .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                        .orNull
            }
        }
    }
}

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

    val xcframework = XCFramework("Shared")
    // Apple Silicon のみを対象とするため iosX64（Intel シミュレータ）は持たない。
    listOf(
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

skie {
    analytics {
        // 機種を特定できるハードウェア情報を含むため送らない。変換機能には影響しない。
        enabled.set(false)
    }
}

// SPM の binaryTarget が要求する zip と checksum を生成する。
// XCFramework 自体は Kotlin プラグインの assembleSharedReleaseXCFramework が作るので、
// xcodebuild -create-xcframework を自前で呼ぶ必要はない。
abstract class PackageXCFrameworkTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:InputDirectory
    abstract val xcframeworkDir: DirectoryProperty

    @get:OutputFile
    abstract val zipFile: RegularFileProperty

    @get:OutputFile
    abstract val checksumFile: RegularFileProperty

    @TaskAction
    fun execute() {
        val xcframework = xcframeworkDir.get().asFile
        val zip = zipFile.get().asFile
        val checksum = checksumFile.get().asFile

        zip.parentFile.mkdirs()
        if (zip.exists()) zip.delete()

        execOperations.exec {
            commandLine(
                "ditto",
                "-c",
                "-k",
                "--sequesterRsrc",
                "--keepParent",
                xcframework.absolutePath,
                zip.absolutePath,
            )
        }

        val output = ByteArrayOutputStream()
        execOperations.exec {
            commandLine("swift", "package", "compute-checksum", zip.absolutePath)
            standardOutput = output
        }
        val value = output.toString().trim()
        checksum.writeText(value)

        logger.lifecycle("zip      : ${zip.absolutePath}")
        logger.lifecycle("checksum : $value")
    }
}

tasks.register<PackageXCFrameworkTask>("packageXCFramework") {
    dependsOn("assembleSharedReleaseXCFramework")
    group = "build"
    description = "Release の XCFramework を zip 化し、SPM 用の checksum を出力する"
    xcframeworkDir.set(layout.buildDirectory.dir("XCFrameworks/release/Shared.xcframework"))
    zipFile.set(layout.buildDirectory.file("spm/Shared.xcframework.zip"))
    checksumFile.set(layout.buildDirectory.file("spm/checksum.txt"))
}
