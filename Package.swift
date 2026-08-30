// swift-tools-version: 6.0
// url と checksum は共通コアの publish 時に書き換える。手で編集しないこと。
//
// XCFramework は GitHub Releases ではなく GitHub Packages(Maven) に置く。
// private リポジトリのリリースアセットは api.github.com 経由でしか取得できず、
// その URL は拡張子が無いため SPM の binaryTarget が受け付けないため。
import PackageDescription

let package = Package(
    name: "Shared",
    products: [
        .library(name: "Shared", targets: ["Shared"])
    ],
    targets: [
        .binaryTarget(
            name: "Shared",
            url: "https://maven.pkg.github.com/yossibank/kmp-app-template/com/yossibank/shared-xcframework/0.2.0/shared-xcframework-0.2.0.zip",
            checksum: "b7962b70ae7a9560f48060a554784be95331150abfd81e75cf160cd285aeaa1d"
        )
    ]
)
