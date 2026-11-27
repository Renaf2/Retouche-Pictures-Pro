package com.retouche.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class FitMode(val label: String, val help: String) {
    FIT("Ajuster", "image entière, des marges sont ajoutées"),
    FILL("Remplir", "recadre au centre pour couvrir toute la surface"),
    STRETCH("Étirer", "déforme l'image pour remplir")
}

data class TextSpec(
    val text: String,
    val x: Float,
    val y: Float,
    val size: Float,
    val color: Int,
    val font: String = "sans-serif",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val outline: Boolean = false,
    val shadow: Boolean = false
)

/** Image ajoutée par-dessus l'image principale, avant validation (position = centre, en pixels de l'image). */
data class OverlaySpec(
    val bitmap: Bitmap,
    val cx: Float,
    val cy: Float,
    val scale: Float,
    val rotation: Float,
    val alpha: Float = 1f,
    val flipped: Boolean = false
)

enum class BrushType(val label: String) {
    PENCIL("Crayon"),
    PEN("Stylo"),
    MARKER("Feutre"),
    HIGHLIGHTER("Surligneur"),
    AIRBRUSH("Aérographe")
}

/** Trait de pinceau/gomme : un seul Path, donc l'opacité reste uniforme même si le trait se recoupe. */
class StrokeBuilder(val paint: Paint, private val size: Float) {
    val path = Path()
    private var lx = 0f
    private var ly = 0f
    private val bounds = RectF()

    fun start(x: Float, y: Float) {
        path.moveTo(x, y)
        path.lineTo(x + 0.01f, y) // un simple appui dessine un point
        lx = x
        ly = y
        bounds.set(x, y, x, y)
    }

    fun move(x: Float, y: Float) {
        path.quadTo(lx, ly, (lx + x) / 2f, (ly + y) / 2f)
        lx = x
        ly = y
        bounds.union(x, y)
    }

    fun end() {
        path.lineTo(lx, ly)
    }

    /** Zone à redessiner (limitée à l'image). */
    fun dirty(w: Int, h: Int): Rect {
        val pad = size / 2f + 3f
        val r = Rect(
            floor(bounds.left - pad).toInt(),
            floor(bounds.top - pad).toInt(),
            ceil(bounds.right + pad).toInt(),
            ceil(bounds.bottom + pad).toInt()
        )
        return if (r.intersect(0, 0, w, h)) r else Rect(0, 0, 0, 0)
    }
}

object ImageOps {

