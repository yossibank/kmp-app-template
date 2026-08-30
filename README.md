# kmp-app-template

iOS / Android で共有するロジックを置く Kotlin Multiplatform の共通ライブラリ。
アプリ本体は含まない。

## 環境

| 項目 | バージョン |
| --- | --- |
| Gradle | 9.7.1 |
| Kotlin | 2.4.10 |
| Android Gradle Plugin | 9.3.2 |
| compileSdk | 37 |
| minSdk | 24 |
| iOS ターゲット | iosArm64 / iosSimulatorArm64 / iosX64 |

## 構成

```
shared/src/
├─ commonMain/   共通ロジック
├─ commonTest/   両OSで実行されるテスト
├─ androidMain/  Android 固有の実装
└─ iosMain/      iOS 固有の実装
```

## ビルド

```sh
./gradlew :shared:assembleAndroidMain        # AAR / klib
./gradlew :shared:assembleSharedXCFramework  # Shared.xcframework
./gradlew :shared:allTests                   # 全ターゲットのテスト
```

XCFramework は `shared/build/XCFrameworks/{debug,release}/` に出力される。
iOS ターゲットのビルドには Xcode が必要。

Android Studio 用の Run Configuration を `.run/` に用意している。
「All Tests」で全ターゲット、「Android Tests」「iOS Tests」で片方だけを実行できる。

## 関連リポジトリ

- [ios-app-template](https://github.com/yossibank/ios-app-template)
- [android-app-template](https://github.com/yossibank/android-app-template)
