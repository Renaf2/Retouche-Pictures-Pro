package com.retouche.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class Tool(val label: String) {
    PAN("Déplacer"),
    BRUSH("Pinceau"),
    ERASER("Gomme"),
    FILL("Remplir"),
    PIPETTE("Pipette"),
    SHAPES("Formes"),
    CLONE("Tampon"),
    HEAL("Correcteur"),
    BLUR("Flou"),
    PIXELATE("Pixeliser"),
    ADJUST("Réglages"),
    FILTERS("Filtres"),
    TEXT("Texte"),
    IMAGE("Image"),
    CROP("Recadrer"),
    SELECT("Sélection"),
    TRANSPARENT("Fond transparent"),
    RESTORE("Restaurer"),
    LAYERS("Calques");

    /** Outils qui peignent en suivant le doigt, directement sur le calque. */
    val isStroke: Boolean
        get() = this == BRUSH || this == ERASER || this == CLONE || this == BLUR ||
            this == PIXELATE || this == RESTORE
}

/** Les familles d'outils, affichées dans la barre du bas. */
enum class Category(val label: String, val tools: List<Tool>) {
    DRAW("Dessiner", listOf(Tool.BRUSH, Tool.ERASER, Tool.FILL, Tool.PIPETTE, Tool.SHAPES)),
    FIX("Retoucher", listOf(Tool.CLONE, Tool.HEAL, Tool.BLUR, Tool.PIXELATE)),
    TUNE("Réglages", listOf(Tool.ADJUST, Tool.FILTERS)),
    ADD("Ajouter", listOf(Tool.TEXT, Tool.IMAGE)),
    CUT("Découper", listOf(Tool.CROP, Tool.SELECT, Tool.TRANSPARENT, Tool.RESTORE)),
    LAYERS("Calques", listOf(Tool.LAYERS));

    companion object {
        fun of(t: Tool): Category? = entries.firstOrNull { t in it.tools }
    }
}

