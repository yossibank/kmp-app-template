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
            url: "https://api.github.com/repos/yossibank/kmp-app-template/releases/assets/577789227.zip",
            checksum: "2753077c8cbed5f93c9853e6e690071faf0f7f117615f210faeb39336182d776"
        )
    ]
)
