# kmp-app-template

iOS / Android で共有するロジックを置く Kotlin Multiplatform の共通ライブラリ。
アプリ本体は含まない（[ios-app-template](https://github.com/yossibank/ios-app-template) /
[android-app-template](https://github.com/yossibank/android-app-template) から参照する想定）。

## 構成

| 項目 | バージョン |
| --- | --- |
| Gradle | 9.7.1 |
| Kotlin | 2.4.10 |
| Android Gradle Plugin | 9.3.2 |
| compileSdk | 37 |
| minSdk | 24 |
| iOS ターゲット | iosArm64 / iosSimulatorArm64 / iosX64 |

AGP 9 以降、KMP の Android ターゲットは `com.android.kotlin.multiplatform.library`
プラグインを使い、`kotlin { android { } }` の中で設定する。トップレベルの
`android { }` ブロックと `com.android.library` は使わない。

## モジュール

```
shared/
└─ src/
    ├─ commonMain/   Greeting.kt / Platform.kt    共通ロジックと expect 宣言
    ├─ commonTest/   GreetingTest.kt              両OSで実行されるテスト
    ├─ androidMain/  Platform.android.kt          actual 実装
    └─ iosMain/      Platform.ios.kt              actual 実装
```

`expect`/`actual` は OS の薄いシム（実行環境名、時刻、UUID、ファイルパスなど）に
限定する。ビジネスロジックの分岐には使わず、interface + DI で注入する。

## 成果物

| 対象 | 成果物 | タスク |
| --- | --- | --- |
| Android | AAR / klib | `./gradlew :shared:assembleAndroidMain` |
| iOS | `Shared.xcframework` | `./gradlew :shared:assembleSharedXCFramework` |

XCFramework は `shared/build/XCFrameworks/{debug,release}/` に出力される。

## テスト

```sh
./gradlew :shared:allTests            # 全ターゲット
./gradlew :shared:testAndroidHostTest # Android のみ
./gradlew :shared:iosSimulatorArm64Test # iOS シミュレータのみ
```

ビジネスロジックのテストは原則 `commonTest` に置く。1 度書けば両OSで実行される。

## 注意

iOS ターゲットのビルドには Xcode が必要。`xcode-select -p` が
CommandLineTools を指している場合は `DEVELOPER_DIR` を設定する。

```sh
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
```
