package com.yossibank.shared

import platform.UIKit.UIDevice

private class IOSPlatform : Platform {
    override val name: String =
        UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual val platform: Platform = IOSPlatform()
