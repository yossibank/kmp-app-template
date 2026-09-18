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
            url: "https://api.github.com/repos/yossibank/kmp-app-template/releases/assets/573691362.zip",
            checksum: "4a46fdda8e1038e9e6568db54c1de5f6dbc947c5e3839d62f99f59a6e41613b0"
        )
    ]
)
