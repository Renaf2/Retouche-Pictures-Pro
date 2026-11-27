package com.retouche.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import kotlin.math.max

enum class SaveFormat(val label: String, val mime: String, val ext: String) {
    PNG("PNG", "image/png", "png"),
    JPEG("JPEG", "image/jpeg", "jpg"),
    WEBP("WebP", "image/webp", "webp")
}

object ImageIO {

    /** Taille maximale (côté le plus long) à l'import, pour garder l'édition fluide. */
    const val MAX_SIDE = 4096

    fun load(ctx: Context, uri: Uri, maxSide: Int = MAX_SIDE): Bitmap {
        val cr = ctx.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = cr.openInputStream(uri) ?: throw IOException("Fichier illisible")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) } // renvoie null en mode « bornes seules »
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Ce n'est pas une image")

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide * 3 / 2) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException("Décodage impossible")

        val orientation = try {
            cr.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        bmp = applyExif(bmp, orientation)

        val longest = max(bmp.width, bmp.height)
        if (longest > maxSide) {
            val s = maxSide / longest.toFloat()
            val scaled = Bitmap.createScaledBitmap(
                bmp, max(1, (bmp.width * s).toInt()), max(1, (bmp.height * s).toInt()), true
            )
            if (scaled !== bmp) bmp.recycle()
            bmp = scaled
        }
        return if (bmp.isMutable && bmp.config == Bitmap.Config.ARGB_8888) bmp
        else ImageOps.copyOf(bmp).also { bmp.recycle() }
    }

    private fun applyExif(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.setRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.setRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.setRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                m.setRotate(90f)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                m.setRotate(-90f)
                m.postScale(-1f, 1f)
            }
            else -> return src
        }
        val out = ImageOps.applyMatrix(src, m)
        src.recycle()
        return out
    }

    /** Écrit l'image dans l'URI choisie par l'utilisateur (sélecteur de fichiers Android). */
    fun save(ctx: Context, uri: Uri, bmp: Bitmap, fmt: SaveFormat, quality: Int) {
        // JPEG ne gère pas la transparence : on aplatit sur fond blanc.
        val flat = if (fmt == SaveFormat.JPEG) {
            ImageOps.fresh(bmp.width, bmp.height).also {
                val c = Canvas(it)
                c.drawColor(Color.WHITE)
                c.drawBitmap(bmp, 0f, 0f, null)
            }
        } else bmp

        try {
            val out = ctx.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Impossible d'écrire le fichier")
            out.use {
                val q = quality.coerceIn(1, 100)
                val ok = when (fmt) {
                    SaveFormat.PNG -> flat.compress(Bitmap.CompressFormat.PNG, 100, it)
                    SaveFormat.JPEG -> flat.compress(Bitmap.CompressFormat.JPEG, q, it)
                    SaveFormat.WEBP ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            flat.compress(
                                if (q >= 100) Bitmap.CompressFormat.WEBP_LOSSLESS
                                else Bitmap.CompressFormat.WEBP_LOSSY,
                                q, it
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            flat.compress(Bitmap.CompressFormat.WEBP, q, it)
                        }
                }
                if (!ok) throw IOException("Échec de l'encodage")
            }
        } finally {
            if (flat !== bmp) flat.recycle()
        }
    }
}
