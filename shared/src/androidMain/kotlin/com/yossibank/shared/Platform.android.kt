package com.yossibank.shared

import android.os.Build

private class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual val platform: Platform = AndroidPlatform()
