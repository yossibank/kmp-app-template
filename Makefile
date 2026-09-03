.PHONY: verify lint format build build-android build-ios publish-local publish-github test clean

verify:
	./gradlew :shared:ktlintCheck :shared:assembleSharedReleaseXCFramework :shared:allTests

lint:
	./gradlew :shared:ktlintCheck

format:
	./gradlew :shared:ktlintFormat

build: build-android build-ios

build-android:
	./gradlew :shared:assembleAndroidMain

build-ios:
	./gradlew :shared:assembleSharedXCFramework

publish-local:
	./gradlew :shared:publishToMavenLocal

# GitHub Packages へ publish する。同一バージョンの上書きは 409 で拒否される。
publish-github:
	./gradlew :shared:publishAllPublicationsToGitHubPackagesRepository

test:
	./gradlew :shared:allTests

clean:
	./gradlew clean
