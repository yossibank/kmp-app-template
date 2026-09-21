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
            url: "https://api.github.com/repos/yossibank/kmp-app-template/releases/assets/578390977.zip",
            checksum: "d041dbfa87b42b4f205b8461169102934afbe9af9f95f10583e15340ef012623"
        )
    ]
)
