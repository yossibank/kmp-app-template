import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.skie)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.openapi.generator)
    `maven-publish`
}

group = "com.yossibank"
version = "0.19.0"

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/yossibank/kmp-app-template")
            credentials {
                username = providers
                    .gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .orNull
                password = providers
                    .gradleProperty("gpr.token")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .orNull
            }
        }
    }
}

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        filters {
            exclude.byNames.add("com.yossibank.shared.generated.**")
        }
    }

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
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            binaryOption("bundleId", "com.yossibank.shared")
            xcframework.add(this)
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(
                files(layout.buildDirectory.dir("generated/openapi/src/commonMain/kotlin"))
                    .builtBy(tasks.named("openApiGenerate")),
            )
            dependencies {
                implementation(libs.bundles.common)
            }
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.bundles.test)
        }
    }
}

tasks.matching { it.name.contains("ArtProfile") }.configureEach {
    dependsOn(tasks.named("openApiGenerate"))
}

tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    exclude("**/generated/**")
}

skie {
    analytics {
        enabled.set(false)
    }
}

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

openApiGenerate {
    generatorName.set("kotlin")
    library.set("multiplatform")
    inputSpec.set("$projectDir/openapi/pokeapi.yml")
    outputDir.set(
        layout.buildDirectory
            .dir("generated/openapi")
            .get()
            .asFile.path,
    )
    modelPackage.set("com.yossibank.shared.generated.model")
    apiPackage.set("com.yossibank.shared.generated")
    packageName.set("com.yossibank.shared.generated")
    globalProperties.set(
        mapOf(
            "models" to listOf(
                "AbilitySummary",
                "GenerationSummary",
                "ItemSummary",
                "PokemonArtwork",
                "PokemonSpritesOther",
                "PaginatedPokemonSummaryList",
                "PokemonAbility",
                "PokemonAbilityPast",
                "PokemonCries",
                "PokemonDetail",
                "PokemonFormSummary",
                "PokemonGameIndex",
                "PokemonHeldItem",
                "PokemonHeldItemVersion",
                "PokemonPastAbility",
                "PokemonPastStat",
                "PokemonPastType",
                "PokemonSpeciesSummary",
                "PokemonSprites",
                "PokemonStat",
                "PokemonSummary",
                "PokemonType",
                "StatSummary",
                "TypeSummary",
                "VersionSummary",
            ).joinToString(","),
        ),
    )
    configOptions.set(
        mapOf(
            "dateLibrary" to "string",
        ),
    )
}
