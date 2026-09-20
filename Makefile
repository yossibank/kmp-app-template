# CI は増分を当てにしない。UP-TO-DATE / FROM-CACHE で素通りすると、
# 警告もテスト結果も出ないまま green になる。
ifdef CI
GRADLE_FLAGS := --rerun-tasks
endif

.PHONY: verify lint format build build-android build-ios publish-local publish-github test clean

verify:
	./gradlew :shared:ktlintCheck :shared:assembleSharedReleaseXCFramework :shared:allTests $(GRADLE_FLAGS)

lint:
	./gradlew :shared:ktlintCheck $(GRADLE_FLAGS)

format:
	./gradlew :shared:ktlintFormat

build: build-android build-ios

build-android:
	./gradlew :shared:assembleAndroidMain $(GRADLE_FLAGS)

build-ios:
	./gradlew :shared:assembleSharedXCFramework $(GRADLE_FLAGS)

publish-local:
	./gradlew :shared:publishToMavenLocal

# GitHub Packages へ publish する。同一バージョンの上書きは 409 で拒否される。
publish-github:
	./gradlew :shared:publishAllPublicationsToGitHubPackagesRepository

test:
	./gradlew :shared:allTests $(GRADLE_FLAGS)

clean:
	./gradlew clean
