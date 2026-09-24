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
            url: "https://api.github.com/repos/yossibank/kmp-app-template/releases/assets/585296803.zip",
            checksum: "f614581792861fc190ef58e963cb1eb873cb60f7ea047318be1caf63d258f42c"
        )
    ]
)
