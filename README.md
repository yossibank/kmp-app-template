# kmp-app-template

iOS / Android で共有するロジックの Kotlin Multiplatform ライブラリ。
使う側は [android-app-template](https://github.com/yossibank/android-app-template) と
[ios-app-template](https://github.com/yossibank/ios-app-template)。

## 使い方

変更したら `make verify`。公開 API を変えたら `make api` で `shared/api/` を更新する。

## リリース

GitHub Actions の **Release** ワークフローにバージョン（semver）を渡して実行する。手元では `./release.sh <version>`。
リリース後に、消費側 2 リポジトリのバージョン指定を上げて PR を開く。
