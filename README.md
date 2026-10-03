<div align="center">

# kmp-app-template

iOS と Android で共有するロジックを Kotlin Multiplatform で書いたライブラリ

[![Verify](https://github.com/yossibank/kmp-app-template/actions/workflows/verify.yml/badge.svg)](https://github.com/yossibank/kmp-app-template/actions/workflows/verify.yml)
[![Release](https://img.shields.io/github/v/release/yossibank/kmp-app-template)](https://github.com/yossibank/kmp-app-template/releases/latest)
[![License](https://img.shields.io/github/license/yossibank/kmp-app-template)](LICENSE)

![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin_Multiplatform-7F52FF?logo=kotlin&logoColor=white)
![Ktor](https://img.shields.io/badge/Ktor-087CFA?logo=ktor&logoColor=white)
![kotlinx.serialization](https://img.shields.io/badge/kotlinx.serialization-7F52FF?logo=kotlin&logoColor=white)
![SKIE](https://img.shields.io/badge/SKIE-555555)
![OpenAPI](https://img.shields.io/badge/OpenAPI-6BA539?logo=openapiinitiative&logoColor=white)

</div>

DummyJSON へのログインとトークンの保存・更新、商品一覧の取得・ページング、エラーの分類を担います。

<div align="center">

<table>
  <tr>
    <th>iOS（SwiftUI）</th>
    <th>Android（Jetpack Compose）</th>
  </tr>
  <tr>
    <td>
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="docs/images/ios-list-dark.png">
        <img src="docs/images/ios-list-light.png" width="260" alt="iOS の一覧">
      </picture>
    </td>
    <td>
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="docs/images/android-list-dark.png">
        <img src="docs/images/android-list-light.png" width="260" alt="Android の一覧">
      </picture>
    </td>
  </tr>
</table>

</div>

## 3 つのリポジトリ

```mermaid
flowchart LR
    KMP["kmp-app-template<br/>共通ロジック"]
    AND["android-app-template<br/>Android アプリ"]
    IOS["ios-app-template<br/>iOS アプリ"]
    KMP -->|"AAR<br/>AWS CodeArtifact"| AND
    KMP -->|"Shared.xcframework<br/>GitHub Releases + SPM"| IOS
```

[android-app-template](https://github.com/yossibank/android-app-template) ・ [ios-app-template](https://github.com/yossibank/ios-app-template)

## 使い方

変更したら `make verify` を通します。既存のアプリに組み込む手順は [docs/integration.md](docs/integration.md) にあります。

> [!NOTE]
> 公開 API は `shared/api/` にダンプしてあり、差分があると `make verify` が落ちます。API を変えたら `make api` で更新します。

<details>
<summary>テンプレートから作ったとき</summary>

パッケージの接頭辞と GitHub のオーナーを置き換えます。3 つのリポジトリそれぞれで実行します。

```sh
scripts/rename.sh <GitHub のオーナー> <パッケージの接頭辞>    # 例: scripts/rename.sh acme com.acme
```

</details>

<details>
<summary>リリース</summary>

GitHub Actions の **Release** ワークフローにバージョン（semver）を渡して実行します（手元では AWS にログインしたうえで `./release.sh <version>`）。

1. XCFramework をビルドする
2. `Package.swift` に URL とチェックサムを書いたコミットを作り、タグだけを push する（main は変えない）
3. Android 向けの AAR を AWS CodeArtifact へ publish する
4. アプリ側 2 リポジトリに、バージョンを上げる PR を開く

</details>
