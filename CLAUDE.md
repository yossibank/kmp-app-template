# kmp-app-template

## 作業の進め方

- 変更したら `make verify` を通す。
- バージョンを `gradle/libs.versions.toml` 以外で指定しない。
- `.gitignore` に `*.jar` を追加しない（`gradle-wrapper.jar` が消える）。

## コードの書き方

- **コメントを書かない。** コード、設定、スクリプト、CI のいずれにも書かない。要ると判断したら、書かずに提案する。
- **iOS に公開する関数**は例外を投げず、sealed な型で返す。

## 3 リポジトリの取り決め

- 公開 API を壊すときは、消費側 2 リポジトリを同じ作業時間に更新する。`@Deprecated` を挟んで猶予を作らない。
- 消費側を更新する順番
  1. kmp-app-template をリリースする。
  2. 消費側 2 リポジトリの PR を開く（publish されていないバージョンを pin すると解決できずに落ちる）。
