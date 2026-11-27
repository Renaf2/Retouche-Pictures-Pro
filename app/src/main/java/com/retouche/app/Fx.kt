package com.retouche.app

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Réglages de couleur (tous entre -100 et 100, sauf netteté et vignettage : 0 à 100). */
data class Adjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val sharpness: Float = 0f,
    val vignette: Float = 0f
) {
    val colorIsIdentity: Boolean
        get() = brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f
}

enum class Filt(val label: String, val vignette: Float = 0f) {
    ORIGINAL("Original"),
    MONO("Noir & blanc"),
    SEPIA("Sépia"),
    VINTAGE("Vintage", 40f),
    WARM("Chaud"),
    COOL("Froid"),
    DRAMA("Dramatique", 45f),
    FADE("Fané"),
    VIVID("Vif"),
    NEGATIVE("Négatif")
}

/** Effets d'image : couleurs, flou, pixellisation, correcteur, contour, formes, sélection. */
object Fx {

    private val IDENTITY = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    // ---------------------------------------------------------------- Couleurs

    fun adjustMatrix(a: Adjust): FloatArray {
        val m = ColorMatrix()
        val w = a.warmth / 100f
        m.postConcat(
            ColorMatrix(
                floatArrayOf(
                    1f + 0.2f * w, 0f, 0f, 0f, 0f,
                    0f, 1f + 0.04f * w, 0f, 0f, 0f,
                    0f, 0f, 1f - 0.2f * w, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        val sat = ColorMatrix()
        sat.setSaturation((1f + a.saturation / 100f).coerceAtLeast(0f))
        m.postConcat(sat)
        val c = 1f + a.contrast / 100f
        val t = 128f * (1f - c) + a.brightness * 1.5f
        m.postConcat(
            ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        return m.array.copyOf()
    }

    fun filterMatrix(f: Filt): FloatArray = when (f) {
        Filt.ORIGINAL -> IDENTITY.copyOf()
        Filt.MONO -> adjustMatrix(Adjust(saturation = -100f, contrast = 10f))
        Filt.SEPIA -> floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        Filt.VINTAGE -> adjustMatrix(Adjust(saturation = -20f, contrast = -10f, brightness = 8f, warmth = 45f))
        Filt.WARM -> adjustMatrix(Adjust(warmth = 45f, saturation = 10f))
        Filt.COOL -> adjustMatrix(Adjust(warmth = -45f))
        Filt.DRAMA -> adjustMatrix(Adjust(contrast = 35f, saturation = -15f, brightness = -6f))
        Filt.FADE -> adjustMatrix(Adjust(contrast = -20f, brightness = 14f, saturation = -20f))
        Filt.VIVID -> adjustMatrix(Adjust(saturation = 50f, contrast = 10f))
        Filt.NEGATIVE -> floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    }

    /** Mélange entre « aucun effet » (t = 0) et l'effet complet (t = 1). */
    fun lerp(m: FloatArray, t: Float): FloatArray =
        FloatArray(20) { IDENTITY[it] + (m[it] - IDENTITY[it]) * t }

    fun applyMatrix(src: Bitmap, m: FloatArray): Bitmap {
        val out = ImageOps.fresh(src.width, src.height)
        val p = Paint()
        p.colorFilter = android.graphics.ColorMatrixColorFilter(ColorMatrix(m))
        Canvas(out).drawBitmap(src, 0f, 0f, p)
        return out
    }

    /** Assombrit les bords (uniquement sur les pixels non transparents). */
    fun drawVignette(canvas: Canvas, w: Int, h: Int, strength: Float) {
        if (strength <= 0f) return
        val cx = w / 2f
        val cy = h / 2f
        val r = hypot(cx, cy)
        val edge = ((strength / 100f).coerceIn(0f, 1f) * 0.85f * 255f).toInt()
        val shader = RadialGradient(
            cx, cy, r,
            intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(edge, 0, 0, 0)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        val p = Paint()
        p.shader = shader
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }

    // ---------------------------------------------------------------- Flou (par moyenne glissante)

    private fun premultiply(px: IntArray) {
        for (i in px.indices) {
            val p = px[i]
            val a = p ushr 24
            if (a == 255) continue
            if (a == 0) {
                px[i] = 0
                continue
            }
            val r = ((p shr 16) and 255) * a / 255
            val g = ((p shr 8) and 255) * a / 255
            val b = (p and 255) * a / 255
            px[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    private fun unpremultiply(px: IntArray) {
        for (i in px.indices) {
            val p = px[i]
            val a = p ushr 24
            if (a == 255 || a == 0) continue
            val r = min(255, ((p shr 16) and 255) * 255 / a)
            val g = min(255, ((p shr 8) and 255) * 255 / a)
            val b = min(255, (p and 255) * 255 / a)
            px[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val step = if (horizontal) 1 else w
        val div = 2 * r + 1
        for (line in 0 until lines) {
            val base = if (horizontal) line * w else line
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (k in -r..r) {
                val idx = min(max(k, 0), len - 1)
                val p = src[base + idx * step]
                sa += p ushr 24
                sr += (p shr 16) and 255
                sg += (p shr 8) and 255
                sb += p and 255
            }
            for (i in 0 until len) {
                dst[base + i * step] =
                    ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val addP = src[base + min(i + r + 1, len - 1) * step]
                val subP = src[base + max(i - r, 0) * step]
                sa += (addP ushr 24) - (subP ushr 24)
                sr += ((addP shr 16) and 255) - ((subP shr 16) and 255)
                sg += ((addP shr 8) and 255) - ((subP shr 8) and 255)
                sb += (addP and 255) - (subP and 255)
            }
        }
    }

    /** Flou « boîte » répété [passes] fois (≈ gaussien). Modifie et renvoie le tableau donné. */
    private fun boxBlurPx(px: IntArray, w: Int, h: Int, r: Int, passes: Int): IntArray {
        if (r < 1) return px
        val tmp = IntArray(px.size)
        repeat(passes) {
            boxPass(px, tmp, w, h, r, true)
            boxPass(tmp, px, w, h, r, false)
        }
        return px
    }

    /** Flou d'une image ; [radius] en pixels. Les grands flous sont calculés en basse résolution (rapide). */
    fun blurBitmap(src: Bitmap, radius: Int): Bitmap {
        val r = max(1, radius)
        val f = max(1, r / 5)
        val sw = max(1, src.width / f)
        val sh = max(1, src.height / f)
        val small = if (f == 1) src else Bitmap.createScaledBitmap(src, sw, sh, true)
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        premultiply(px)
        boxBlurPx(px, sw, sh, max(1, r / f), 3)
        unpremultiply(px)
        val bs = ImageOps.fresh(sw, sh)
        bs.setPixels(px, 0, sw, 0, 0, sw, sh)
        if (f == 1) return bs
        val out = Bitmap.createScaledBitmap(bs, src.width, src.height, true)
        if (out !== bs) bs.recycle()
        return out
    }

    /** Pixellisation en blocs de [block] pixels. */
    fun pixelate(src: Bitmap, block: Int): Bitmap {
        val b = max(2, block)
        val sw = max(1, src.width / b)
        val sh = max(1, src.height / b)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val out = Bitmap.createScaledBitmap(small, src.width, src.height, false)
        if (small !== out) small.recycle()
        return out
    }

    /** Netteté (masque flou). [amount] de 0 à 100. */
    fun sharpen(src: Bitmap, amount: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val blur = px.copyOf()
        premultiply(blur)
        boxBlurPx(blur, w, h, 2, 2)
        unpremultiply(blur)
        val k = amount / 100f * 1.5f
        for (i in px.indices) {
            val p = px[i]
            val a = p ushr 24
            if (a == 0) continue
            val q = blur[i]
            val pr = (p shr 16) and 255
            val pg = (p shr 8) and 255
            val pb = p and 255
            val r = (pr + k * (pr - ((q shr 16) and 255))).toInt().coerceIn(0, 255)
            val g = (pg + k * (pg - ((q shr 8) and 255))).toInt().coerceIn(0, 255)
            val b = (pb + k * (pb - (q and 255))).toInt().coerceIn(0, 255)
            px[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        val out = ImageOps.fresh(w, h)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    // ---------------------------------------------------------------- Contour d'un détourage

    private fun erodeLine(src: IntArray, dst: IntArray, base: Int, step: Int, len: Int, r: Int, dq: IntArray) {
        var head = 0
        var tail = 0
        var next = 0
        for (i in 0 until len) {
            val hi = min(len - 1, i + r)
            while (next <= hi) {
                val v = src[base + next * step]
                while (tail > head && src[base + dq[tail - 1] * step] >= v) tail--
                dq[tail++] = next
                next++
            }
            val lo = i - r
            while (dq[head] < lo) head++
            dst[base + i * step] = src[base + dq[head] * step]
        }
    }

    /**
     * Affine le bord d'un sujet détouré :
     * [shrink] rogne le contour de quelques pixels (retire le liseré de l'ancien fond),
     * [feather] adoucit la transition.
     */
    fun refineEdge(src: Bitmap, shrink: Int, feather: Int): Bitmap {
        val w = src.width
        val h = src.height
        val n = w * h
        val px = IntArray(n)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val alpha = IntArray(n) { px[it] ushr 24 }
        if (shrink > 0) {
            val tmp = IntArray(n)
            val dq = IntArray(max(w, h))
            for (y in 0 until h) erodeLine(alpha, tmp, y * w, 1, w, shrink, dq)
            for (x in 0 until w) erodeLine(tmp, alpha, x, w, h, shrink, dq)
        }
        if (feather > 0) {
            val packed = IntArray(n) { alpha[it] shl 24 }
            boxBlurPx(packed, w, h, feather, 2)
            for (i in 0 until n) alpha[i] = min(alpha[i], packed[i] ushr 24)
        }
        for (i in 0 until n) px[i] = (alpha[i] shl 24) or (px[i] and 0x00FFFFFF)
        val out = ImageOps.fresh(w, h)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    // ---------------------------------------------------------------- Correcteur (retire une tache)

    /**
     * Efface ce que recouvre le trait [path] (largeur [strokeWidth]) en reconstituant les pixels
     * à partir de leur voisinage (diffusion). Idéal pour les petites taches, boutons, poussières.
     */
    fun heal(src: Bitmap, path: Path, strokeWidth: Float): Bitmap? {
        val b = RectF()
        path.computeBounds(b, true)
        val pad = strokeWidth / 2f + 8f
        val l = max(0, floor(b.left - pad).toInt())
        val t = max(0, floor(b.top - pad).toInt())
        val r = min(src.width, ceil(b.right + pad).toInt())
        val bt = min(src.height, ceil(b.bottom + pad).toInt())
        val w = r - l
        val h = bt - t
        if (w < 3 || h < 3) return null

        val maskBmp = ImageOps.fresh(w, h)
        val mc = Canvas(maskBmp)
        mc.translate(-l.toFloat(), -t.toFloat())
        val mp = Paint(Paint.ANTI_ALIAS_FLAG)
        mp.style = Paint.Style.STROKE
        mp.strokeCap = Paint.Cap.ROUND
        mp.strokeJoin = Paint.Join.ROUND
        mp.strokeWidth = strokeWidth
        mp.color = Color.WHITE
        mc.drawPath(path, mp)
        val mpx = IntArray(w * h)
        maskBmp.getPixels(mpx, 0, w, 0, 0, w, h)
        maskBmp.recycle()

        val px = IntArray(w * h)
        src.getPixels(px, 0, w, l, t, w, h)
        val n = w * h
        val unknown = BooleanArray(n) { (mpx[it] ushr 24) > 0 }

        // Valeur de départ : moyenne des pixels connus de la zone.
        val avg = FloatArray(4)
        var known = 0
        for (i in 0 until n) {
            if (unknown[i]) continue
            val p = px[i]
            avg[0] += (p ushr 24).toFloat()
            avg[1] += ((p shr 16) and 255).toFloat()
            avg[2] += ((p shr 8) and 255).toFloat()
            avg[3] += (p and 255).toFloat()
            known++
        }
        if (known == 0) return null
        for (c in 0 until 4) avg[c] /= known.toFloat()

        var cur = Array(4) { FloatArray(n) }
        var nxt = Array(4) { FloatArray(n) }
        for (i in 0 until n) {
            val p = px[i]
            if (unknown[i]) {
                for (c in 0 until 4) cur[c][i] = avg[c]
            } else {
                cur[0][i] = (p ushr 24).toFloat()
                cur[1][i] = ((p shr 16) and 255).toFloat()
                cur[2][i] = ((p shr 8) and 255).toFloat()
                cur[3][i] = (p and 255).toFloat()
            }
        }
        for (c in 0 until 4) System.arraycopy(cur[c], 0, nxt[c], 0, n)

        val iterations = max(w, h).coerceIn(40, 250)
        repeat(iterations) {
            for (y in 0 until h) {
                val ym = if (y > 0) y - 1 else 0
                val yp = if (y < h - 1) y + 1 else h - 1
                for (x in 0 until w) {
                    val i = y * w + x
                    if (!unknown[i]) continue
                    val xm = if (x > 0) x - 1 else 0
                    val xp = if (x < w - 1) x + 1 else w - 1
                    val iu = ym * w + x
                    val id = yp * w + x
                    val il = y * w + xm
                    val ir = y * w + xp
                    for (c in 0 until 4) {
                        val a = cur[c]
                        nxt[c][i] = (a[iu] + a[id] + a[il] + a[ir]) * 0.25f
                    }
                }
            }
            val tmp = cur
            cur = nxt
            nxt = tmp
        }

        val out = ImageOps.copyOf(src)
        for (i in 0 until n) {
            if (!unknown[i]) continue
            val k = (mpx[i] ushr 24) / 255f
            val p = px[i]
            val a = ((p ushr 24) * (1f - k) + cur[0][i] * k).toInt().coerceIn(0, 255)
            val rr = (((p shr 16) and 255) * (1f - k) + cur[1][i] * k).toInt().coerceIn(0, 255)
            val gg = (((p shr 8) and 255) * (1f - k) + cur[2][i] * k).toInt().coerceIn(0, 255)
            val bb = ((p and 255) * (1f - k) + cur[3][i] * k).toInt().coerceIn(0, 255)
            px[i] = (a shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
        out.setPixels(px, 0, w, l, t, w, h) // le bloc est écrit à (l, t) dans l'image
        return out
    }

    // ---------------------------------------------------------------- Formes

    fun drawShape(canvas: Canvas, s: ShapeSpec) {
        val a = (s.opacity.coerceIn(0f, 1f) * 255f).toInt()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = (s.color and 0x00FFFFFF) or (a shl 24)
        p.strokeWidth = s.width
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        val fillable = s.kind == ShapeKind.RECT || s.kind == ShapeKind.ELLIPSE
        p.style = if (s.filled && fillable) Paint.Style.FILL else Paint.Style.STROKE
        when (s.kind) {
            ShapeKind.LINE -> canvas.drawLine(s.x0, s.y0, s.x1, s.y1, p)
            ShapeKind.ARROW -> {
                val path = Path()
                path.moveTo(s.x0, s.y0)
                path.lineTo(s.x1, s.y1)
                val ang = atan2(s.y1 - s.y0, s.x1 - s.x0)
                val head = max(s.width * 4f, 24f)
                val spread = 0.5f
                path.moveTo(s.x1 - head * cos(ang - spread), s.y1 - head * sin(ang - spread))
                path.lineTo(s.x1, s.y1)
                path.lineTo(s.x1 - head * cos(ang + spread), s.y1 - head * sin(ang + spread))
                canvas.drawPath(path, p)
            }
            ShapeKind.RECT -> canvas.drawRect(
                min(s.x0, s.x1), min(s.y0, s.y1), max(s.x0, s.x1), max(s.y0, s.y1), p
            )
            ShapeKind.ELLIPSE -> canvas.drawOval(
                RectF(min(s.x0, s.x1), min(s.y0, s.y1), max(s.x0, s.x1), max(s.y0, s.y1)), p
            )
        }
    }

    // ---------------------------------------------------------------- Sélection

    fun buildSelection(mode: SelMode, x0: Float, y0: Float, x1: Float, y1: Float, lasso: List<PointF>): Selection {
        val path = Path()
        when (mode) {
            SelMode.RECT -> path.addRect(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1), Path.Direction.CW)
            SelMode.ELLIPSE -> path.addOval(
                RectF(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1)), Path.Direction.CW
            )
            SelMode.LASSO -> {
                if (lasso.isNotEmpty()) {
                    path.moveTo(lasso[0].x, lasso[0].y)
                    for (i in 1 until lasso.size) path.lineTo(lasso[i].x, lasso[i].y)
                    path.close()
                }
            }
        }
        val b = RectF()
        path.computeBounds(b, true)
        return Selection(path, b)
    }

    private fun boundsRect(sel: Selection, w: Int, h: Int): Rect {
        val r = Rect(
            floor(sel.bounds.left).toInt(), floor(sel.bounds.top).toInt(),
            ceil(sel.bounds.right).toInt(), ceil(sel.bounds.bottom).toInt()
        )
        r.intersect(0, 0, w, h)
        return r
    }

    /** Copie des pixels de la sélection (image de la taille de la sélection) et sa position. */
    class Piece(val bitmap: Bitmap, val left: Int, val top: Int)

    fun copySelection(src: Bitmap, sel: Selection): Piece? {
        val r = boundsRect(sel, src.width, src.height)
        if (r.width() < 1 || r.height() < 1) return null
        val out = ImageOps.fresh(r.width(), r.height())
        val c = Canvas(out)
        c.drawBitmap(src, -r.left.toFloat(), -r.top.toFloat(), null)
        val mask = ImageOps.fresh(r.width(), r.height())
        val mc = Canvas(mask)
        mc.translate(-r.left.toFloat(), -r.top.toFloat())
        val fp = Paint(Paint.ANTI_ALIAS_FLAG)
        fp.color = Color.WHITE
        mc.drawPath(sel.path, fp)
        val dp = Paint()
        dp.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        c.drawBitmap(mask, 0f, 0f, dp)
        mask.recycle()
        return Piece(out, r.left, r.top)
    }

    fun eraseSelection(dst: Bitmap, sel: Selection) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.BLACK
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        Canvas(dst).drawPath(sel.path, p)
    }

    fun fillSelection(dst: Bitmap, sel: Selection, argb: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = argb
        Canvas(dst).drawPath(sel.path, p)
    }

    /** Remplace l'intérieur de la sélection par les pixels de [source] (même taille que [dst]). */
    fun paintSelection(dst: Bitmap, sel: Selection, source: Bitmap) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        Canvas(dst).drawPath(sel.path, p)
    }
}
