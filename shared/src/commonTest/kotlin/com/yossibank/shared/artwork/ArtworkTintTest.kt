package com.yossibank.shared.artwork

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun argb(
    alpha: Int,
    red: Int,
    green: Int,
    blue: Int,
): Int = (alpha shl 24) or (red shl 16) or (green shl 8) or blue

private val RED = argb(255, 230, 40, 40)
private val BLUE = argb(255, 40, 60, 230)

class ArtworkTintTest {
    @Test
    fun the_tint_follows_the_color_that_covers_the_most() {
        val tint = assertNotNull(ArtworkTint.of(IntArray(30) { RED } + IntArray(10) { BLUE }))

        assertTrue(tint.hue < 0.05 || tint.hue > 0.95, "多い色ではなく少ない色に寄っている: ${tint.hue}")
    }

    @Test
    fun transparent_pixels_do_not_count() {
        val tint = assertNotNull(ArtworkTint.of(IntArray(30) { argb(0, 230, 40, 40) } + IntArray(10) { BLUE }))

        assertTrue(tint.hue in 0.6..0.7, "透明な画素が数えられている: ${tint.hue}")
    }

    @Test
    fun an_image_without_a_vivid_color_has_no_tint() {
        assertNull(ArtworkTint.of(IntArray(40) { argb(255, 128, 128, 128) }), "灰色から色を作っている")
        assertNull(ArtworkTint.of(IntArray(0)))
    }

    @Test
    fun the_tint_is_kept_readable_on_a_card() {
        val dark = assertNotNull(ArtworkTint.of(IntArray(10) { argb(255, 70, 20, 20) }))
        val pale = assertNotNull(ArtworkTint.of(IntArray(10) { argb(255, 255, 190, 190) }))

        assertEquals(0.55, dark.brightness, "暗すぎる色がそのまま出ている")
        assertEquals(0.45, pale.saturation, "淡すぎる色がそのまま出ている")
        assertEquals(0.9, pale.brightness, "明るすぎる色がそのまま出ている")
    }
}
