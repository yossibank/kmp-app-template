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
            url: "https://api.github.com/repos/yossibank/kmp-app-template/releases/assets/536743378.zip",
            checksum: "9855b2e75eca7ccf206aa4d75eddf1d0d58d0d5fcf92715168661db9dbcef543"
        )
    ]
)
