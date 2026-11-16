package com.metrolist.android.playback

import android.content.Context
import android.graphics.Bitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.metrolist.shared.utils.canonicalArtworkUrl
import java.io.ByteArrayOutputStream

private const val EMBEDDED_ARTWORK_SIZE_PX = 320
private const val EMBEDDED_ARTWORK_MAX_BYTES = 150_000

/**
 * A small JPEG of the cover for session metadata. Controllers that cannot open our content:// artwork link (the Wear OS
 * bridge never requested it) read the bytes from the metadata instead. Kept small: very large embedded artwork has
 * crashed system UI in Media3 releases.
 */
internal suspend fun loadEmbeddedArtwork(
    context: Context,
    url: String,
): ByteArray? {
    val source = canonicalArtworkUrl(url)
    if (!source.startsWith("https://")) return null
    val result =
        context.imageLoader.execute(
            ImageRequest
                .Builder(context)
                .data(source)
                .size(EMBEDDED_ARTWORK_SIZE_PX)
                .build(),
        )
    val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return null
    return ByteArrayOutputStream().use { output ->
        if (bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) output.toByteArray().takeIf { it.size <= EMBEDDED_ARTWORK_MAX_BYTES } else null
    }
}
