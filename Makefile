GRADLE := ./gradlew

.PHONY: verify api lint format test build build-android build-ios publish-local publish clean

verify:
	$(GRADLE) \
		:shared:ktlintCheck \
		:shared:checkKotlinAbi \
		:shared:assembleAndroidMain \
		:shared:assembleSharedDebugXCFramework \
		:shared:allTests
	@sh scripts/check-ios-api.sh

api:
	$(GRADLE) :shared:updateKotlinAbi

lint:
	$(GRADLE) :shared:ktlintCheck

format:
	$(GRADLE) :shared:ktlintFormat

test:
	$(GRADLE) :shared:allTests

build: build-android build-ios

build-android:
	$(GRADLE) :shared:assembleAndroidMain

build-ios:
	$(GRADLE) :shared:assembleSharedDebugXCFramework

publish-local:
	$(GRADLE) :shared:publishToMavenLocal

publish:
	$(if $(VERSION),,$(error VERSION を指定してください（例: make publish VERSION=0.3.0）))
	$(GRADLE) :shared:publishAndroidPublicationToCodeArtifactRepository -PreleaseVersion=$(VERSION)

clean:
	$(GRADLE) clean
