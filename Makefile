.PHONY: verify build build-android build-ios publish-local publish-github test clean

# 変更後に必ず通すもの。iOS ターゲットのビルドには Xcode が必要。
verify:
	./gradlew :shared:assembleSharedXCFramework :shared:allTests

build: build-android build-ios

build-android:
	./gradlew :shared:assembleAndroidMain

build-ios:
	./gradlew :shared:assembleSharedXCFramework

# アプリ側から参照できるよう mavenLocal へ publish する。
publish-local:
	./gradlew :shared:publishToMavenLocal

# GitHub Packages へ publish する。同一バージョンの上書きは 409 で拒否される。
publish-github:
	./gradlew :shared:publishAllPublicationsToGitHubPackagesRepository

test:
	./gradlew :shared:allTests

clean:
	./gradlew clean
