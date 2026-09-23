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
            url: "https://api.github.com/repos/yossibank/kmp-app-template/releases/assets/583492014.zip",
            checksum: "9658382c9a7db6e9475a600d3801476c0b0a85141dfed8bfd1de1548e748c110"
        )
    ]
)
