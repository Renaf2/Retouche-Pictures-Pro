package com.retouche.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuffXfermode
import kotlin.math.max
import kotlin.math.roundToInt

/** Assemble les calques. Même code pour l'aperçu à l'écran, l'enregistrement et les miniatures. */
object Compositor {

    private fun layerPaint(l: Layer, smooth: Boolean, filter: ColorFilter?): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.isFilterBitmap = smooth
        p.alpha = (l.opacity.coerceIn(0f, 1f) * 255f).roundToInt()
        l.blend.mode?.let { p.xfermode = PorterDuffXfermode(it) }
        if (filter != null) p.colorFilter = filter
        return p
    }

    /** Matrice de placement d'un calque IMAGE (null pour les autres types). */
    fun layerMatrix(l: Layer): Matrix? {
        val b = l.bitmap ?: return null
        if (l.kind != LayerKind.IMAGE) return null
        val m = Matrix()
        m.postTranslate(-b.width / 2f, -b.height / 2f)
        if (l.flipped) m.postScale(-1f, 1f)
        m.postScale(l.scale, l.scale)
        m.postRotate(l.rotation)
        m.postTranslate(l.cx, l.cy)
        return m
    }

    /** Les 4 coins (x0,y0,x1,y1,…) d'un calque IMAGE dans le repère du document. */
    fun imageCorners(l: Layer): FloatArray? {
        val b = l.bitmap ?: return null
        val m = layerMatrix(l) ?: return null
        val w = b.width.toFloat()
        val h = b.height.toFloat()
        val pts = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        m.mapPoints(pts)
        return pts
    }

    fun drawLayer(canvas: Canvas, l: Layer, smooth: Boolean = true, filter: ColorFilter? = null) {
        val p = layerPaint(l, smooth, filter)
        when (l.kind) {
            LayerKind.RASTER -> {
                val b = l.bitmap ?: return
                canvas.drawBitmap(b, 0f, 0f, p)
            }
            LayerKind.IMAGE -> {
                val b = l.bitmap ?: return
                val m = layerMatrix(l) ?: return
                canvas.drawBitmap(b, m, p)
            }
            LayerKind.TEXT -> {
                val t = l.text ?: return
                val saved = canvas.saveLayer(null, p)
                ImageOps.drawTextOn(canvas, t)
                canvas.restoreToCount(saved)
            }
        }
    }

    /**
     * Dessine tous les calques visibles, du bas vers le haut.
     * [filterLayerId] : calque auquel appliquer [filter] (aperçu des réglages/filtres).
     */
    fun drawDoc(
        canvas: Canvas,
        doc: Doc,
        smooth: Boolean = true,
        filterLayerId: Int = -1,
        filter: ColorFilter? = null
    ) {
        for (l in doc.layers) {
            if (!l.visible) continue
            drawLayer(canvas, l, smooth, if (l.id == filterLayerId) filter else null)
        }
    }

    /** Image finale (tous les calques visibles fusionnés). */
    fun flatten(doc: Doc): Bitmap {
        val out = ImageOps.fresh(doc.width, doc.height)
        drawDoc(Canvas(out), doc)
        return out
    }

    /** Couleur du pixel visible (tous calques confondus), 0 si hors du document. */
    fun pixelAt(doc: Doc, x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= doc.width || y >= doc.height) return 0
        val one = ImageOps.fresh(1, 1)
        val c = Canvas(one)
        c.translate(-x.toFloat(), -y.toFloat())
        drawDoc(c, doc)
        val px = one.getPixel(0, 0)
        one.recycle()
        return px
    }

    /** Miniature d'un calque (dans le repère du document). */
    fun thumb(doc: Doc, layer: Layer, target: Int): Bitmap {
        val s = target / max(doc.width, doc.height).toFloat()
        val w = max(1, (doc.width * s).roundToInt())
        val h = max(1, (doc.height * s).roundToInt())
        val out = ImageOps.fresh(w, h)
        val c = Canvas(out)
        c.scale(s, s)
        drawLayer(c, layer.copy(opacity = 1f, blend = Blend.NORMAL, visible = true))
        return out
    }
}
