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
            url: "https://api.github.com/repos/yossibank/kmp-app-template/releases/assets/537251533.zip",
            checksum: "a370f8111e3881bea1b10e06f0151b993acdd65a5ee80537f64dae404082bdcf"
        )
    ]
)
