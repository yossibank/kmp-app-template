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
            url: "https://api.github.com/repos/yossibank/kmp-app-template/releases/assets/583609778.zip",
            checksum: "791eba997fb8fd9bd03c44e319c864ae91c72556f975d769eed4242109d5d9e8"
        )
    ]
)
