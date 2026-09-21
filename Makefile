ifdef CI
GRADLE_FLAGS := --rerun-tasks
endif

.PHONY: verify lint format api build build-android build-ios publish-local publish-github test clean

verify:
	./gradlew :shared:ktlintCheck :shared:checkKotlinAbi :shared:assembleAndroidMain :shared:assembleSharedReleaseXCFramework :shared:allTests $(GRADLE_FLAGS)

lint:
	./gradlew :shared:ktlintCheck $(GRADLE_FLAGS)

format:
	./gradlew :shared:ktlintFormat

api:
	./gradlew :shared:updateKotlinAbi

build: build-android build-ios

build-android:
	./gradlew :shared:assembleAndroidMain $(GRADLE_FLAGS)

build-ios:
	./gradlew :shared:assembleSharedXCFramework $(GRADLE_FLAGS)

publish-local:
	./gradlew :shared:publishToMavenLocal

publish-github:
	./gradlew :shared:publishAllPublicationsToGitHubPackagesRepository

test:
	./gradlew :shared:allTests $(GRADLE_FLAGS)

clean:
	./gradlew clean
