// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "Shared",
    products: [
        .library(name: "Shared", targets: ["Shared"])
    ],
    targets: [
        .binaryTarget(
            name: "Shared",
            url: "https://api.github.com/repos/yossibank/kmp-app-template/releases/assets/583799940.zip",
            checksum: "1a2ad23514ad54cba31eafdeb8069913082a679c2b0e80472831643e9c0844a6"
        )
    ]
)
