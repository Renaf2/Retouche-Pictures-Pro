package com.retouche.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.nio.FloatBuffer
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Détourage automatique, 100 % hors ligne, via ONNX Runtime.
 *
 * Le modèle est lu dans app/src/main/assets/. Le premier trouvé dans cette liste est utilisé
 * (du plus précis au plus léger) : tu peux donc améliorer la qualité en ajoutant un fichier.
 */
class BgRemover(private val context: Context) {

    private val candidates = listOf(
        "isnet-general-use.onnx", // ~170 Mo, meilleure qualité (entrée 1024×1024)
        "u2net.onnx",             // ~170 Mo
        "silueta.onnx",           // ~43 Mo, bon compromis
        "u2netp.onnx"             // ~4,5 Mo, léger (livré par défaut)
    )

    fun modelName(): String? = candidates.firstOrNull { name ->
        runCatching { context.assets.open(name).close() }.isSuccess
    }

    fun remove(src: Bitmap): Bitmap {
        val name = modelName()
            ?: throw IllegalStateException("Modèle IA absent : voir le README (dossier assets).")
        val bytes = context.assets.open(name).use { it.readBytes() }
        val env = OrtEnvironment.getEnvironment()

        env.createSession(bytes, OrtSession.SessionOptions()).use { session ->
            val inName = session.inputNames.first()
            val shape = (session.inputInfo[inName]!!.info as TensorInfo).shape
            val th = if (shape.size == 4 && shape[2] > 0) shape[2].toInt() else 320
            val tw = if (shape.size == 4 && shape[3] > 0) shape[3].toInt() else 320

            val isnet = name.startsWith("isnet")
            val mean = if (isnet) floatArrayOf(0.5f, 0.5f, 0.5f) else floatArrayOf(0.485f, 0.456f, 0.406f)
            val std = if (isnet) floatArrayOf(1f, 1f, 1f) else floatArrayOf(0.229f, 0.224f, 0.225f)

            // --- Entrée : image réduite, aplatie sur blanc (si elle a déjà de la transparence)
            val small = downscaleOnWhite(src, tw, th)
            val px = IntArray(tw * th)
            small.getPixels(px, 0, tw, 0, 0, tw, th)
            small.recycle()

            var mx = 1f
            for (c in px) {
                mx = max(mx, ((c shr 16) and 255).toFloat())
                mx = max(mx, ((c shr 8) and 255).toFloat())
                mx = max(mx, (c and 255).toFloat())
            }
            val plane = tw * th
            val data = FloatArray(3 * plane)
            for (i in 0 until plane) {
                val c = px[i]
                data[i] = (((c shr 16) and 255) / mx - mean[0]) / std[0]
                data[plane + i] = (((c shr 8) and 255) / mx - mean[1]) / std[1]
                data[2 * plane + i] = ((c and 255) / mx - mean[2]) / std[2]
            }

            // --- Inférence
            val pred = FloatArray(plane)
            OnnxTensor.createTensor(
                env, FloatBuffer.wrap(data), longArrayOf(1, 3, th.toLong(), tw.toLong())
            ).use { input ->
                session.run(mapOf(inName to input)).use { result ->
                    val out = result.get(0) as OnnxTensor
                    out.floatBuffer.get(pred, 0, plane)
                }
            }

            // --- Normalisation min-max du masque
            var lo = Float.MAX_VALUE
            var hi = -Float.MAX_VALUE
            for (v in pred) {
                if (v < lo) lo = v
                if (v > hi) hi = v
            }
            val range = (hi - lo).coerceAtLeast(1e-6f)
            for (i in pred.indices) pred[i] = (pred[i] - lo) / range

            return applyMask(src, pred, tw, th)
        }
    }

    /** Applique le masque (réduit) à l'image d'origine par interpolation bilinéaire, ligne par ligne. */
    private fun applyMask(src: Bitmap, mask: FloatArray, mw: Int, mh: Int): Bitmap {
        val w = src.width
        val h = src.height
        val out = ImageOps.fresh(w, h)

        val x0 = IntArray(w)
        val x1 = IntArray(w)
        val xw = FloatArray(w)
        for (x in 0 until w) {
            val fx = ((x + 0.5f) * mw / w - 0.5f).coerceIn(0f, (mw - 1).toFloat())
            val ix = floor(fx).toInt()
            x0[x] = ix
            x1[x] = min(ix + 1, mw - 1)
            xw[x] = fx - ix
        }

        val row = IntArray(w)
        for (y in 0 until h) {
            val fy = ((y + 0.5f) * mh / h - 0.5f).coerceIn(0f, (mh - 1).toFloat())
            val iy = floor(fy).toInt()
            val iy1 = min(iy + 1, mh - 1)
            val wy = fy - iy
            val r0 = iy * mw
            val r1 = iy1 * mw

            src.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val top = mask[r0 + x0[x]] * (1f - xw[x]) + mask[r0 + x1[x]] * xw[x]
                val bot = mask[r1 + x0[x]] * (1f - xw[x]) + mask[r1 + x1[x]] * xw[x]
                val m = top * (1f - wy) + bot * wy

                // Légère accentuation des bords : fond bien nettoyé, transitions douces conservées.
                val t = ((m - 0.1f) / 0.8f).coerceIn(0f, 1f)
                val s = t * t * (3f - 2f * t)

                val c = row[x]
                val a = ((c ushr 24) * s).toInt()
                row[x] = (c and 0x00FFFFFF) or (a shl 24)
            }
            out.setPixels(row, 0, w, 0, y, w, 1)
        }
        return out
    }

    private fun downscaleOnWhite(src: Bitmap, tw: Int, th: Int): Bitmap {
        // Paliers de ÷2 pour limiter le crénelage, puis ajustement final.
        var cur = src
        while (cur.width >= tw * 2 && cur.height >= th * 2) {
            val next = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
            if (cur !== src) cur.recycle()
            cur = next
        }
        val out = ImageOps.fresh(tw, th)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawBitmap(cur, null, Rect(0, 0, tw, th), Paint(Paint.FILTER_BITMAP_FLAG))
        if (cur !== src) cur.recycle()
        return out
    }
}
