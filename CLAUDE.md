# kmp-app-template

## プロジェクト概要

iOS / Android で共有するロジックの Kotlin Multiplatform ライブラリ。アプリ本体は含まない。
共通コアとして 2 つのアプリに配布する側で、消費側の CI を壊さない責任がある。

ios-app-template・android-app-template と合わせた 3 リポジトリ構成の 1 つ。

## 技術スタック

| 項目 | 採用 |
| --- | --- |
| 配布 | Android は AAR / klib、iOS は XCFramework 1 枚 |
| 通信 | Ktor |
| シリアライズ | kotlinx.serialization |
| 並行性 | Kotlin Coroutines |
| テスト | kotlin.test（`commonTest`） |
| 依存管理 | Gradle（バージョンカタログ） |

バージョンは [README.md](README.md)。

## プロジェクト構成

```
shared/src/commonMain/    共通ロジック
shared/src/commonTest/    両OSで実行されるテスト
shared/openapi/           モデル生成の元にする OpenAPI 定義
```

ソースセットの役割は [README.md](README.md)。

## 使用ライブラリ

| | |
| --- | --- |
| Ktor / kotlinx.serialization | HTTP とシリアライズ |
| SKIE | Swift 向けの API 変換。外さない |
| openapi-generator | モデルのみ生成。HTTP クライアントは手書き |
| ktlint | 書式のチェック |
| Renovate | 依存の更新 PR（毎週月曜） |

## コーディング規約

- コードに無駄なコメントを書かない。
- テストは `commonTest` に置く。1 度書けば両OSで実行される。
- iOS に公開する型は data class / sealed interface / enum のみ。`Flow` は公開しない（SKIE が変換せず、
  Swift 側で気づかないまま壊れる）。例外は投げず sealed な型で返す。

## 全体ルール

- 変更したら `make verify` を通す。通らないものは完了ではない。
- 公開 API を 1 リリースで壊さない。`@Deprecated` を挟み、消費側 2 リポジトリが乗り換えてから消す。
- publish 済みのバージョンを再 publish しない（GitHub Packages は 409 を返す）。
- バージョンを `gradle/libs.versions.toml` 以外で指定しない。
- `org.jetbrains.kotlin.android` を適用しない。`com.android.kotlin.multiplatform.library` を使う。
- `.gitignore` に `*.jar` を追加しない（`gradle-wrapper.jar` が消える）。
