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

バージョンは [gradle/libs.versions.toml](gradle/libs.versions.toml)。

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

- コメントを書かない。コード、設定、スクリプト、CI のいずれにも書かない。
- コメントが要ると判断したときは、書かずに提案する。
- テストは `commonTest` に置く。1 度書けば両OSで実行される。
- iOS に公開する型は data class / sealed interface / enum のみ。`Flow` は公開しない（SKIE が変換せず、
  Swift 側で気づかないまま壊れる）。例外は投げず sealed な型で返す。

## 全体ルール

- 変更したら `make verify` を通す。通らないものは完了ではない。
- 公開 API を壊すときは、消費側 2 リポジトリを同じ作業時間に更新する。`@Deprecated` を挟んで
  猶予を作らない。
- 消費側の PR は release の後に開く。publish されていないバージョンを pin した状態で開くと
  解決できずに落ちる。消費側の CI は pull_request でしか走らないので、ブランチを push する
  だけなら何も起きない。
- publish 済みのバージョンを再 publish しない（GitHub Packages は 409 を返す）。
- バージョンを `gradle/libs.versions.toml` 以外で指定しない。
- `org.jetbrains.kotlin.android` を適用しない。`com.android.kotlin.multiplatform.library` を使う。
- `.gitignore` に `*.jar` を追加しない（`gradle-wrapper.jar` が消える）。
