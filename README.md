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

PokeAPI からの一覧取得・ページング・エラーの分類を担います。

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

## 3 つのリポジトリ

```mermaid
flowchart LR
    KMP["kmp-app-template<br/>共通ロジック"]
    AND["android-app-template<br/>Android アプリ"]
    IOS["ios-app-template<br/>iOS アプリ"]
    KMP -->|"AAR / klib<br/>GitHub Packages"| AND
    KMP -->|"Shared.xcframework<br/>GitHub Releases + SPM"| IOS
```

[android-app-template](https://github.com/yossibank/android-app-template) ・ [ios-app-template](https://github.com/yossibank/ios-app-template)

## 使い方

変更したら `make verify` を通します。

> [!NOTE]
> 公開 API は `shared/api/` にダンプしてあり、差分があると `make verify` が落ちます。API を変えたら `make api` で更新します。

<details>
<summary>リリース</summary>

GitHub Actions の **Release** ワークフローにバージョン（semver）を渡して実行します（手元では `./release.sh <version>`）。

1. XCFramework をビルドする
2. `Package.swift` を更新してタグを付ける
3. GitHub Packages へ publish する

リリース後に、アプリ側 2 リポジトリのバージョン指定を上げます。

</details>
