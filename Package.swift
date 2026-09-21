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
            url: "https://api.github.com/repos/yossibank/kmp-app-template/releases/assets/578234711.zip",
            checksum: "4bc3f21ec0daf99cce82267950b18ba0ec4eb3c3f536d1afa91b0012e1cca5b0"
        )
    ]
)
