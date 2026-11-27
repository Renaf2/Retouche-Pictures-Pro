package com.retouche.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Opérations qui touchent tous les calques à la fois (recadrage, rotation, taille…). */
object DocOps {

    /** Transforme un calque IMAGE ou TEXT en pixels de la taille du document. */
    fun rasterize(doc: Doc, l: Layer): Layer {
        if (l.kind == LayerKind.RASTER && l.bitmap != null) return l
        val out = ImageOps.fresh(doc.width, doc.height)
        Compositor.drawLayer(Canvas(out), l.copy(opacity = 1f, blend = Blend.NORMAL, visible = true))
        return l.copy(
            kind = LayerKind.RASTER, bitmap = out, text = null,
            cx = 0f, cy = 0f, scale = 1f, rotation = 0f, flipped = false
        )
    }

    /** Un texte devient une image libre (nécessaire avant rotation / miroir). */
    fun textToImage(l: Layer): Layer {
        val t = l.text ?: return l
        val pad = ceil(t.size / 6f).toInt() + 4
        val b = ImageOps.textBounds(t)
        val w = max(1, ceil(b.width()).toInt() + pad * 2)
        val h = max(1, ceil(b.height()).toInt() + pad * 2)
        val bmp = ImageOps.fresh(w, h)
        ImageOps.drawTextOn(Canvas(bmp), t.copy(x = pad.toFloat(), y = pad.toFloat()))
        return l.copy(
            kind = LayerKind.IMAGE, bitmap = bmp, text = null,
            cx = t.x - pad + w / 2f, cy = t.y - pad + h / 2f,
            scale = 1f, rotation = 0f, flipped = false
        )
    }

    private fun mapBitmaps(l: Layer, f: (Bitmap) -> Bitmap): Layer =
        l.copy(bitmap = l.bitmap?.let(f), backup = l.backup?.let(f))

    fun crop(doc: Doc, rect: Rect): Doc {
        val r = Rect(rect)
        if (!r.intersect(0, 0, doc.width, doc.height) || r.width() < 1 || r.height() < 1) {
            throw IllegalArgumentException("Zone de recadrage vide")
        }
        val layers = doc.layers.map { l ->
            when (l.kind) {
                LayerKind.RASTER -> mapBitmaps(l) { ImageOps.crop(it, r) }
                LayerKind.IMAGE -> l.copy(cx = l.cx - r.left, cy = l.cy - r.top)
                LayerKind.TEXT -> l.copy(text = l.text?.let { it.copy(x = it.x - r.left, y = it.y - r.top) })
            }
        }
        return doc.copy(width = r.width(), height = r.height(), layers = layers)
    }

    fun rotate90(doc: Doc, clockwise: Boolean): Doc {
        val w = doc.width.toFloat()
        val h = doc.height.toFloat()
        val layers = doc.layers.map { src ->
            val l = if (src.kind == LayerKind.TEXT) textToImage(src) else src
            when (l.kind) {
                LayerKind.RASTER -> mapBitmaps(l) { ImageOps.rotate90(it, clockwise) }
                else ->
                    if (clockwise) l.copy(cx = h - l.cy, cy = l.cx, rotation = l.rotation + 90f)
                    else l.copy(cx = l.cy, cy = w - l.cx, rotation = l.rotation - 90f)
            }
        }
        return doc.copy(width = doc.height, height = doc.width, layers = layers)
    }

    fun flip(doc: Doc, horizontal: Boolean): Doc {
        val w = doc.width.toFloat()
        val h = doc.height.toFloat()
        val layers = doc.layers.map { src ->
            val l = if (src.kind == LayerKind.TEXT) textToImage(src) else src
            when (l.kind) {
                LayerKind.RASTER -> mapBitmaps(l) { ImageOps.flip(it, horizontal) }
                else ->
                    if (horizontal) l.copy(cx = w - l.cx, rotation = -l.rotation, flipped = !l.flipped)
                    else l.copy(cy = h - l.cy, rotation = 180f - l.rotation, flipped = !l.flipped)
            }
        }
        return doc.copy(layers = layers)
    }

    /** Redimensionne le document et tous ses calques. */
    fun resize(doc: Doc, tw: Int, th: Int, mode: FitMode, bg: Int, newId: () -> Int): Doc {
        val sw = doc.width
        val sh = doc.height
        // L'étirement non uniforme ne s'applique qu'à des pixels : on rastérise les calques libres.
        val prepared = doc.layers.map { l ->
            if (mode == FitMode.STRETCH && l.kind != LayerKind.RASTER) rasterize(doc, l) else l
        }
        var s = 1f
        var ox = 0f
        var oy = 0f
        if (mode == FitMode.FIT) {
            s = min(tw / sw.toFloat(), th / sh.toFloat())
            ox = (tw - sw * s) / 2f
            oy = (th - sh * s) / 2f
        } else if (mode == FitMode.FILL) {
            s = max(tw / sw.toFloat(), th / sh.toFloat())
            ox = (tw - sw * s) / 2f
            oy = (th - sh * s) / 2f
        }
        val layers = prepared.map { l ->
            when (l.kind) {
                LayerKind.RASTER -> mapBitmaps(l) { ImageOps.resizeTo(it, tw, th, mode, 0) }
                LayerKind.IMAGE -> l.copy(cx = l.cx * s + ox, cy = l.cy * s + oy, scale = l.scale * s)
                LayerKind.TEXT -> l.copy(
                    text = l.text?.let { it.copy(x = it.x * s + ox, y = it.y * s + oy, size = it.size * s) }
                )
            }
        }.toMutableList()
        var active = doc.active
        if (mode == FitMode.FIT && Color.alpha(bg) > 0) {
            val fond = ImageOps.fresh(tw, th)
            Canvas(fond).drawColor(bg)
            layers.add(0, Layer(newId(), "Fond", LayerKind.RASTER, fond))
            active += 1
        }
        return Doc(tw, th, layers, active)
    }
}