    /** Remplace les pixels (au lieu de les mélanger) : sert à restaurer la base sous un trait. */
    val SRC_PAINT: Paint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }

    fun fresh(w: Int, h: Int): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    fun copyOf(b: Bitmap): Bitmap =
        b.copy(Bitmap.Config.ARGB_8888, true) ?: throw OutOfMemoryError("copie impossible")

    // ---------------------------------------------------------------- Pinceau / gomme

    /** Largeur réellement touchée autour du trait (sert à limiter la zone redessinée). */
    fun strokeReach(type: BrushType, size: Float, eraser: Boolean): Float = when {
        eraser -> size
        type == BrushType.HIGHLIGHTER -> size * 1.5f
        type == BrushType.AIRBRUSH -> size * 1.8f
        else -> size
    }

    /** Petite texture de grain (couleur du trait, transparence aléatoire) pour l'effet crayon. */
    private fun grainTile(argb: Int): Bitmap {
        val n = 64
        val rnd = java.util.Random(7)
        val rgb = argb and 0x00FFFFFF
        val px = IntArray(n * n) {
            val a = if (rnd.nextFloat() < 0.35f) 0 else 120 + rnd.nextInt(136)
            rgb or (a shl 24)
        }
        return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }

    fun strokePaint(
        argb: Int, size: Float, opacity: Float, eraser: Boolean,
        type: BrushType = BrushType.PEN
    ): Paint {
        val a = (opacity.coerceIn(0f, 1f) * 255f).roundToInt()
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = size
            if (eraser) {
                color = Color.BLACK
                alpha = a
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            } else {
                color = (argb and 0x00FFFFFF) or (a shl 24)
                when (type) {
                    BrushType.PEN -> {}
                    BrushType.PENCIL -> {
                        shader = android.graphics.BitmapShader(
                            grainTile(argb),
                            android.graphics.Shader.TileMode.REPEAT,
                            android.graphics.Shader.TileMode.REPEAT
                        )
                        alpha = a
                    }
                    BrushType.MARKER -> {
                        strokeCap = Paint.Cap.SQUARE
                        alpha = (a * 0.9f).roundToInt()
                    }
                    BrushType.HIGHLIGHTER -> {
                        strokeCap = Paint.Cap.SQUARE
                        strokeWidth = size * 1.5f
                        alpha = (a * 0.4f).roundToInt()
                    }
                    BrushType.AIRBRUSH -> {
                        maskFilter = android.graphics.BlurMaskFilter(
                            max(1f, size * 0.4f), android.graphics.BlurMaskFilter.Blur.NORMAL
                        )
                        alpha = (a * 0.8f).roundToInt()
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- Régions (remplissage / baguette)

    private class IntStack {
        private var a = IntArray(1024)
        var size = 0
        fun push(v: Int) {
            if (size == a.size) a = a.copyOf(size * 2)
            a[size++] = v
        }
        fun pop(): Int = a[--size]
    }

    /** Deux pixels sont « proches » si chaque canal (ARGB) diffère d'au plus [tol]. */
    private fun similar(a: Int, b: Int, tol: Int): Boolean =
        abs((a ushr 24) - (b ushr 24)) <= tol &&
            abs(((a shr 16) and 255) - ((b shr 16) and 255)) <= tol &&
            abs(((a shr 8) and 255) - ((b shr 8) and 255)) <= tol &&
            abs((a and 255) - (b and 255)) <= tol

    /**
     * Masque des pixels proches de celui en (sx, sy).
     * contiguous = true : uniquement la zone connectée (remplissage « seau »).
     * contiguous = false : toute l'image (utile pour retirer un fond qui passe derrière des détails).
     */
    fun region(
        px: IntArray, w: Int, h: Int, sx: Int, sy: Int, tol: Int, contiguous: Boolean
    ): BooleanArray {
        val target = px[sy * w + sx]
        val mask = BooleanArray(w * h)
        if (!contiguous) {
            for (i in px.indices) mask[i] = similar(px[i], target, tol)
            return mask
        }
        val stack = IntStack()
        stack.push(sy * w + sx)
        while (stack.size > 0) {
            val p = stack.pop()
            if (mask[p]) continue
            val y = p / w
            val x = p - y * w
            val row = y * w
            var l = x
            while (l > 0 && !mask[row + l - 1] && similar(px[row + l - 1], target, tol)) l--
            var r = x
            while (r < w - 1 && !mask[row + r + 1] && similar(px[row + r + 1], target, tol)) r++
            for (i in l..r) mask[row + i] = true
            for (k in 0..1) {
                val ny = if (k == 0) y - 1 else y + 1
                if (ny < 0 || ny >= h) continue
                val nrow = ny * w
                var inSpan = false
                for (i in l..r) {
                    val ok = !mask[nrow + i] && similar(px[nrow + i], target, tol)
                    if (ok && !inSpan) {
                        stack.push(nrow + i)
                        inSpan = true
                    } else if (!ok) {
                        inSpan = false
                    }
                }
            }
        }
        return mask
    }

    /** Pot de peinture. Renvoie null si le point est hors de l'image. */
    fun fill(src: Bitmap, x: Int, y: Int, argb: Int, tol: Int): Bitmap? {
        val w = src.width
        val h = src.height
        if (x !in 0 until w || y !in 0 until h) return null
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val mask = region(px, w, h, x, y, tol, true)
        for (i in px.indices) if (mask[i]) px[i] = argb
        val out = fresh(w, h)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Baguette magique : rend transparente la zone de couleur proche du pixel touché.
     * Les pixels en bordure de zone sont atténués de moitié pour éviter l'effet « escalier ».
     */
    fun makeTransparent(src: Bitmap, x: Int, y: Int, tol: Int, contiguous: Boolean): Bitmap? {
        val w = src.width
        val h = src.height
        if (x !in 0 until w || y !in 0 until h) return null
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val mask = region(px, w, h, x, y, tol, contiguous)

        val edge = BooleanArray(w * h)
        for (yy in 0 until h) {
            for (xx in 0 until w) {
                val i = yy * w + xx
                if (mask[i]) continue
                if ((xx > 0 && mask[i - 1]) || (xx < w - 1 && mask[i + 1]) ||
                    (yy > 0 && mask[i - w]) || (yy < h - 1 && mask[i + w])
                ) edge[i] = true
            }
        }
        for (i in px.indices) {
            if (mask[i]) {
                px[i] = 0
            } else if (edge[i]) {
                val a = (px[i] ushr 24) / 2
                px[i] = (px[i] and 0x00FFFFFF) or (a shl 24)
            }
        }
        val out = fresh(w, h)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    // ---------------------------------------------------------------- Géométrie

    fun crop(src: Bitmap, r: Rect): Bitmap {
        val rr = Rect(r)
        if (!rr.intersect(0, 0, src.width, src.height) || rr.width() < 1 || rr.height() < 1) {
            throw IllegalArgumentException("Zone de recadrage vide")
        }
        val out = fresh(rr.width(), rr.height())
        Canvas(out).drawBitmap(src, rr, Rect(0, 0, rr.width(), rr.height()), null)
        return out
    }

    /** Applique une matrice et recale le résultat en (0,0). */
    fun applyMatrix(src: Bitmap, m: Matrix): Bitmap {
        val r = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
        m.mapRect(r)
        val mm = Matrix(m)
        mm.postTranslate(-r.left, -r.top)
        val out = fresh(r.width().roundToInt(), r.height().roundToInt())
        Canvas(out).drawBitmap(src, mm, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    fun rotate90(src: Bitmap, clockwise: Boolean): Bitmap =
        applyMatrix(src, Matrix().apply { setRotate(if (clockwise) 90f else -90f) })

    fun flip(src: Bitmap, horizontal: Boolean): Bitmap =
        applyMatrix(src, Matrix().apply { if (horizontal) setScale(-1f, 1f) else setScale(1f, -1f) })

    /** Supprime les bords entièrement transparents. Renvoie null s'il n'y a rien à rogner. */
    fun trimTransparent(src: Bitmap): Bitmap? {
        val w = src.width
        val h = src.height
        val row = IntArray(w)
        var top = h
        var bottom = -1
        var left = w
        var right = -1
        for (y in 0 until h) {
            src.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                if ((row[x] ushr 24) > 8) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    bottom = y
                }
            }
        }
        if (bottom < 0) return null
        if (left == 0 && top == 0 && right == w - 1 && bottom == h - 1) return null
        return crop(src, Rect(left, top, right + 1, bottom + 1))
    }

    // ---------------------------------------------------------------- Redimensionnement

    /** Réduction par paliers de ÷2 puis ajustement final : évite le crénelage des grosses réductions. */
    fun scaleSmooth(src: Bitmap, tw: Int, th: Int): Bitmap {
        var cur = src
        while (cur.width >= tw * 2 && cur.height >= th * 2) {
            val next = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
            if (cur !== src) cur.recycle()
            cur = next
        }
        val out = if (cur.width == tw && cur.height == th) cur
        else Bitmap.createScaledBitmap(cur, tw, th, true)
        if (cur !== src && out !== cur) cur.recycle()
        return out
    }

    private fun drawScaled(canvas: Canvas, src: Bitmap, x: Int, y: Int, w: Int, h: Int) {
        val scaled = scaleSmooth(src, w, h)
        canvas.drawBitmap(scaled, x.toFloat(), y.toFloat(), null)
        if (scaled !== src) scaled.recycle()
    }

    /**
     * Redimensionne vers tw×th.
     * FIT : image entière centrée, fond [bg] autour (0 = transparent).
     * FILL : l'image couvre toute la surface, l'excédent est rogné au centre.
     * STRETCH : déformation.
     */
    fun resizeTo(src: Bitmap, tw: Int, th: Int, mode: FitMode, bg: Int): Bitmap {
        require(tw in 1..16384 && th in 1..16384) { "Dimensions invalides" }
        val out = fresh(tw, th)
        val canvas = Canvas(out)
        if (Color.alpha(bg) > 0) canvas.drawColor(bg)
        val sw = src.width
        val sh = src.height
        when (mode) {
            FitMode.STRETCH -> drawScaled(canvas, src, 0, 0, tw, th)
            FitMode.FIT -> {
                val s = min(tw / sw.toFloat(), th / sh.toFloat())
                val dw = max(1, (sw * s).roundToInt())
                val dh = max(1, (sh * s).roundToInt())
                drawScaled(canvas, src, (tw - dw) / 2, (th - dh) / 2, dw, dh)
            }
            FitMode.FILL -> {
                val s = max(tw / sw.toFloat(), th / sh.toFloat())
                val cw = min(sw, max(1, (tw / s).roundToInt()))
                val ch = min(sh, max(1, (th / s).roundToInt()))
                val l = (sw - cw) / 2
                val t = (sh - ch) / 2
                val region = crop(src, Rect(l, t, l + cw, t + ch))
                drawScaled(canvas, region, 0, 0, tw, th)
                region.recycle()
            }
        }
        return out
    }

    // ---------------------------------------------------------------- Texte

    fun textPaint(spec: TextSpec): Paint {
        val style = when {
            spec.bold && spec.italic -> Typeface.BOLD_ITALIC
            spec.bold -> Typeface.BOLD
            spec.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = spec.size
            color = spec.color
            typeface = Typeface.create(spec.font, style)
        }
    }

    /** (x, y) = coin supérieur gauche du bloc de texte. Même code pour l'aperçu et le rendu final. */
    fun drawTextOn(canvas: Canvas, spec: TextSpec) {
        val p = textPaint(spec)
        val lh = p.fontSpacing
        val lines = spec.text.split('\n')
        if (spec.outline) {
            val lum = (0.299f * Color.red(spec.color) + 0.587f * Color.green(spec.color) +
                0.114f * Color.blue(spec.color)) / 255f
            val o = Paint(p)
            o.style = Paint.Style.STROKE
            o.strokeWidth = max(2f, spec.size / 8f)
            o.strokeJoin = Paint.Join.ROUND
            o.color = if (lum > 0.5f) Color.BLACK else Color.WHITE
            lines.forEachIndexed { i, line ->
                canvas.drawText(line, spec.x, spec.y - p.ascent() + i * lh, o)
            }
        }
        if (spec.shadow) {
            p.setShadowLayer(max(2f, spec.size / 10f), spec.size / 16f, spec.size / 16f, 0x99000000.toInt())
        }
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, spec.x, spec.y - p.ascent() + i * lh, p)
        }
    }

    fun textBounds(spec: TextSpec): RectF {
        val p = textPaint(spec)
        val lines = spec.text.split('\n')
        val w = lines.maxOf { p.measureText(it) }
        return RectF(spec.x, spec.y, spec.x + w, spec.y + p.fontSpacing * lines.size)
    }

    fun drawText(src: Bitmap, spec: TextSpec): Bitmap {
        val out = copyOf(src)
        drawTextOn(Canvas(out), spec)
        return out
    }

    // ---------------------------------------------------------------- Pinceaux « source » (tampon, flou, restaurer…)

    /**
     * Pinceau qui peint avec les pixels d'une autre image [source], décalés de (dx, dy) :
     * le pixel dessiné en p reprend celui de source en p + (dx, dy). Les pixels sont remplacés
     * (et non superposés), l'opacité réglant le mélange avec l'existant.
     */
    fun sourcePaint(source: Bitmap, dx: Float, dy: Float, size: Float, opacity: Float, soft: Boolean): Paint {
        val a = (opacity.coerceIn(0f, 1f) * 255f).roundToInt()
        val shader = android.graphics.BitmapShader(
            source, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP
        )
        val m = Matrix()
        m.setTranslate(-dx, -dy)
        shader.setLocalMatrix(m)
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = size
            this.shader = shader
            alpha = a
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
            if (soft) maskFilter = android.graphics.BlurMaskFilter(
                max(1f, size * 0.25f), android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        }
    }

    fun sourceReach(size: Float): Float = size * 1.6f

    /** Rectangle englobant les pixels non transparents de l'image (null si tout est transparent). */
    fun contentBounds(src: Bitmap): Rect? {
        val w = src.width
        val h = src.height
        val row = IntArray(w)
        var top = h
        var bottom = -1
        var left = w
        var right = -1
        for (y in 0 until h) {
            src.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                if ((row[x] ushr 24) > 8) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    bottom = y
                }
            }
        }
        if (bottom < 0) return null
        return Rect(left, top, right + 1, bottom + 1)
    }

    // ---------------------------------------------------------------- Image sur image

    fun overlayMatrix(o: OverlaySpec): Matrix = Matrix().apply {
        postTranslate(-o.bitmap.width / 2f, -o.bitmap.height / 2f)
        if (o.flipped) postScale(-1f, 1f)
        postScale(o.scale, o.scale)
        postRotate(o.rotation)
        postTranslate(o.cx, o.cy)
    }

    /** Même code pour l'aperçu et le rendu final. */
    fun drawOverlayOn(canvas: Canvas, o: OverlaySpec) {
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (o.alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        }
        canvas.drawBitmap(o.bitmap, overlayMatrix(o), p)
    }

    /** Les 4 coins (x0,y0,x1,y1,…) de l'image ajoutée, dans le repère de l'image principale. */
    fun overlayCorners(o: OverlaySpec): FloatArray {
        val w = o.bitmap.width.toFloat()
        val h = o.bitmap.height.toFloat()
        val pts = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        overlayMatrix(o).mapPoints(pts)
        return pts
    }

    fun drawOverlay(src: Bitmap, o: OverlaySpec): Bitmap {
        val out = copyOf(src)
        drawOverlayOn(Canvas(out), o)
        return out
    }
}
