package com.retouche.app

import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF

/**
 * RASTER : pixels de la taille du document (on peut dessiner dessus).
 * IMAGE  : image libre (position, taille, rotation modifiables à tout moment).
 * TEXT   : texte modifiable à tout moment.
 */
enum class LayerKind { RASTER, IMAGE, TEXT }

enum class Blend(val label: String, val mode: PorterDuff.Mode?) {
    NORMAL("Normal", null),
    MULTIPLY("Multiplier", PorterDuff.Mode.MULTIPLY),
    SCREEN("Écran", PorterDuff.Mode.SCREEN),
    OVERLAY("Superposition", PorterDuff.Mode.OVERLAY),
    DARKEN("Assombrir", PorterDuff.Mode.DARKEN),
    LIGHTEN("Éclaircir", PorterDuff.Mode.LIGHTEN)
}

/** Un calque est immuable : toute modification produit un nouveau calque (c'est ce qui permet l'annulation). */
data class Layer(
    val id: Int,
    val name: String,
    val kind: LayerKind,
    val bitmap: Bitmap? = null,
    val text: TextSpec? = null,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val blend: Blend = Blend.NORMAL,
    /** Centre de l'image (calque IMAGE uniquement), en pixels du document. */
    val cx: Float = 0f,
    val cy: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val flipped: Boolean = false,
    /** Pixels d'origine avant suppression du fond : sert au pinceau « Restaurer ». */
    val backup: Bitmap? = null
)

/** Le document : une pile de calques (index 0 = tout en bas). */
data class Doc(
    val width: Int,
    val height: Int,
    val layers: List<Layer>,
    val active: Int
) {
    val activeLayer: Layer? get() = layers.getOrNull(active)

    fun withLayer(index: Int, l: Layer): Doc =
        copy(layers = layers.toMutableList().also { it[index] = l })

    fun withActive(l: Layer): Doc = withLayer(active, l)
}

enum class SelMode(val label: String) {
    RECT("Rectangle"),
    ELLIPSE("Ellipse"),
    LASSO("Libre")
}

/** Zone sélectionnée (coordonnées du document). */
class Selection(val path: Path, val bounds: RectF)

enum class ShapeKind(val label: String) {
    LINE("Ligne"),
    ARROW("Flèche"),
    RECT("Rectangle"),
    ELLIPSE("Ellipse")
}

data class ShapeSpec(
    val kind: ShapeKind,
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    val color: Int,
    val width: Float,
    val filled: Boolean,
    val opacity: Float
)

/** Pourquoi on ouvre le sélecteur d'image. */
enum class PickPurpose { IMAGE_LAYER, BACKGROUND }
