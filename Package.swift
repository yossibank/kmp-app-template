// swift-tools-version: 6.0
// このファイルは release.sh が生成する。手で編集しないこと。
import PackageDescription

let package = Package(
    name: "Shared",
    products: [
        .library(name: "Shared", targets: ["Shared"])
    ],
    targets: [
        .binaryTarget(
            name: "Shared",
            url: "https://api.github.com/repos/yossibank/kmp-app-template/releases/assets/554469806.zip",
            checksum: "4e41d802c7992a0fea23e4a7a483b2fad84759f354e328341db9ba3182779ea0"
        )
    ]
)