enum class CropHandle { NONE, MOVE, TL, TR, BL, BR, L, R, T, B }

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    // ------------------------------------------------------------------ État observable

    var doc by mutableStateOf<Doc?>(null)
        private set

    /** Incrémenté à chaque modification de pixels : force le redessin du canevas. */
    var version by mutableIntStateOf(0)
        private set

    /** Incrémenté à chaque nouvelle image (ouverture / création) : sert à recadrer la vue. */
    var loadId by mutableIntStateOf(0)
        private set

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    var tool by mutableStateOf(Tool.PAN)
        private set
    var category by mutableStateOf(Category.DRAW)
        private set
    var color by mutableIntStateOf(AColor.BLACK)
        private set

    // Réglages des outils
    var brushType by mutableStateOf(BrushType.PEN)
    var brushSize by mutableFloatStateOf(24f)
    var opacity by mutableFloatStateOf(1f)
    var tolerance by mutableFloatStateOf(24f)
    var contiguous by mutableStateOf(true)
    var blurStrength by mutableFloatStateOf(14f)
    var pixelStrength by mutableFloatStateOf(16f)
    var shapeKind by mutableStateOf(ShapeKind.RECT)
    var shapeFilled by mutableStateOf(false)
    var shapeWidth by mutableFloatStateOf(8f)
    var selMode by mutableStateOf(SelMode.RECT)
    var adjust by mutableStateOf(Adjust())
    var filt by mutableStateOf(Filt.ORIGINAL)
    var filtStrength by mutableFloatStateOf(100f)
    var refineShrink by mutableFloatStateOf(1f)
    var refineFeather by mutableFloatStateOf(2f)

    var selection by mutableStateOf<Selection?>(null)
        private set
    var shapeDraft by mutableStateOf<ShapeSpec?>(null)
        private set
    var cropRect by mutableStateOf<RectF?>(null)
        private set
    var cropRatio by mutableStateOf<Float?>(null)
        private set
    var cloneSetting by mutableStateOf(true)
        private set
    var cloneMarker by mutableStateOf<PointF?>(null)
        private set

    /** Pour quel usage le sélecteur d'image a été ouvert (calque image ou arrière-plan). */
    var pendingPick: PickPurpose = PickPurpose.IMAGE_LAYER

    // ------------------------------------------------------------------ Interne

    private var nextId = 1
    private var layerCounter = 1
    private fun newId(): Int = nextId++

    private val undo = ArrayList<Doc>()
    private val redo = ArrayList<Doc>()
    private var live = false
    private var rev = 0

    private var stroke: StrokeBuilder? = null
    private var strokeBase: Bitmap? = null
    private var healBuilder: StrokeBuilder? = null
    private var healWidth = 24f

    private var cloneSrcX = 0f
    private var cloneSrcY = 0f
    private var cloneDx = 0f
    private var cloneDy = 0f
    private var cloneNeedsOffset = true

    private var effKey: String? = null
    private var effBitmap: Bitmap? = null

    private var selStartX = 0f
    private var selStartY = 0f
    private val lasso = ArrayList<PointF>()

    private var shapeStartX = 0f
    private var shapeStartY = 0f

    private var toolBeforePipette = Tool.BRUSH
    private val lastTool = HashMap<Category, Tool>()

    private companion object {
        const val MAX_STEPS = 30
        const val BUDGET_BYTES = 450L * 1024 * 1024
    }

    val strokeActive: Boolean get() = stroke != null
    val healActive: Boolean get() = healBuilder != null
    val healPath: Path? get() = healBuilder?.path
    val activeIsImage: Boolean get() = doc?.activeLayer?.kind == LayerKind.IMAGE
    val activeText: TextSpec? get() = doc?.activeLayer?.text
    val activeHasBackup: Boolean get() = doc?.activeLayer?.backup != null

    // ------------------------------------------------------------------ Historique

    private fun sync() {
        canUndo = undo.isNotEmpty()
        canRedo = redo.isNotEmpty()
        rev++
        version++
        if (tool == Tool.CROP) initCrop()
    }

    private fun historyBytes(): Long {
        val seen = IdentityHashMap<Bitmap, Boolean>()
        var total = 0L
        fun add(d: Doc?) {
            if (d == null) return
            for (l in d.layers) {
                l.bitmap?.let { if (seen.put(it, true) == null) total += it.allocationByteCount }
                l.backup?.let { if (seen.put(it, true) == null) total += it.allocationByteCount }
            }
        }
        add(doc)
        undo.forEach { add(it) }
        redo.forEach { add(it) }
        return total
    }

    private fun trim() {
        while (undo.size > MAX_STEPS) undo.removeAt(0)
        while (undo.size > 1 && historyBytes() > BUDGET_BYTES) undo.removeAt(0)
    }

    private fun resetHistory() {
        undo.clear()
        redo.clear()
        live = false
        stroke = null
        strokeBase = null
        healBuilder = null
        selection = null
        cropRect = null
        shapeDraft = null
        cloneMarker = null
        cloneSetting = true
        cloneNeedsOffset = true
        effBitmap = null
        effKey = null
        adjust = Adjust()
        filt = Filt.ORIGINAL
    }

    /** Valide un nouvel état du document ; l'ancien devient une étape d'annulation. */
    private fun commit(d: Doc) {
        liveEnd()
        doc?.let { undo.add(it) }
        redo.clear()
        doc = d
        trim()
        sync()
    }

    /** Modification continue (curseur, déplacement) : une seule étape d'annulation pour tout le geste. */
    private fun liveEdit(f: (Doc) -> Doc) {
        val d = doc ?: return
        if (!live) {
            undo.add(d)
            redo.clear()
            live = true
            canUndo = true
            canRedo = false
        }
        doc = f(d)
        version++
    }

    fun liveEnd() {
        if (!live) return
        live = false
        trim()
        sync()
    }

    fun undo() {
        if (busy != null || stroke != null || healBuilder != null) return
        liveEnd()
        val cur = doc ?: return
        if (undo.isEmpty()) return
        redo.add(cur)
        doc = undo.removeAt(undo.lastIndex)
        selection = null
        sync()
    }

    fun redo() {
        if (busy != null || stroke != null || healBuilder != null) return
        liveEnd()
        val cur = doc ?: return
        if (redo.isEmpty()) return
        undo.add(cur)
        doc = redo.removeAt(redo.lastIndex)
        selection = null
        sync()
    }

    /** Calcul lourd sur le calque actif, hors du thread principal. */
    private fun layerOp(
        label: String,
        needRaster: Boolean = true,
        after: () -> Unit = {},
        work: (Layer) -> Layer?
    ) {
        val d = doc ?: return
        if (busy != null) return
        liveEnd()
        busy = label
        viewModelScope.launch {
            var result: Doc? = null
            var error: String? = null
            try {
                result = withContext(Dispatchers.Default) {
                    var d2 = d
                    var l = d2.activeLayer ?: return@withContext null
                    if ((needRaster && l.kind != LayerKind.RASTER) || l.bitmap == null) {
                        l = DocOps.rasterize(d2, l)
                        d2 = d2.withActive(l)
                    }
                    val nl = work(l) ?: return@withContext null
                    d2.withActive(nl)
                }
            } catch (e: OutOfMemoryError) {
                error = "Mémoire insuffisante pour cette opération"
            } catch (e: Exception) {
                error = e.message ?: "Erreur"
            } finally {
                busy = null
            }
            if (error != null) {
                message = error
            } else if (result == null) {
                message = "Aucun changement"
            } else {
                commit(result)
                after()
            }
        }
    }

    /** Calcul lourd sur l'ensemble du document. */
    private fun docOp(label: String, after: () -> Unit = {}, work: (Doc) -> Doc?) {
        val d = doc ?: return
        if (busy != null) return
        liveEnd()
        busy = label
        viewModelScope.launch {
            var result: Doc? = null
            var error: String? = null
            try {
                result = withContext(Dispatchers.Default) { work(d) }
            } catch (e: OutOfMemoryError) {
                error = "Mémoire insuffisante pour cette opération"
            } catch (e: Exception) {
                error = e.message ?: "Erreur"
            } finally {
                busy = null
            }
            if (error != null) {
                message = error
            } else if (result == null) {
                message = "Aucun changement"
            } else {
                selection = null
                commit(result)
                after()
            }
        }
    }

    // ------------------------------------------------------------------ Outils / couleur

    fun selectTool(t: Tool) {
        if (busy != null || t == tool) return
        liveEnd()
        if (tool == Tool.CROP) cropRect = null
        if (tool == Tool.ADJUST || tool == Tool.FILTERS) {
            adjust = Adjust()
            filt = Filt.ORIGINAL
        }
        if (t != Tool.BLUR && t != Tool.PIXELATE) {
            effBitmap = null
            effKey = null
        }
        if (t == Tool.PIPETTE) toolBeforePipette = tool
        tool = t
        Category.of(t)?.let {
            category = it
            lastTool[it] = t
        }
        if (t == Tool.CROP) initCrop()
    }

    fun selectCategory(c: Category) {
        val t = lastTool[c] ?: c.tools.first()
        if (t == tool) {
            category = c
        } else {
            selectTool(t)
            category = c
        }
    }

    /** [applyToText] : si un texte est sélectionné, il prend aussi cette couleur. */
    fun chooseColor(argb: Int, applyToText: Boolean = true) {
        color = argb or (0xFF shl 24)
        if (applyToText && doc?.activeLayer?.kind == LayerKind.TEXT) {
            val c = color
            updateText { it.copy(color = c) }
        }
    }

    // ------------------------------------------------------------------ Fichiers

    fun load(uri: Uri) {
        if (busy != null) return
        busy = "Ouverture…"
        viewModelScope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) { ImageIO.load(getApplication(), uri) }
                resetHistory()
                doc = Doc(bmp.width, bmp.height, listOf(Layer(newId(), "Photo", LayerKind.RASTER, bmp)), 0)
                loadId++
                sync()
            } catch (e: OutOfMemoryError) {
                message = "Image trop grande pour la mémoire de l'appareil"
            } catch (e: Exception) {
                message = "Ouverture impossible : ${e.message}"
            } finally {
                busy = null
            }
        }
    }

    fun newBlank(w: Int, h: Int, bg: Int) {
        if (busy != null) return
        try {
            val b = ImageOps.fresh(w, h)
            if (AColor.alpha(bg) > 0) Canvas(b).drawColor(bg)
            resetHistory()
            doc = Doc(w, h, listOf(Layer(newId(), "Fond", LayerKind.RASTER, b)), 0)
            loadId++
            sync()
        } catch (e: OutOfMemoryError) {
            message = "Dimensions trop grandes pour la mémoire de l'appareil"
        }
    }

    fun save(uri: Uri, fmt: SaveFormat, quality: Int) {
        val d = doc ?: return
        if (busy != null) return
        liveEnd()
        busy = "Enregistrement…"
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val flat = Compositor.flatten(d)
                    ImageIO.save(getApplication(), uri, flat, fmt, quality)
                }
                message = "Image enregistrée"
            } catch (e: OutOfMemoryError) {
                message = "Mémoire insuffisante pour enregistrer"
            } catch (e: Exception) {
                message = "Échec de l'enregistrement : ${e.message}"
            } finally {
                busy = null
            }
        }
    }

    /** Une image vient d'être choisie dans le sélecteur. */
    fun onImagePicked(uri: Uri) {
        when (pendingPick) {
            PickPurpose.IMAGE_LAYER -> addImageLayer(uri)
            PickPurpose.BACKGROUND -> backgroundFromImage(uri)
        }
    }

    // ------------------------------------------------------------------ Pinceaux (dessin, gomme, tampon, flou, pixels, restaurer)

    private fun effectSource(l: Layer, blur: Boolean): Bitmap {
        val strength = if (blur) blurStrength.toInt() else pixelStrength.toInt()
        val key = "${l.id}|$rev|$blur|$strength"
        val cached = effBitmap
        if (effKey == key && cached != null) return cached
        val src = l.bitmap!!
        val out = if (blur) Fx.blurBitmap(src, strength) else Fx.pixelate(src, strength)
        effKey = key
        effBitmap = out
        return out
    }

    fun cloneChooseSource() {
        cloneSetting = true
        cloneNeedsOffset = true
        cloneMarker = null
    }

    fun cloneSetSource(x: Float, y: Float) {
        cloneSrcX = x
        cloneSrcY = y
        cloneNeedsOffset = true
        cloneSetting = false
        cloneMarker = PointF(x, y)
    }

    fun strokeStart(x: Float, y: Float) {
        val d = doc ?: return
        if (busy != null) return
        liveEnd()
        var d2 = d
        var layer = d2.activeLayer ?: return
        if (layer.kind != LayerKind.RASTER || layer.bitmap == null) {
            layer = DocOps.rasterize(d2, layer)
            d2 = d2.withActive(layer)
        }
        val base = layer.bitmap ?: return

        val paint: Paint
        val reach: Float
        when (tool) {
            Tool.BRUSH, Tool.ERASER -> {
                val eraser = tool == Tool.ERASER
                paint = ImageOps.strokePaint(color, brushSize, opacity, eraser, brushType)
                reach = ImageOps.strokeReach(brushType, brushSize, eraser)
            }
            Tool.CLONE -> {
                if (cloneSetting || cloneMarker == null) {
                    message = "Touchez d'abord l'image pour choisir la zone à copier"
                    return
                }
                if (cloneNeedsOffset) {
                    cloneDx = cloneSrcX - x
                    cloneDy = cloneSrcY - y
                    cloneNeedsOffset = false
                }
                paint = ImageOps.sourcePaint(base, cloneDx, cloneDy, brushSize, opacity, true)
                reach = ImageOps.sourceReach(brushSize)
                cloneMarker = PointF(x + cloneDx, y + cloneDy)
            }
            Tool.BLUR -> {
                paint = ImageOps.sourcePaint(effectSource(layer, true), 0f, 0f, brushSize, opacity, true)
                reach = ImageOps.sourceReach(brushSize)
            }
            Tool.PIXELATE -> {
                paint = ImageOps.sourcePaint(effectSource(layer, false), 0f, 0f, brushSize, opacity, false)
                reach = brushSize
            }
            Tool.RESTORE -> {
                val bk = layer.backup
                if (bk == null) {
                    message = "Disponible après une suppression du fond"
                    return
                }
                paint = ImageOps.sourcePaint(bk, 0f, 0f, brushSize, opacity, true)
                reach = ImageOps.sourceReach(brushSize)
            }
            else -> return
        }

        // On travaille sur une copie : l'état précédent (partagé, jamais modifié) sert à l'annulation.
        val work = ImageOps.copyOf(base)
        undo.add(d)
        redo.clear()
        doc = d2.withActive(layer.copy(bitmap = work))
        strokeBase = base
        stroke = StrokeBuilder(paint, reach).also { it.start(x, y) }
        trim()
        canUndo = true
        canRedo = false
        renderStroke()
    }

    fun strokeMove(x: Float, y: Float) {
        stroke?.move(x, y)
        if (tool == Tool.CLONE && stroke != null) cloneMarker = PointF(x + cloneDx, y + cloneDy)
        renderStroke()
    }

    fun strokeEnd() {
        val s = stroke ?: return
        s.end()
        renderStroke()
        strokeBase = null
        stroke = null
    }

    /** Annule un trait en cours (ex. un second doigt arrive pour zoomer). */
    fun strokeCancel() {
        if (stroke == null) return
        stroke = null
        strokeBase = null
        if (undo.isNotEmpty()) doc = undo.removeAt(undo.lastIndex)
        sync()
    }

    private fun renderStroke() {
        val d = doc ?: return
        val work = d.activeLayer?.bitmap ?: return
        val base = strokeBase ?: return
        val s = stroke ?: return
        val c = Canvas(work)
        c.save()
        c.clipRect(s.dirty(work.width, work.height))
        selection?.let { c.clipPath(it.path) }
        c.drawBitmap(base, 0f, 0f, ImageOps.SRC_PAINT)
        c.drawPath(s.path, s.paint)
        c.restore()
        version++
    }

    // ------------------------------------------------------------------ Correcteur (retire une tache)

    fun healStart(x: Float, y: Float) {
        if (doc == null || busy != null) return
        liveEnd()
        healWidth = brushSize
        healBuilder = StrokeBuilder(Paint(), brushSize).also { it.start(x, y) }
        version++
    }

    fun healMove(x: Float, y: Float) {
        healBuilder?.move(x, y)
        version++
    }

    fun healEnd() {
        val b = healBuilder ?: return
        b.end()
        healBuilder = null
        val path = b.path
        val w = healWidth
        version++
        layerOp("Correction…") { l -> Fx.heal(l.bitmap!!, path, w)?.let { l.copy(bitmap = it) } }
    }

    fun healCancel() {
        healBuilder = null
        version++
    }

    // ------------------------------------------------------------------ Remplissage / baguette / pipette

    fun fillAt(x: Float, y: Float) {
        val ix = floor(x).toInt()
        val iy = floor(y).toInt()
        val col = color or (0xFF shl 24)
        val tol = tolerance.toInt()
        layerOp("Remplissage…") { l -> ImageOps.fill(l.bitmap!!, ix, iy, col, tol)?.let { l.copy(bitmap = it) } }
    }

    fun wandAt(x: Float, y: Float) {
        val ix = floor(x).toInt()
        val iy = floor(y).toInt()
        val tol = tolerance.toInt()
        val cont = contiguous
        layerOp("Suppression du fond…") { l ->
            val b = l.bitmap!!
            ImageOps.makeTransparent(b, ix, iy, tol, cont)?.let { l.copy(bitmap = it, backup = l.backup ?: b) }
        }
    }

    fun pick(x: Float, y: Float) {
        val d = doc ?: return
        val c = Compositor.pixelAt(d, floor(x).toInt(), floor(y).toInt())
        if ((c ushr 24) == 0) return // pixel transparent : rien à copier
        chooseColor(c, applyToText = false)
    }

    /** Fin du geste de pipette : retour à l'outil précédent. */
    fun pickDone() {
        if (tool != Tool.PIPETTE) return
        selectTool(if (toolBeforePipette == Tool.PIPETTE) Tool.BRUSH else toolBeforePipette)
    }

    // ------------------------------------------------------------------ Formes

    fun shapeStart(x: Float, y: Float) {
        if (doc == null || busy != null) return
        liveEnd()
        shapeStartX = x
        shapeStartY = y
        shapeDraft = ShapeSpec(shapeKind, x, y, x, y, color, shapeWidth, shapeFilled, opacity)
    }

    fun shapeMove(x: Float, y: Float) {
        shapeDraft = shapeDraft?.copy(x1 = x, y1 = y)
    }

    fun shapeEnd() {
        val s = shapeDraft ?: return
        shapeDraft = null
        if (abs(s.x1 - s.x0) < 2f && abs(s.y1 - s.y0) < 2f) return
        layerOp("Dessin de la forme…") { l ->
            val out = ImageOps.copyOf(l.bitmap!!)
            Fx.drawShape(Canvas(out), s)
            l.copy(bitmap = out)
        }
    }

    fun shapeCancel() {
        shapeDraft = null
    }

    // ------------------------------------------------------------------ Texte

    /** Indice du calque texte touché en (x, y), ou -1. */
    fun textHit(x: Float, y: Float): Int {
        val d = doc ?: return -1
        for (i in d.layers.indices.reversed()) {
            val l = d.layers[i]
            if (!l.visible || l.kind != LayerKind.TEXT) continue
            val t = l.text ?: continue
            val b = ImageOps.textBounds(t)
            b.inset(-14f, -14f)
            if (b.contains(x, y)) return i
        }
        return -1
    }

    fun createText(x: Float, y: Float) {
        val d = doc ?: return
        if (busy != null) return
        val size = max(24f, min(d.width, d.height) / 10f)
        val base = TextSpec("Texte", 0f, 0f, size, color)
        val b = ImageOps.textBounds(base)
        val spec = base.copy(x = x - b.width() / 2f, y = y - b.height() / 2f)
        commit(insertAbove(d, Layer(newId(), "Texte", LayerKind.TEXT, text = spec)))
    }

    fun updateText(f: (TextSpec) -> TextSpec) {
        editActive { l -> if (l.kind == LayerKind.TEXT) l.copy(text = l.text?.let(f)) else l }
    }

    // ------------------------------------------------------------------ Calques

    private fun insertAbove(d: Doc, layer: Layer): Doc {
        val list = d.layers.toMutableList()
        list.add(d.active + 1, layer)
        return Doc(d.width, d.height, list, d.active + 1)
    }

    private fun insertBelowActive(d: Doc, layer: Layer): Doc {
        val list = d.layers.toMutableList()
        list.add(d.active, layer)
        return Doc(d.width, d.height, list, d.active + 1)
    }

    private fun editActive(f: (Layer) -> Layer) {
        liveEdit { d ->
            val l = d.activeLayer
            if (l == null) d else d.withActive(f(l))
        }
    }

    fun selectLayer(i: Int) {
        val d = doc ?: return
        if (i !in d.layers.indices || i == d.active) return
        liveEnd()
        doc = d.copy(active = i)
        version++
    }

    fun addLayer() {
        val d = doc ?: return
        layerCounter++
        val l = Layer(newId(), "Calque $layerCounter", LayerKind.RASTER, ImageOps.fresh(d.width, d.height))
        commit(insertAbove(d, l))
    }

    fun duplicateLayer() {
        val d = doc ?: return
        val src = d.activeLayer ?: return
        if (busy != null) return
        try {
            val copy = src.copy(id = newId(), name = src.name + " (copie)", bitmap = src.bitmap?.let { ImageOps.copyOf(it) })
            commit(insertAbove(d, copy))
        } catch (e: OutOfMemoryError) {
            message = "Mémoire insuffisante"
        }
    }

    fun deleteLayer() {
        val d = doc ?: return
        if (d.layers.size <= 1) {
            message = "Il faut garder au moins un calque"
            return
        }
        val list = d.layers.toMutableList()
        list.removeAt(d.active)
        commit(Doc(d.width, d.height, list, min(d.active, list.size - 1)))
    }

    fun moveLayer(up: Boolean) {
        val d = doc ?: return
        val to = if (up) d.active + 1 else d.active - 1
        if (to !in d.layers.indices) return
        val list = d.layers.toMutableList()
        val tmp = list[d.active]
        list[d.active] = list[to]
        list[to] = tmp
        commit(Doc(d.width, d.height, list, to))
    }

    fun mergeDown() {
        val d = doc ?: return
        if (d.active == 0) {
            message = "Il n'y a pas de calque en dessous"
            return
        }
        docOp("Fusion des calques…") { dd ->
            val top = dd.layers[dd.active]
            val below = dd.layers[dd.active - 1]
            val out = ImageOps.fresh(dd.width, dd.height)
            val c = Canvas(out)
            Compositor.drawLayer(c, below.copy(visible = true))
            Compositor.drawLayer(c, top.copy(visible = true))
            val merged = below.copy(
                kind = LayerKind.RASTER, bitmap = out, text = null,
                cx = 0f, cy = 0f, scale = 1f, rotation = 0f, flipped = false,
                opacity = 1f, blend = Blend.NORMAL, backup = null, visible = true
            )
            val list = dd.layers.toMutableList()
            list.removeAt(dd.active)
            list[dd.active - 1] = merged
            Doc(dd.width, dd.height, list, dd.active - 1)
        }
    }

    fun toggleVisible(i: Int) {
        val d = doc ?: return
        val l = d.layers.getOrNull(i) ?: return
        commit(d.withLayer(i, l.copy(visible = !l.visible)))
    }

    fun layerOpacity(v: Float) {
        editActive { it.copy(opacity = v.coerceIn(0f, 1f)) }
    }

    fun setBlend(b: Blend) {
        val d = doc ?: return
        val l = d.activeLayer ?: return
        commit(d.withActive(l.copy(blend = b)))
    }

    // Déplacement / transformation du calque image ou texte sélectionné

    fun moveActive(dx: Float, dy: Float) {
        editActive { l ->
            when (l.kind) {
                LayerKind.IMAGE -> l.copy(cx = l.cx + dx, cy = l.cy + dy)
                LayerKind.TEXT -> l.copy(text = l.text?.let { it.copy(x = it.x + dx, y = it.y + dy) })
                LayerKind.RASTER -> l
            }
        }
    }

    fun transformActive(zoom: Float, rotationDeg: Float, dx: Float, dy: Float) {
        editActive { l ->
            if (l.kind != LayerKind.IMAGE) l
            else l.copy(
                scale = (l.scale * zoom).coerceIn(0.005f, 50f),
                rotation = l.rotation + rotationDeg,
                cx = l.cx + dx,
                cy = l.cy + dy
            )
        }
    }

    /** Taille du calque image actif, en % du plus petit côté du document. */
    fun activeSizePercent(): Float {
        val d = doc ?: return 0f
        val l = d.activeLayer ?: return 0f
        val b = l.bitmap ?: return 0f
        if (l.kind != LayerKind.IMAGE) return 0f
        return l.scale * max(b.width, b.height) / min(d.width, d.height).toFloat() * 100f
    }

    fun setActiveSizePercent(p: Float) {
        val d = doc ?: return
        editActive { l ->
            val b = l.bitmap
            if (l.kind != LayerKind.IMAGE || b == null) l
            else l.copy(scale = p / 100f * min(d.width, d.height) / max(b.width, b.height).toFloat())
        }
    }

    fun setActiveRotation(deg: Float) {
        editActive { l -> if (l.kind == LayerKind.IMAGE) l.copy(rotation = deg) else l }
    }

    fun toggleActiveFlip() {
        editActive { l -> if (l.kind == LayerKind.IMAGE) l.copy(flipped = !l.flipped) else l }
    }

    fun addImageLayer(uri: Uri) {
        val d = doc ?: return
        if (busy != null) return
        liveEnd()
        busy = "Ouverture…"
        viewModelScope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) { ImageIO.load(getApplication(), uri, 2048) }
                val target = min(d.width, d.height) * 0.6f
                val s = target / max(bmp.width, bmp.height).toFloat()
                val cur = doc ?: d
                val layer = Layer(
                    newId(), "Image", LayerKind.IMAGE, bmp,
                    cx = cur.width / 2f, cy = cur.height / 2f, scale = s
                )
                commit(insertAbove(cur, layer))
            } catch (e: OutOfMemoryError) {
                message = "Image trop grande pour la mémoire de l'appareil"
            } catch (e: Exception) {
                message = "Ouverture impossible : ${e.message}"
            } finally {
                busy = null
            }
        }
    }

    // ------------------------------------------------------------------ Réglages et filtres

    fun previewMatrix(): FloatArray? = when (tool) {
        Tool.ADJUST -> if (adjust.colorIsIdentity) null else Fx.adjustMatrix(adjust)
        Tool.FILTERS ->
            if (filt == Filt.ORIGINAL) null else Fx.lerp(Fx.filterMatrix(filt), filtStrength / 100f)
        else -> null
    }

    fun previewVignette(): Float = when (tool) {
        Tool.ADJUST -> adjust.vignette
        Tool.FILTERS -> filt.vignette * filtStrength / 100f
        else -> 0f
    }

    fun resetAdjust() {
        adjust = Adjust()
        filt = Filt.ORIGINAL
        filtStrength = 100f
    }

    fun applyColorEffect() {
        val m = previewMatrix()
        val vig = previewVignette()
        val sharp = if (tool == Tool.ADJUST) adjust.sharpness else 0f
        if (m == null && vig <= 0f && sharp <= 0f) {
            message = "Rien à appliquer : modifiez d'abord un réglage"
            return
        }
        layerOp("Application…", needRaster = false, after = { resetAdjust() }) { l ->
            var b = l.bitmap!!
            if (m != null) b = Fx.applyMatrix(b, m)
            if (sharp > 0f) b = Fx.sharpen(b, sharp)
            if (vig > 0f) {
                if (b === l.bitmap) b = ImageOps.copyOf(b)
                Fx.drawVignette(Canvas(b), b.width, b.height, vig)
            }
            l.copy(bitmap = b)
        }
    }

    // ------------------------------------------------------------------ Fond transparent

    fun removeBackground() {
        val remover = BgRemover(getApplication())
        if (remover.modelName() == null) {
            message = "Modèle IA absent (voir le README). La baguette magique reste disponible."
            return
        }
        layerOp("Détourage IA en cours…") { l ->
            val b = l.bitmap!!
            l.copy(bitmap = remover.remove(b), backup = l.backup ?: b)
        }
    }

    fun refineEdge() {
        val s = refineShrink.toInt()
        val f = refineFeather.toInt()
        if (s == 0 && f == 0) {
            message = "Réglez « Rogner le liseré » ou « Adoucir » d'abord"
            return
        }
        layerOp("Affinage du contour…") { l -> l.copy(bitmap = Fx.refineEdge(l.bitmap!!, s, f)) }
    }

    fun backgroundColor() {
        val col = color or (0xFF shl 24)
        docOp("Ajout du fond…") { d ->
            val b = ImageOps.fresh(d.width, d.height)
            Canvas(b).drawColor(col)
            insertBelowActive(d, Layer(newId(), "Fond", LayerKind.RASTER, b))
        }
    }

    fun backgroundBlur() {
        val l = doc?.activeLayer ?: return
        if (l.backup == null) {
            message = "Supprimez d'abord le fond : le flou utilise la photo d'origine."
            return
        }
        docOp("Flou d'arrière-plan…") { d ->
            val src = d.activeLayer?.backup ?: return@docOp null
            val r = (0.03f * min(d.width, d.height)).toInt().coerceIn(10, 60)
            val blurred = ImageOps.copyOf(Fx.blurBitmap(src, r))
            insertBelowActive(d, Layer(newId(), "Fond flou", LayerKind.RASTER, blurred))
        }
    }

    private fun backgroundFromImage(uri: Uri) {
        val d = doc ?: return
        if (busy != null) return
        liveEnd()
        busy = "Ouverture…"
        viewModelScope.launch {
            try {
                val layer = withContext(Dispatchers.IO) {
                    val bmp = ImageIO.load(getApplication(), uri)
                    val fill = ImageOps.resizeTo(bmp, d.width, d.height, FitMode.FILL, 0)
                    Layer(newId(), "Fond", LayerKind.RASTER, fill)
                }
                val cur = doc ?: d
                selection = null
                commit(insertBelowActive(cur, layer))
            } catch (e: OutOfMemoryError) {
                message = "Image trop grande pour la mémoire de l'appareil"
            } catch (e: Exception) {
                message = "Ouverture impossible : ${e.message}"
            } finally {
                busy = null
            }
        }
    }

    // ------------------------------------------------------------------ Sélection

    fun selStart(x: Float, y: Float) {
        if (doc == null || busy != null) return
        liveEnd()
        selStartX = x
        selStartY = y
        lasso.clear()
        lasso.add(PointF(x, y))
        selection = null
    }

    fun selMove(x: Float, y: Float) {
        if (selMode == SelMode.LASSO) lasso.add(PointF(x, y))
        selection = Fx.buildSelection(selMode, selStartX, selStartY, x, y, lasso)
    }

    fun selEnd() {
        val s = selection ?: return
        if (s.bounds.width() < 4f && s.bounds.height() < 4f) selection = null
    }

    fun selClear() {
        selection = null
    }

    /** Copie (ou coupe) la sélection dans un nouveau calque image, que l'on peut ensuite déplacer. */
    fun selToLayer(cut: Boolean) {
        val sel = selection ?: return
        docOp(if (cut) "Découpe…" else "Copie…", after = { selectTool(Tool.IMAGE) }) { d ->
            var d2 = d
            var l = d2.activeLayer ?: return@docOp null
            if (l.kind != LayerKind.RASTER || l.bitmap == null) {
                l = DocOps.rasterize(d2, l)
                d2 = d2.withActive(l)
            }
            val bmp = l.bitmap ?: return@docOp null
            val piece = Fx.copySelection(bmp, sel) ?: return@docOp null
            if (cut) {
                val out = ImageOps.copyOf(bmp)
                Fx.eraseSelection(out, sel)
                l = l.copy(bitmap = out)
                d2 = d2.withActive(l)
            }
            val layer = Layer(
                newId(), "Sélection", LayerKind.IMAGE, piece.bitmap,
                cx = piece.left + piece.bitmap.width / 2f,
                cy = piece.top + piece.bitmap.height / 2f
            )
            insertAbove(d2, layer)
        }
    }

    fun selErase() {
        val sel = selection ?: return
        layerOp("Effacement…") { l ->
            val out = ImageOps.copyOf(l.bitmap!!)
            Fx.eraseSelection(out, sel)
            l.copy(bitmap = out)
        }
    }

    fun selFill() {
        val sel = selection ?: return
        val col = color or (0xFF shl 24)
        layerOp("Remplissage…") { l ->
            val out = ImageOps.copyOf(l.bitmap!!)
            Fx.fillSelection(out, sel, col)
            l.copy(bitmap = out)
        }
    }

    fun selEffect(blur: Boolean) {
        val sel = selection ?: return
        val strength = if (blur) blurStrength.toInt() else pixelStrength.toInt()
        layerOp(if (blur) "Flou…" else "Pixellisation…") { l ->
            val b = l.bitmap!!
            val src = if (blur) Fx.blurBitmap(b, strength) else Fx.pixelate(b, strength)
            val out = ImageOps.copyOf(b)
            Fx.paintSelection(out, sel, src)
            l.copy(bitmap = out)
        }
    }

    fun selCrop() {
        val sel = selection ?: return
        val r = Rect(
            floor(sel.bounds.left).toInt(), floor(sel.bounds.top).toInt(),
            ceil(sel.bounds.right).toInt(), ceil(sel.bounds.bottom).toInt()
        )
        docOp("Recadrage…") { d -> DocOps.crop(d, r) }
    }

    // ------------------------------------------------------------------ Recadrage

    private fun clamp(v: Float, lo: Float, hi: Float) = min(max(v, lo), hi)

    private fun initCrop() {
        val d = doc ?: return
        cropRect = RectF(0f, 0f, d.width.toFloat(), d.height.toFloat())
        chooseCropRatio(cropRatio)
    }

    fun resetCrop() {
        cropRatio = null
        initCrop()
    }

    fun chooseCropRatio(ratio: Float?) {
        cropRatio = ratio
        val r = cropRect ?: return
        val d = doc ?: return
        if (ratio == null) return
        val bw = d.width.toFloat()
        val bh = d.height.toFloat()
        var w = r.width()
        var h = w / ratio
        if (h > r.height()) {
            h = r.height()
            w = h * ratio
        }
        val l = clamp(r.centerX() - w / 2f, 0f, bw - w)
        val t = clamp(r.centerY() - h / 2f, 0f, bh - h)
        cropRect = RectF(l, t, l + w, t + h)
    }

    fun cropHit(x: Float, y: Float, slop: Float): CropHandle {
        val r = cropRect ?: return CropHandle.NONE
        val nearL = abs(x - r.left) <= slop
        val nearR = abs(x - r.right) <= slop
        val nearT = abs(y - r.top) <= slop
        val nearB = abs(y - r.bottom) <= slop
        val inX = x >= r.left - slop && x <= r.right + slop
        val inY = y >= r.top - slop && y <= r.bottom + slop
        val free = cropRatio == null
        return when {
            nearL && nearT -> CropHandle.TL
            nearR && nearT -> CropHandle.TR
            nearL && nearB -> CropHandle.BL
            nearR && nearB -> CropHandle.BR
            free && nearL && inY -> CropHandle.L
            free && nearR && inY -> CropHandle.R
            free && nearT && inX -> CropHandle.T
            free && nearB && inX -> CropHandle.B
            r.contains(x, y) -> CropHandle.MOVE
            else -> CropHandle.NONE
        }
    }

    fun cropDrag(h: CropHandle, dx: Float, dy: Float) {
        val r = cropRect ?: return
        val d = doc ?: return
        val bw = d.width.toFloat()
        val bh = d.height.toFloat()
        val minS = 8f
        val n = RectF(r)
        when (h) {
            CropHandle.MOVE -> n.offsetTo(
                clamp(r.left + dx, 0f, bw - r.width()),
                clamp(r.top + dy, 0f, bh - r.height())
            )
            CropHandle.L -> n.left = clamp(r.left + dx, 0f, r.right - minS)
            CropHandle.R -> n.right = clamp(r.right + dx, r.left + minS, bw)
            CropHandle.T -> n.top = clamp(r.top + dy, 0f, r.bottom - minS)
            CropHandle.B -> n.bottom = clamp(r.bottom + dy, r.top + minS, bh)
            CropHandle.TL, CropHandle.TR, CropHandle.BL, CropHandle.BR -> {
                val left = h == CropHandle.TL || h == CropHandle.BL
                val top = h == CropHandle.TL || h == CropHandle.TR
                // Le coin opposé reste fixe (ancre) ; le coin touché suit le doigt.
                val ax = if (left) r.right else r.left
                val ay = if (top) r.bottom else r.top
                val cx = (if (left) r.left else r.right) + dx
                val cy = (if (top) r.top else r.bottom) + dy
                val sx = if (left) -1f else 1f
                val sy = if (top) -1f else 1f
                val maxW = if (left) ax else bw - ax
                val maxH = if (top) ay else bh - ay
                var w = clamp(sx * (cx - ax), minS, max(minS, maxW))
                var hh = clamp(sy * (cy - ay), minS, max(minS, maxH))
                cropRatio?.let { ratio ->
                    hh = w / ratio
                    if (hh > maxH) {
                        hh = maxH
                        w = hh * ratio
                    }
                }
                n.set(
                    if (left) ax - w else ax,
                    if (top) ay - hh else ay,
                    if (left) ax else ax + w,
                    if (top) ay else ay + hh
                )
            }
            CropHandle.NONE -> return
        }
        cropRect = n
    }

    fun applyCrop() {
        val r = cropRect ?: return
        val rect = Rect(
            floor(r.left).toInt(), floor(r.top).toInt(),
            ceil(r.right).toInt(), ceil(r.bottom).toInt()
        )
        docOp("Recadrage…") { d -> DocOps.crop(d, rect) }
    }

    // ------------------------------------------------------------------ Transformations du document

    fun rotate(clockwise: Boolean) = docOp("Rotation…") { d -> DocOps.rotate90(d, clockwise) }

    fun flip(horizontal: Boolean) = docOp("Miroir…") { d -> DocOps.flip(d, horizontal) }

    /** Recadre le document sur la zone qui contient quelque chose de visible. */
    fun trimTransparent() = docOp("Rognage…") { d ->
        val flat = Compositor.flatten(d)
        val r = ImageOps.contentBounds(flat)
        flat.recycle()
        if (r == null || (r.left == 0 && r.top == 0 && r.right == d.width && r.bottom == d.height)) null
        else DocOps.crop(d, r)
    }

    fun resize(w: Int, h: Int, mode: FitMode, bg: Int) =
        docOp("Redimensionnement…") { d -> DocOps.resize(d, w, h, mode, bg) { newId() } }
}
