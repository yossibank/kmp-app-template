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
- SKIE を外さない。suspend / Flow / sealed interface が ObjC の completionHandler や
  SharedKotlinx_coroutines_coreFlow として iOS 側に露出するようになる。
- iOS へは shared モジュールが XCFramework 1 枚として公開される（umbrella）。
- iOS に公開する型は data class / sealed interface / enum のみ。
- 例外を投げない。結果は sealed な型で返す。
- `internal` を既定にする。公開するのは必要なものだけ。
- バージョンは `gradle/libs.versions.toml` にのみ書く。build.gradle.kts に直書きしない。

## CI の消費側検証

共通コアの変更が両アプリを壊さないことを CI で確認している。Android は
mavenLocal 経由でビルドとユニットテスト、iOS はビルドのみ。

**iOS を `xcodebuild test` に戻すのは、`suspend` か `Flow` を公開したとき。**
いまの公開 API は文字列を返す関数だけで、SKIE の変換を通っていない。実行時
テストの追加コストは約 120 秒（シミュレータを事前起動しない場合は 337 秒）。
戻す場合は `xcrun simctl boot` を先のステップに置いて起動待ちを重ねること。

## やってはいけない

- `org.jetbrains.kotlin.android` を適用しない（AGP 9 でエラーになる）
- `com.android.library` + `androidTarget()` を使わない。
  `com.android.kotlin.multiplatform.library` と `kotlin { android { } }` を使う
- トップレベルの `android { }` ブロックを書かない
- `.gitignore` に `*.jar` を追加しない（`gradle-wrapper.jar` が消える）
- `compileSdk` を 37 未満に下げない（AndroidX の最新版が要求する）
- publish 済みのバージョンを再 publish しない（GitHub Packages は 409 を返す。必ず version を上げる）
- detekt を安易に追加しない。1.23.x は JDK 25 上で動かず、2.x は alpha しかない（ktlint のみ採用）
