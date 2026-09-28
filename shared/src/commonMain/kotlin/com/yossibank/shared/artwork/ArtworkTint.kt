package com.yossibank.shared.artwork

data class ArtworkTint(
    val hue: Double,
    val saturation: Double,
    val brightness: Double,
) {
    companion object {
        fun of(argb: IntArray): ArtworkTint? {
            val dominant = dominantColor(argb)?.hsb() ?: return null

            return ArtworkTint(
                hue = dominant.hue,
                saturation = maxOf(dominant.saturation, 0.45),
                brightness = dominant.brightness.coerceIn(0.55, 0.9),
            )
        }
    }
}

private fun dominantColor(argb: IntArray): Rgb? {
    val weights = DoubleArray(12)
    val sums = Array(12) { Rgb(0.0, 0.0, 0.0) }

    for (pixel in argb) {
        if ((pixel ushr 24 and 0xFF) <= 127) continue

        val rgb = Rgb(
            red = (pixel ushr 16 and 0xFF) / 255.0,
            green = (pixel ushr 8 and 0xFF) / 255.0,
            blue = (pixel and 0xFF) / 255.0,
        )
        val hsb = rgb.hsb()

        if (hsb.saturation <= 0.2 || hsb.brightness <= 0.2) continue

        val index = minOf((hsb.hue * 12).toInt(), 11)
        weights[index] += hsb.saturation
        sums[index] = sums[index] + rgb * hsb.saturation
    }

    val index = weights.indices.maxBy { weights[it] }
    val weight = weights[index]

    return if (weight > 0) sums[index] * (1 / weight) else null
}

private data class Rgb(
    val red: Double,
    val green: Double,
    val blue: Double,
) {
    operator fun plus(other: Rgb) = Rgb(red + other.red, green + other.green, blue + other.blue)

    operator fun times(factor: Double) = Rgb(red * factor, green * factor, blue * factor)

    fun hsb(): Hsb {
        val maximum = maxOf(red, green, blue)
        val delta = maximum - minOf(red, green, blue)

        if (delta <= 0) return Hsb(hue = 0.0, saturation = 0.0, brightness = maximum)

        val sector = when (maximum) {
            red -> (green - blue) / delta
            green -> (blue - red) / delta + 2
            else -> (red - green) / delta + 4
        }
        val hue = (sector / 6) % 1

        return Hsb(
            hue = if (hue < 0) hue + 1 else hue,
            saturation = delta / maximum,
            brightness = maximum,
        )
    }
}

private data class Hsb(
    val hue: Double,
    val saturation: Double,
    val brightness: Double,
)
