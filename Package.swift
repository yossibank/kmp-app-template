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
            url: "https://api.github.com/repos/yossibank/kmp-app-template/releases/assets/536608783.zip",
            checksum: "a36ed3d8d13a184fbed19b41f7037df7eca8fdf599cc70b9ff22945f2cc8b744"
        )
    ]
)
