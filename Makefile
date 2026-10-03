.PHONY: verify lint format api build build-android build-ios publish-local publish test clean

verify:
	./gradlew :shared:ktlintCheck :shared:checkKotlinAbi :shared:assembleAndroidMain :shared:assembleSharedReleaseXCFramework :shared:allTests
	@sh scripts/check-ios-api.sh

lint:
	./gradlew :shared:ktlintCheck

format:
	./gradlew :shared:ktlintFormat

api:
	./gradlew :shared:updateKotlinAbi

build: build-android build-ios

build-android:
	./gradlew :shared:assembleAndroidMain

build-ios:
	./gradlew :shared:assembleSharedReleaseXCFramework

publish-local:
	./gradlew :shared:publishToMavenLocal

publish:
	$(if $(VERSION),,$(error VERSION を指定してください（例: make publish VERSION=0.3.0）))
	./gradlew :shared:publishAndroidPublicationToCodeArtifactRepository -PreleaseVersion=$(VERSION)

test:
	./gradlew :shared:allTests

clean:
	./gradlew clean
