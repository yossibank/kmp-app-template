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
            url: "https://api.github.com/repos/yossibank/kmp-app-template/releases/assets/541280596.zip",
            checksum: "ec335f3e814b7dca95b199df1167b1efa0b343c63b73de0b2fbf717689813783"
        )
    ]
)
