# kmp-app-template

iOS / Android で共有するロジックの Kotlin Multiplatform ライブラリ。アプリ本体は含まない。

## 検証

変更したら必ず通す。通らないものは完了ではない。

```sh
make verify   # XCFramework のビルド + 全ターゲットのテスト
```

iOS ターゲットのビルドには Xcode が必要。`xcode-select -p` が CommandLineTools を
指している場合は `DEVELOPER_DIR` を設定する。

## 規約

- ビジネスロジックのテストは `commonTest` に置く。1 度書けば両OSで実行される。
- `expect`/`actual` は OS の薄いシムのみ（時刻・UUID・パス・鍵保管・ログ出力）。
  ビジネスロジックの分岐には使わず、interface + DI で注入する。
- iOS に公開する型は data class / sealed interface / enum のみ。
- 例外を投げない。結果は sealed な型で返す。
- `internal` を既定にする。公開するのは必要なものだけ。
- バージョンは `gradle/libs.versions.toml` にのみ書く。build.gradle.kts に直書きしない。

## やってはいけない

- `org.jetbrains.kotlin.android` を適用しない（AGP 9 でエラーになる）
- `com.android.library` + `androidTarget()` を使わない。
  `com.android.kotlin.multiplatform.library` と `kotlin { android { } }` を使う
- トップレベルの `android { }` ブロックを書かない
- `.gitignore` に `*.jar` を追加しない（`gradle-wrapper.jar` が消える）
- `compileSdk` を 37 未満に下げない（AndroidX の最新版が要求する）
