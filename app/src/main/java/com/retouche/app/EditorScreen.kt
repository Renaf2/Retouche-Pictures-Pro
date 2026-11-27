package com.retouche.app

import android.graphics.Color as AColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Category as CategoryIcon
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Brush as BrushIcon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private enum class Dlg { NONE, RESIZE, NEW, SAVE, COLOR, ABOUT }

private val PALETTE: List<Int> = listOf(
    "#000000", "#FFFFFF", "#808080", "#C0C0C0", "#800000", "#FF0000", "#FF8000", "#FFFF00",
    "#008000", "#00FF00", "#008080", "#00FFFF", "#000080", "#0000FF", "#800080", "#FF00FF",
    "#804000", "#FF8080", "#80FF80", "#8080FF", "#FFD700", "#FFC0CB", "#A52A2A", "#4B0082"
).map { AColor.parseColor(it) }

// =====================================================================================
//  Composants de base : capsules, boutons ronds
// =====================================================================================

/** Capsule (forme « pilule ») sélectionnable : remplace les chips rectangulaires. */
@Composable
private fun FilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fg = if (selected) cs.onPrimary else cs.onSurfaceVariant
    Box(
        Modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(if (selected) cs.primary else cs.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) { label() }
        }
    }
}

/** Capsule d'action simple (non sélectionnable), même aspect que [FilterChip]. */
@Composable
private fun AssistChip(onClick: () -> Unit, label: @Composable () -> Unit) =
    FilterChip(selected = false, onClick = onClick, label = label)

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(cs.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, contentDescription = null,
            tint = if (enabled) cs.onSurfaceVariant else cs.onSurfaceVariant.copy(alpha = 0.35f)
        )
    }
}

@Composable
private fun CapsuleTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val fg = if (selected) cs.onPrimary else cs.onSurfaceVariant
    Row(
        Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(if (selected) cs.primary else cs.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon(fg)
        Spacer(Modifier.width(8.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

// =====================================================================================
//  Écran principal
// =====================================================================================

@Composable
fun EditorScreen(vm: EditorViewModel) {
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var dlg by remember { mutableStateOf(Dlg.NONE) }
    var saveFmt by remember { mutableStateOf(SaveFormat.PNG) }
    var saveQuality by remember { mutableIntStateOf(92) }
    var panelOpen by remember { mutableStateOf(true) }
    val hasImage = vm.doc != null

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }
    // Un nouvel outil rouvre toujours son panneau d'options
    LaunchedEffect(vm.tool) { panelOpen = true }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.load(uri)
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.onImagePicked(uri)
    }
    val pngLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) vm.save(uri, SaveFormat.PNG, saveQuality)
    }
    val jpgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        if (uri != null) vm.save(uri, SaveFormat.JPEG, saveQuality)
    }
    val webpLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/webp")) { uri ->
        if (uri != null) vm.save(uri, SaveFormat.WEBP, saveQuality)
    }

    fun startSave() {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "retouche_$stamp.${saveFmt.ext}"
        when (saveFmt) {
            SaveFormat.PNG -> pngLauncher.launch(name)
            SaveFormat.JPEG -> jpgLauncher.launch(name)
            SaveFormat.WEBP -> webpLauncher.launch(name)
        }
    }

    fun pickImage(purpose: PickPurpose) {
        vm.pendingPick = purpose
        pickLauncher.launch("image/*")
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasImage) {
                    RoundIconButton(Icons.AutoMirrored.Filled.Undo, "Annuler", vm.canUndo) { vm.undo() }
                    RoundIconButton(Icons.AutoMirrored.Filled.Redo, "Rétablir", vm.canRedo) { vm.redo() }
                } else {
                    AppLogo(Modifier.size(40.dp))
                    AppTitle(24.sp)
                }
                Spacer(Modifier.weight(1f))
                if (hasImage) {
                    Button(
                        onClick = { dlg = Dlg.SAVE },
                        modifier = Modifier.height(48.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Enregistrer")
                    }
                }
                Box {
                    RoundIconButton(Icons.Filled.Settings, "Réglages et à propos") { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        @Composable
                        fun item(label: String, enabled: Boolean = true, action: () -> Unit) {
                            DropdownMenuItem(
                                text = { Text(label) },
                                enabled = enabled,
                                onClick = {
                                    menuOpen = false
                                    action()
                                }
                            )
                        }
                        item("Ouvrir une image") { openLauncher.launch("image/*") }
                        item("Nouvelle image…") { dlg = Dlg.NEW }
                        item("Enregistrer…", hasImage) { dlg = Dlg.SAVE }
                        HorizontalDivider()
                        item("Changer la taille…", hasImage) { dlg = Dlg.RESIZE }
                        item("Pivoter ↻ 90°", hasImage) { vm.rotate(true) }
                        item("Pivoter ↺ 90°", hasImage) { vm.rotate(false) }
                        item("Miroir horizontal", hasImage) { vm.flip(true) }
                        item("Miroir vertical", hasImage) { vm.flip(false) }
                        item("Rogner les bords transparents", hasImage) { vm.trimTransparent() }
                        HorizontalDivider()
                        item("À propos") { dlg = Dlg.ABOUT }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (hasImage) {
                    CanvasArea(vm, Modifier.fillMaxSize())
                } else {
                    Welcome(
                        onOpen = { openLauncher.launch("image/*") },
                        onNew = { dlg = Dlg.NEW }
                    )
                }
                vm.busy?.let { BusyOverlay(it) }
            }
            if (hasImage) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.navigationBarsPadding()) {
                        PanelHeader(vm, panelOpen) { panelOpen = !panelOpen }
                        if (panelOpen) {
                            ToolOptions(vm, onPick = { pickImage(it) })
                        }
                        ToolRow(vm, onColor = { dlg = Dlg.COLOR })
                        CategoryBar(vm)
                    }
                }
            }
        }
    }

    when (dlg) {
        Dlg.RESIZE -> SizeDialog(forNew = false, vm = vm, onDismiss = { dlg = Dlg.NONE })
        Dlg.NEW -> SizeDialog(forNew = true, vm = vm, onDismiss = { dlg = Dlg.NONE })
        Dlg.SAVE -> SaveDialog(
            fmt = saveFmt,
            quality = saveQuality,
            onFmt = { saveFmt = it },
            onQuality = { saveQuality = it },
            onConfirm = {
                dlg = Dlg.NONE
                startSave()
            },
            onDismiss = { dlg = Dlg.NONE }
        )
        Dlg.COLOR -> ColorDialog(
            initial = vm.color,
            onPick = { vm.chooseColor(it) },
            onDismiss = { dlg = Dlg.NONE }
        )
        Dlg.ABOUT -> AboutDialog(onDismiss = { dlg = Dlg.NONE })
        Dlg.NONE -> {}
    }
}

@Composable
private fun Welcome(onOpen: () -> Unit, onNew: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppLogo(Modifier.size(132.dp))
        Spacer(Modifier.height(24.dp))
        AppTitle(34.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Retouchez vos photos simplement.\nAucune pub, aucune connexion Internet.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onOpen,
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().height(56.dp)
        ) { Text("Ouvrir une photo", style = MaterialTheme.typography.titleSmall) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onNew,
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().height(56.dp)
        ) { Text("Nouvelle image vide", style = MaterialTheme.typography.titleSmall) }
    }
}

@Composable
private fun BusyOverlay(label: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .pointerInput(Unit) {
                // Absorbe tous les gestes pendant un calcul
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                    } while (e.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(label, color = Color.White, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// =====================================================================================
//  Canevas : affichage multi-calques, zoom, déplacement, gestes des outils
// =====================================================================================

/** Calque IMAGE ou TEXTE touché en (x, y) : le calque actif d'abord, puis du dessus vers le dessous. */
private fun layerHit(d: Doc, x: Float, y: Float): Int {
    fun hit(i: Int): Boolean {
        val l = d.layers[i]
        if (!l.visible) return false
        return when (l.kind) {
            LayerKind.IMAGE -> {
                val b = l.bitmap ?: return false
                val m = Compositor.layerMatrix(l) ?: return false
                val inv = Matrix()
                if (!m.invert(inv)) return false
                val p = floatArrayOf(x, y)
                inv.mapPoints(p)
                p[0] >= 0f && p[1] >= 0f && p[0] <= b.width && p[1] <= b.height
            }
            LayerKind.TEXT -> {
                val t = l.text ?: return false
                val r = ImageOps.textBounds(t)
                r.inset(-14f, -14f)
                r.contains(x, y)
            }
            LayerKind.RASTER -> false
        }
    }
    if (d.active in d.layers.indices && hit(d.active)) return d.active
    for (i in d.layers.indices.reversed()) if (hit(i)) return i
    return -1
}

@Composable
private fun CanvasArea(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val docNow = vm.doc ?: return
    val density = LocalDensity.current
    val slopPx = with(density) { 28.dp.toPx() }
    val handlePx = with(density) { 10.dp.toPx() }

    var viewScale by remember { mutableFloatStateOf(1f) }
    var viewOffset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Damier de transparence
    val checker = remember {
        val tile = android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(tile)
        val p = Paint()
        p.color = 0xFFCCCCCC.toInt()
        c.drawRect(0f, 0f, 32f, 32f, p)
        p.color = 0xFFEEEEEE.toInt()
        c.drawRect(0f, 0f, 16f, 16f, p)
        c.drawRect(16f, 16f, 32f, 32f, p)
        ShaderBrush(ImageShader(tile.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }

    val whitePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = AColor.WHITE
        }
    }
    val darkPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFF5B4BDB.toInt()
        }
    }
    val healPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = 0x99FF3B30.toInt()
        }
    }

    // Recadre la vue à chaque nouvelle image ou changement de dimensions
    LaunchedEffect(vm.loadId, docNow.width, docNow.height, canvasSize != IntSize.Zero) {
        if (canvasSize != IntSize.Zero) {
            val s = min(
                canvasSize.width / docNow.width.toFloat(),
                canvasSize.height / docNow.height.toFloat()
            ) * 0.95f
            viewScale = s
            viewOffset = Offset(
                (canvasSize.width - docNow.width * s) / 2f,
                (canvasSize.height - docNow.height * s) / 2f
            )
        }
    }

    Canvas(
        modifier
            .onSizeChanged { canvasSize = it }
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tool = vm.tool
                    val d0 = vm.doc
                    if (vm.busy != null || d0 == null) return@awaitEachGesture

                    fun toImg(p: Offset) = Offset(
                        (p.x - viewOffset.x) / viewScale,
                        (p.y - viewOffset.y) / viewScale
                    )

                    val startPos = down.position
                    val p0 = toImg(startPos)
                    var multi = false
                    var active = false
                    var moved = false
                    var handle = CropHandle.NONE

                    when (tool) {
                        Tool.BRUSH, Tool.ERASER, Tool.BLUR, Tool.PIXELATE, Tool.RESTORE -> {
                            vm.strokeStart(p0.x, p0.y)
                            active = true
                        }
                        Tool.CLONE -> if (!vm.cloneSetting) {
                            vm.strokeStart(p0.x, p0.y)
                            active = true
                        }
                        Tool.HEAL -> {
                            vm.healStart(p0.x, p0.y)
                            active = true
                        }
                        Tool.PIPETTE -> {
                            vm.pick(p0.x, p0.y)
                            active = true
                        }
                        Tool.SHAPES -> {
                            vm.shapeStart(p0.x, p0.y)
                            active = true
                        }
                        Tool.SELECT -> {
                            vm.selStart(p0.x, p0.y)
                            active = true
                        }
                        Tool.CROP -> {
                            handle = vm.cropHit(p0.x, p0.y, slopPx / viewScale)
                            active = handle != CropHandle.NONE
                        }
                        Tool.TEXT -> {
                            val i = vm.textHit(p0.x, p0.y)
                            if (i >= 0) {
                                vm.selectLayer(i)
                                active = true
                            }
                        }
                        Tool.IMAGE, Tool.LAYERS -> {
                            val i = layerHit(d0, p0.x, p0.y)
                            if (i >= 0) {
                                vm.selectLayer(i)
                                active = true
                            }
                        }
                        else -> {}
                    }

                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            // Deux doigts : zoom + déplacement de la vue (ou, sur une image ajoutée, zoom + rotation de celle-ci)
                            if (!multi) {
                                multi = true
                                if (active) {
                                    when {
                                        tool.isStroke -> vm.strokeCancel()
                                        tool == Tool.HEAL -> vm.healCancel()
                                        tool == Tool.SHAPES -> vm.shapeCancel()
                                        tool == Tool.CLONE -> vm.strokeCancel()
                                    }
                                }
                                active = false
                            }
                            val zf = event.calculateZoom()
                            val pan = event.calculatePan()
                            val c = event.calculateCentroid()
                            if ((tool == Tool.IMAGE || tool == Tool.LAYERS) && vm.activeIsImage) {
                                vm.transformActive(
                                    zf, event.calculateRotation(),
                                    pan.x / viewScale, pan.y / viewScale
                                )
                            } else if (c.isSpecified) {
                                val ns = (viewScale * zf).coerceIn(0.02f, 100f)
                                val f = ns / viewScale
                                viewOffset = c - (c - viewOffset) * f + pan
                                viewScale = ns
                            }
                            event.changes.forEach { it.consume() }
                        } else if (!multi) {
                            val ch = event.changes.first()
                            if (!moved && (ch.position - startPos).getDistance() > viewConfiguration.touchSlop) {
                                moved = true
                            }
                            if (ch.positionChanged()) {
                                val delta = ch.position - ch.previousPosition
                                val pi = toImg(ch.position)
                                when {
                                    active && (tool.isStroke || tool == Tool.CLONE) -> vm.strokeMove(pi.x, pi.y)
                                    active && tool == Tool.HEAL -> vm.healMove(pi.x, pi.y)
                                    active && tool == Tool.PIPETTE -> vm.pick(pi.x, pi.y)
                                    active && tool == Tool.SHAPES -> vm.shapeMove(pi.x, pi.y)
                                    active && tool == Tool.SELECT -> vm.selMove(pi.x, pi.y)
                                    active && tool == Tool.CROP ->
                                        vm.cropDrag(handle, delta.x / viewScale, delta.y / viewScale)
                                    active && (tool == Tool.TEXT || tool == Tool.IMAGE || tool == Tool.LAYERS) ->
                                        vm.moveActive(delta.x / viewScale, delta.y / viewScale)
                                    tool == Tool.PAN || (moved && !active) -> viewOffset += delta
                                }
                                ch.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (!multi) {
                        when (tool) {
                            Tool.BRUSH, Tool.ERASER, Tool.BLUR, Tool.PIXELATE, Tool.RESTORE -> vm.strokeEnd()
                            Tool.CLONE ->
                                if (active) vm.strokeEnd()
                                else if (!moved) vm.cloneSetSource(p0.x, p0.y)
                            Tool.HEAL -> vm.healEnd()
                            Tool.SHAPES -> vm.shapeEnd()
                            Tool.SELECT -> vm.selEnd()
                            Tool.FILL -> if (!moved) vm.fillAt(p0.x, p0.y)
                            Tool.TRANSPARENT -> if (!moved) vm.wandAt(p0.x, p0.y)
                            Tool.PIPETTE -> vm.pickDone()
                            Tool.TEXT -> if (!active && !moved) vm.createText(p0.x, p0.y)
                            else -> {}
                        }
                    }
                    vm.liveEnd()
                }
            }
    ) {
        @Suppress("UNUSED_VARIABLE")
        val tick = vm.version // abonnement : redessine à chaque modification de pixels

        val d = vm.doc ?: return@Canvas
        val sc = viewScale
        val off = viewOffset
        val iw = d.width * sc
        val ih = d.height * sc
        val tool = vm.tool

        drawRect(checker, topLeft = off, size = Size(iw, ih))

        // ---- Les calques (avec l'aperçu en direct des réglages / filtres)
        val m = vm.previewMatrix()
        val filter = m?.let { ColorMatrixColorFilter(ColorMatrix(it)) }
        val vig = vm.previewVignette()
        drawIntoCanvas { cv ->
            val n = cv.nativeCanvas
            n.save()
            n.translate(off.x, off.y)
            n.scale(sc, sc)
            n.clipRect(0f, 0f, d.width.toFloat(), d.height.toFloat())
            Compositor.drawDoc(n, d, smooth = sc < 3f, filterLayerId = d.activeLayer?.id ?: -1, filter = filter)
            if (vig > 0f) Fx.drawVignette(n, d.width, d.height, vig)

            // Forme en cours de tracé
            vm.shapeDraft?.let { Fx.drawShape(n, it) }

            // Tache à corriger (aperçu du passage du doigt)
            vm.healPath?.let { p ->
                healPaint.strokeWidth = vm.brushSize
                n.drawPath(p, healPaint)
            }

            // Zone sélectionnée : contour blanc + pointillés
            vm.selection?.let { s ->
                whitePaint.strokeWidth = 3f / sc
                n.drawPath(s.path, whitePaint)
                darkPaint.strokeWidth = 3f / sc
                darkPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f / sc, 10f / sc), 0f)
                n.drawPath(s.path, darkPaint)
                darkPaint.pathEffect = null
            }
            n.restore()
        }

        // ---- Tampon : zone source
        if (tool == Tool.CLONE) {
            vm.cloneMarker?.let { mk ->
                val c = Offset(off.x + mk.x * sc, off.y + mk.y * sc)
                val r = vm.brushSize / 2f * sc
                drawCircle(Color.White, r, c, style = Stroke(width = 5f))
                drawCircle(Color(0xFF5B4BDB), r, c, style = Stroke(width = 2.5f))
                drawLine(Color.White, Offset(c.x - 14f, c.y), Offset(c.x + 14f, c.y), strokeWidth = 3f)
                drawLine(Color.White, Offset(c.x, c.y - 14f), Offset(c.x, c.y + 14f), strokeWidth = 3f)
            }
        }

        // ---- Cadre du calque image / texte sélectionné
        if (tool == Tool.IMAGE || tool == Tool.TEXT || tool == Tool.LAYERS) {
            val l = d.activeLayer
            if (l != null && l.visible) {
                if (l.kind == LayerKind.IMAGE) {
                    val c = Compositor.imageCorners(l)
                    if (c != null) {
                        val pts = List(4) { Offset(off.x + c[it * 2] * sc, off.y + c[it * 2 + 1] * sc) }
                        for (i in 0..3) {
                            drawLine(Color.White, pts[i], pts[(i + 1) % 4], strokeWidth = 6f)
                            drawLine(Color(0xFF5B4BDB), pts[i], pts[(i + 1) % 4], strokeWidth = 3f)
                        }
                        pts.forEach {
                            drawCircle(Color.White, radius = handlePx, center = it)
                            drawCircle(Color(0xFF5B4BDB), radius = handlePx * 0.6f, center = it)
                        }
                    }
                } else if (l.kind == LayerKind.TEXT) {
                    val t = l.text
                    if (t != null) {
                        val b = ImageOps.textBounds(t)
                        drawRect(
                            Color(0xFF5B4BDB),
                            topLeft = Offset(off.x + b.left * sc, off.y + b.top * sc),
                            size = Size(b.width() * sc, b.height() * sc),
                            style = Stroke(width = 3f)
                        )
                    }
                }
            }
        }

        // ---- Cadre de recadrage
        vm.cropRect?.let { r ->
            val sx = off.x + r.left * sc
            val sy = off.y + r.top * sc
            val sr = off.x + r.right * sc
            val sb = off.y + r.bottom * sc
            val dim = Color(0x99000000)
            drawRect(dim, Offset(off.x, off.y), Size(iw, (sy - off.y).coerceAtLeast(0f)))
            drawRect(dim, Offset(off.x, sb), Size(iw, (off.y + ih - sb).coerceAtLeast(0f)))
            drawRect(dim, Offset(off.x, sy), Size((sx - off.x).coerceAtLeast(0f), (sb - sy).coerceAtLeast(0f)))
            drawRect(dim, Offset(sr, sy), Size((off.x + iw - sr).coerceAtLeast(0f), (sb - sy).coerceAtLeast(0f)))
            drawRect(Color.White, Offset(sx, sy), Size(sr - sx, sb - sy), style = Stroke(width = 3f))
            for (k in 1..2) {
                val gx = sx + (sr - sx) * k / 3f
                val gy = sy + (sb - sy) * k / 3f
                drawLine(Color(0x88FFFFFF), Offset(gx, sy), Offset(gx, sb), strokeWidth = 1f)
                drawLine(Color(0x88FFFFFF), Offset(sx, gy), Offset(sr, gy), strokeWidth = 1f)
            }
            val mx = (sx + sr) / 2f
            val my = (sy + sb) / 2f
            val pts = mutableListOf(
                Offset(sx, sy), Offset(sr, sy), Offset(sx, sb), Offset(sr, sb)
            )
            if (vm.cropRatio == null) {
                pts += listOf(Offset(mx, sy), Offset(mx, sb), Offset(sx, my), Offset(sr, my))
            }
            pts.forEach {
                drawRect(
                    Color.White,
                    Offset(it.x - handlePx / 2f, it.y - handlePx / 2f),
                    Size(handlePx, handlePx)
                )
            }
        }
    }
}


// =====================================================================================
//  Barres du bas : options (haut), outils de la catégorie, catégories (bas)
// =====================================================================================

private fun categoryIcon(c: Category): ImageVector = when (c) {
    Category.DRAW -> Icons.Filled.BrushIcon
    Category.FIX -> Icons.Filled.AutoFixHigh
    Category.TUNE -> Icons.Filled.Tune
    Category.ADD -> Icons.Filled.AddPhotoAlternate
    Category.CUT -> Icons.Filled.ContentCut
    Category.LAYERS -> Icons.Filled.Layers
}

/** Barre du bas : une grande icône par famille d'outils, toujours visible. */
@Composable
private fun CategoryBar(vm: EditorViewModel) {
    val cs = MaterialTheme.colorScheme
    HorizontalDivider(color = cs.outlineVariant)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Category.entries.forEach { c ->
            val selected = vm.category == c && vm.tool != Tool.PAN
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { vm.selectCategory(c) }
                    .semantics { contentDescription = c.label },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier
                        .width(60.dp)
                        .height(34.dp)
                        .clip(CircleShape)
                        .background(if (selected) cs.primary else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        categoryIcon(c), contentDescription = null,
                        tint = if (selected) cs.onPrimary else cs.onSurfaceVariant
                    )
                }
                Text(
                    c.label,
                    fontSize = 12.sp,
                    maxLines = 1,
                    color = if (selected) cs.primary else cs.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/** Les outils de la famille choisie, en capsules ; la couleur et la main sont toujours à portée. */
@Composable
private fun ToolRow(vm: EditorViewModel, onColor: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CapsuleTab("Couleur", selected = false, onClick = onColor) { fg ->
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(vm.color))
                    .border(2.dp, fg, CircleShape)
            )
        }
        CapsuleTab(Tool.PAN.label, selected = vm.tool == Tool.PAN, onClick = { vm.selectTool(Tool.PAN) }) { fg ->
            ToolGlyph(Tool.PAN, fg)
        }
        vm.category.tools.forEach { t ->
            CapsuleTab(t.label, selected = vm.tool == t, onClick = { vm.selectTool(t) }) { fg ->
                ToolGlyph(t, fg)
            }
        }
    }
}

/** Icône de l'outil : icônes Material, sauf gomme et fond transparent dessinés à la main. */
@Composable
private fun ToolGlyph(t: Tool, tint: Color) {
    val vec: ImageVector? = when (t) {
        Tool.PAN -> Icons.Filled.PanTool
        Tool.BRUSH -> Icons.Filled.BrushIcon
        Tool.FILL -> Icons.Filled.FormatColorFill
        Tool.PIPETTE -> Icons.Filled.Colorize
        Tool.SHAPES -> Icons.Filled.CategoryIcon
        Tool.CLONE -> Icons.Filled.ContentCopy
        Tool.HEAL -> Icons.Filled.Healing
        Tool.BLUR -> Icons.Filled.BlurOn
        Tool.PIXELATE -> Icons.Filled.Grain
        Tool.ADJUST -> Icons.Filled.Tune
        Tool.FILTERS -> Icons.Filled.PhotoFilter
        Tool.TEXT -> Icons.Filled.TextFields
        Tool.IMAGE -> Icons.Filled.AddPhotoAlternate
        Tool.CROP -> Icons.Filled.Crop
        Tool.SELECT -> Icons.Filled.HighlightAlt
        Tool.RESTORE -> Icons.Filled.Restore
        Tool.LAYERS -> Icons.Filled.Layers
        else -> null
    }
    if (vec != null) {
        Icon(vec, contentDescription = null, tint = tint)
    } else {
        Canvas(Modifier.size(24.dp)) {
            val s = size.minDimension / 24f
            if (t == Tool.ERASER) {
                withTransform({ rotate(-45f, Offset(12f * s, 12f * s)) }) {
                    drawRoundRect(
                        tint, Offset(2f * s, 7f * s), Size(20f * s, 10f * s),
                        CornerRadius(2.5f * s), style = Stroke(2f * s)
                    )
                    drawRect(tint, Offset(12f * s, 8f * s), Size(9f * s, 8f * s))
                }
            } else {
                // Damier : symbole de la transparence
                drawRoundRect(
                    tint, Offset(3f * s, 3f * s), Size(18f * s, 18f * s),
                    CornerRadius(3f * s), style = Stroke(2f * s)
                )
                drawRect(tint, Offset(4f * s, 4f * s), Size(8f * s, 8f * s))
                drawRect(tint, Offset(12f * s, 12f * s), Size(8f * s, 8f * s))
            }
        }
    }
}

/** Titre de l'outil + bouton pour replier / déplier les options. */
@Composable
private fun PanelHeader(vm: EditorViewModel, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            vm.tool.label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(
                if (open) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = if (open) "Replier les options" else "Afficher les options"
            )
        }
    }
}

// =====================================================================================
//  Petits éléments réutilisables des panneaux
// =====================================================================================

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onDone: (() -> Unit)? = null,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(92.dp), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            onValueChangeFinished = { onDone?.invoke() },
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
        Text(display, Modifier.width(64.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector? = null,
    primary: Boolean = true,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val content: @Composable () -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }
    if (primary) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp)) { content() }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp)) { content() }
    }
}

@Composable
private fun BrushTypePicker(vm: EditorViewModel) {
    ChipRow {
        BrushType.entries.forEach { b ->
            FilterChip(
                selected = vm.brushType == b,
                onClick = { vm.brushType = b },
                label = { Text(b.label) }
            )
        }
    }
}

private fun normDeg(d: Float): Float {
    var x = d % 360f
    if (x > 180f) x -= 360f
    if (x < -180f) x += 360f
    return x
}


// =====================================================================================
//  Panneaux d'options, un par outil
// =====================================================================================

@Composable
private fun ToolOptions(vm: EditorViewModel, onPick: (PickPurpose) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 6.dp)
    ) {
        when (vm.tool) {
            Tool.PAN -> Hint(
                "Glissez avec un doigt pour déplacer l'image, pincez avec deux doigts pour zoomer. " +
                    "Choisissez une famille d'outils en bas."
            )
            Tool.BRUSH, Tool.ERASER -> {
                if (vm.tool == Tool.BRUSH) BrushTypePicker(vm)
                LabeledSlider("Taille", vm.brushSize, 1f..300f, "${vm.brushSize.toInt()} px") { vm.brushSize = it }
                LabeledSlider("Opacité", vm.opacity, 0.05f..1f, "${(vm.opacity * 100).toInt()} %") { vm.opacity = it }
            }
            Tool.FILL -> {
                Hint("Touchez une zone de l'image pour la remplir avec la couleur choisie.")
                LabeledSlider("Tolérance", vm.tolerance, 0f..128f, "${vm.tolerance.toInt()}") { vm.tolerance = it }
            }
            Tool.PIPETTE -> Hint("Touchez ou glissez sur l'image pour copier une couleur.")
            Tool.SHAPES -> ShapesPanel(vm)
            Tool.CLONE -> ClonePanel(vm)
            Tool.HEAL -> {
                Hint("Passez le doigt sur la tache, le bouton ou l'imperfection à effacer : elle disparaît en se fondant dans le décor.")
                LabeledSlider("Taille", vm.brushSize, 4f..200f, "${vm.brushSize.toInt()} px") { vm.brushSize = it }
            }
            Tool.BLUR -> {
                Hint("Peignez avec le doigt pour flouter (visage, plaque, détail privé).")
                LabeledSlider("Taille", vm.brushSize, 4f..300f, "${vm.brushSize.toInt()} px") { vm.brushSize = it }
                LabeledSlider("Force", vm.blurStrength, 2f..60f, "${vm.blurStrength.toInt()}") { vm.blurStrength = it }
            }
            Tool.PIXELATE -> {
                Hint("Peignez avec le doigt pour pixelliser (masque en gros carrés).")
                LabeledSlider("Taille", vm.brushSize, 4f..300f, "${vm.brushSize.toInt()} px") { vm.brushSize = it }
                LabeledSlider("Carrés", vm.pixelStrength, 4f..80f, "${vm.pixelStrength.toInt()}") { vm.pixelStrength = it }
            }
            Tool.ADJUST -> AdjustPanel(vm)
            Tool.FILTERS -> FiltersPanel(vm)
            Tool.TEXT -> TextPanel(vm)
            Tool.IMAGE -> ImagePanel(vm, onPick)
            Tool.CROP -> CropPanel(vm)
            Tool.SELECT -> SelectPanel(vm)
            Tool.TRANSPARENT -> TransparentPanel(vm, onPick)
            Tool.RESTORE -> {
                if (vm.activeHasBackup) {
                    Hint("Peignez avec le doigt pour faire réapparaître ce que la suppression du fond a effacé.")
                } else {
                    Hint("Disponible après une suppression du fond (outil « Fond transparent »).")
                }
                LabeledSlider("Taille", vm.brushSize, 2f..300f, "${vm.brushSize.toInt()} px") { vm.brushSize = it }
                LabeledSlider("Opacité", vm.opacity, 0.05f..1f, "${(vm.opacity * 100).toInt()} %") { vm.opacity = it }
            }
            Tool.LAYERS -> LayersPanel(vm)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ShapesPanel(vm: EditorViewModel) {
    ChipRow {
        ShapeKind.entries.forEach { k ->
            FilterChip(selected = vm.shapeKind == k, onClick = { vm.shapeKind = k }, label = { Text(k.label) })
        }
        if (vm.shapeKind == ShapeKind.RECT || vm.shapeKind == ShapeKind.ELLIPSE) {
            FilterChip(
                selected = vm.shapeFilled,
                onClick = { vm.shapeFilled = !vm.shapeFilled },
                label = { Text("Rempli") }
            )
        }
    }
    LabeledSlider("Épaisseur", vm.shapeWidth, 1f..80f, "${vm.shapeWidth.toInt()} px") { vm.shapeWidth = it }
    LabeledSlider("Opacité", vm.opacity, 0.05f..1f, "${(vm.opacity * 100).toInt()} %") { vm.opacity = it }
    Hint("Glissez sur l'image pour tracer la forme avec la couleur choisie.")
}

@Composable
private fun ClonePanel(vm: EditorViewModel) {
    if (vm.cloneSetting) {
        Hint("Étape 1 : touchez l'image à l'endroit à copier (la zone source).")
    } else {
        Hint("Étape 2 : peignez ailleurs, la zone source est recopiée. Le cercle indique la source.")
    }
    LabeledSlider("Taille", vm.brushSize, 4f..300f, "${vm.brushSize.toInt()} px") { vm.brushSize = it }
    LabeledSlider("Opacité", vm.opacity, 0.05f..1f, "${(vm.opacity * 100).toInt()} %") { vm.opacity = it }
    ActionButton("Choisir une autre source", primary = false) { vm.cloneChooseSource() }
}

@Composable
private fun AdjustPanel(vm: EditorViewModel) {
    val a = vm.adjust
    LabeledSlider("Luminosité", a.brightness, -100f..100f, "${a.brightness.toInt()}") { vm.adjust = vm.adjust.copy(brightness = it) }
    LabeledSlider("Contraste", a.contrast, -100f..100f, "${a.contrast.toInt()}") { vm.adjust = vm.adjust.copy(contrast = it) }
    LabeledSlider("Saturation", a.saturation, -100f..100f, "${a.saturation.toInt()}") { vm.adjust = vm.adjust.copy(saturation = it) }
    LabeledSlider("Chaleur", a.warmth, -100f..100f, "${a.warmth.toInt()}") { vm.adjust = vm.adjust.copy(warmth = it) }
    LabeledSlider("Netteté", a.sharpness, 0f..100f, "${a.sharpness.toInt()}") { vm.adjust = vm.adjust.copy(sharpness = it) }
    LabeledSlider("Vignettage", a.vignette, 0f..100f, "${a.vignette.toInt()}") { vm.adjust = vm.adjust.copy(vignette = it) }
    Hint("La netteté s'applique au moment de valider.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton("Appliquer", Icons.Filled.Check) { vm.applyColorEffect() }
        ActionButton("Remettre à zéro", primary = false) { vm.resetAdjust() }
    }
}

@Composable
private fun FiltersPanel(vm: EditorViewModel) {
    ChipRow {
        Filt.entries.forEach { f ->
            FilterChip(selected = vm.filt == f, onClick = { vm.filt = f }, label = { Text(f.label) })
        }
    }
    if (vm.filt != Filt.ORIGINAL) {
        LabeledSlider("Intensité", vm.filtStrength, 0f..100f, "${vm.filtStrength.toInt()} %") { vm.filtStrength = it }
    } else {
        Hint("Touchez un filtre pour le voir sur l'image, puis validez.")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton("Appliquer", Icons.Filled.Check, enabled = vm.filt != Filt.ORIGINAL) { vm.applyColorEffect() }
        ActionButton("Annuler", Icons.Filled.Close, primary = false) { vm.resetAdjust() }
    }
}

@Composable
private fun TextPanel(vm: EditorViewModel) {
    val t = vm.activeText
    if (t == null) {
        Hint("Touchez l'image à l'endroit où écrire. Touchez un texte existant pour le modifier ou le déplacer.")
        return
    }
    OutlinedTextField(
        value = t.text,
        onValueChange = { s -> vm.updateText { it.copy(text = s) } },
        label = { Text("Texte") },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    )
    ChipRow {
        listOf(
            "Sans" to "sans-serif",
            "Serif" to "serif",
            "Mono" to "monospace",
            "Manuscrit" to "cursive"
        ).forEach { (label, family) ->
            FilterChip(
                selected = t.font == family,
                onClick = { vm.updateText { it.copy(font = family) } },
                label = { Text(label) }
            )
        }
    }
    ChipRow {
        FilterChip(selected = t.bold, onClick = { vm.updateText { it.copy(bold = !it.bold) } }, label = { Text("Gras") })
        FilterChip(selected = t.italic, onClick = { vm.updateText { it.copy(italic = !it.italic) } }, label = { Text("Italique") })
        FilterChip(selected = t.outline, onClick = { vm.updateText { it.copy(outline = !it.outline) } }, label = { Text("Contour") })
        FilterChip(selected = t.shadow, onClick = { vm.updateText { it.copy(shadow = !it.shadow) } }, label = { Text("Ombre") })
    }
    val maxSize = max(400f, (vm.doc?.height ?: 0) / 2f)
    LabeledSlider("Taille", t.size, 8f..maxSize, "${t.size.toInt()} px", onDone = { vm.liveEnd() }) { s ->
        vm.updateText { it.copy(size = s) }
    }
    Hint("La couleur se change avec le bouton « Couleur » ci-dessous.")
    ActionButton("Supprimer ce texte", Icons.Filled.Delete, primary = false) { vm.deleteLayer() }
}

@Composable
private fun ImagePanel(vm: EditorViewModel, onPick: (PickPurpose) -> Unit) {
    val l = vm.doc?.activeLayer
    ActionButton(
        "Ajouter une image", Icons.Filled.AddPhotoAlternate,
        modifier = Modifier.fillMaxWidth()
    ) { onPick(PickPurpose.IMAGE_LAYER) }
    if (l == null || l.kind != LayerKind.IMAGE) {
        Hint("Ajoutez une photo, un logo ou un autocollant. Chaque image devient un calque que vous pouvez déplacer à tout moment.")
        return
    }
    Hint("Glissez l'image pour la déplacer. Avec deux doigts : agrandir et tourner.")
    val pct = vm.activeSizePercent()
    LabeledSlider("Taille", pct, 5f..300f, "${pct.toInt()} %", onDone = { vm.liveEnd() }) { vm.setActiveSizePercent(it) }
    LabeledSlider("Rotation", normDeg(l.rotation), -180f..180f, "${normDeg(l.rotation).toInt()}°", onDone = { vm.liveEnd() }) {
        vm.setActiveRotation(it)
    }
    LabeledSlider("Opacité", l.opacity, 0.1f..1f, "${(l.opacity * 100).toInt()} %", onDone = { vm.liveEnd() }) {
        vm.layerOpacity(it)
    }
    ChipRow {
        FilterChip(selected = l.flipped, onClick = { vm.toggleActiveFlip() }, label = { Text("Miroir") })
        AssistChip(onClick = { vm.deleteLayer() }, label = { Text("Supprimer l'image") })
    }
}

@Composable
private fun CropPanel(vm: EditorViewModel) {
    ChipRow {
        listOf<Pair<String, Float?>>(
            "Libre" to null,
            "1:1" to 1f,
            "4:3" to 4f / 3f,
            "3:4" to 3f / 4f,
            "16:9" to 16f / 9f,
            "9:16" to 9f / 16f,
            "A4 ↕" to 210f / 297f,
            "A4 ↔" to 297f / 210f
        ).forEach { (label, ratio) ->
            FilterChip(
                selected = vm.cropRatio == ratio,
                onClick = { vm.chooseCropRatio(ratio) },
                label = { Text(label) }
            )
        }
    }
    ChipRow {
        ActionButton("Appliquer", Icons.Filled.Check) { vm.applyCrop() }
        ActionButton("Réinitialiser", primary = false) { vm.resetCrop() }
        ActionButton("Pivoter", Icons.Filled.RotateRight, primary = false) { vm.rotate(true) }
        ActionButton("Miroir", Icons.Filled.Flip, primary = false) { vm.flip(true) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectPanel(vm: EditorViewModel) {
    ChipRow {
        SelMode.entries.forEach { m ->
            FilterChip(selected = vm.selMode == m, onClick = { vm.selMode = m }, label = { Text(m.label) })
        }
    }
    if (vm.selection == null) {
        Hint("Dessinez sur l'image la zone à sélectionner. Vous pourrez ensuite la copier, la déplacer, l'effacer, la flouter…")
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(onClick = { vm.selToLayer(false) }, label = { Text("Copier") })
        AssistChip(onClick = { vm.selToLayer(true) }, label = { Text("Couper et déplacer") })
        AssistChip(onClick = { vm.selErase() }, label = { Text("Effacer") })
        AssistChip(onClick = { vm.selFill() }, label = { Text("Remplir") })
        AssistChip(onClick = { vm.selEffect(true) }, label = { Text("Flouter") })
        AssistChip(onClick = { vm.selEffect(false) }, label = { Text("Pixelliser") })
        AssistChip(onClick = { vm.selCrop() }, label = { Text("Recadrer dessus") })
        AssistChip(onClick = { vm.selClear() }, label = { Text("Désélectionner") })
    }
    Hint("« Copier » et « Couper » créent un calque image : déplacez-le ensuite avec le doigt.")
}

@Composable
private fun TransparentPanel(vm: EditorViewModel, onPick: (PickPurpose) -> Unit) {
    SectionTitle("1. Supprimer le fond")
    ActionButton(
        "Supprimer le fond automatiquement (IA)",
        modifier = Modifier.fillMaxWidth()
    ) { vm.removeBackground() }
    Hint("Ou à la main : touchez le fond à rendre transparent.")
    LabeledSlider("Tolérance", vm.tolerance, 0f..128f, "${vm.tolerance.toInt()}") { vm.tolerance = it }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = vm.contiguous, onCheckedChange = { vm.contiguous = it })
        Text("  Zone connectée uniquement", style = MaterialTheme.typography.bodyMedium)
    }

    SectionTitle("2. Affiner le contour")
    LabeledSlider("Rogner", vm.refineShrink, 0f..8f, "${vm.refineShrink.toInt()} px") { vm.refineShrink = it }
    LabeledSlider("Adoucir", vm.refineFeather, 0f..8f, "${vm.refineFeather.toInt()} px") { vm.refineFeather = it }
    ActionButton("Affiner le contour", primary = false) { vm.refineEdge() }

    SectionTitle("3. Nouveau fond")
    ChipRow {
        AssistChip(onClick = { vm.backgroundColor() }, label = { Text("Couleur choisie") })
        AssistChip(onClick = { onPick(PickPurpose.BACKGROUND) }, label = { Text("Une image") })
        AssistChip(onClick = { vm.backgroundBlur() }, label = { Text("Photo floutée") })
    }

    SectionTitle("Outils")
    ChipRow {
        AssistChip(onClick = { vm.selectTool(Tool.RESTORE) }, label = { Text("Pinceau restaurer") })
        AssistChip(onClick = { vm.trimTransparent() }, label = { Text("Rogner les bords vides") })
    }
}

@Composable
private fun LayersPanel(vm: EditorViewModel) {
    val d = vm.doc ?: return
    val cs = MaterialTheme.colorScheme
    val tick = vm.version
    Hint("Touchez un calque pour le sélectionner. L'œil le cache ou le montre. Le calque du haut est affiché devant.")
    for (i in d.layers.indices.reversed()) {
        val l = d.layers[i]
        val selected = i == d.active
        val thumb = remember(l, d.width, d.height, tick) {
            Compositor.thumb(d, l, 96).asImageBitmap()
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (selected) cs.primaryContainer else cs.surfaceVariant)
                .clickable { vm.selectLayer(i) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFDDDDDD))
            ) {
                Image(
                    thumb, contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(l.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(
                    when (l.kind) {
                        LayerKind.RASTER -> "Dessin / photo"
                        LayerKind.IMAGE -> "Image déplaçable"
                        LayerKind.TEXT -> "Texte"
                    } + if (l.opacity < 0.99f) " · ${(l.opacity * 100).toInt()} %" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { vm.toggleVisible(i) }
                    .semantics { contentDescription = if (l.visible) "Cacher le calque" else "Montrer le calque" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (l.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = null,
                    tint = if (l.visible) cs.primary else cs.onSurfaceVariant
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    ChipRow {
        AssistChip(onClick = { vm.addLayer() }, label = { Text("+ Nouveau") })
        AssistChip(onClick = { vm.duplicateLayer() }, label = { Text("Dupliquer") })
        AssistChip(onClick = { vm.moveLayer(true) }, label = { Text("↑ Monter") })
        AssistChip(onClick = { vm.moveLayer(false) }, label = { Text("↓ Descendre") })
        AssistChip(onClick = { vm.mergeDown() }, label = { Text("Fusionner dessous") })
        AssistChip(onClick = { vm.deleteLayer() }, label = { Text("Supprimer") })
    }
    val active = d.activeLayer
    if (active != null) {
        LabeledSlider(
            "Opacité", active.opacity, 0.05f..1f, "${(active.opacity * 100).toInt()} %",
            onDone = { vm.liveEnd() }
        ) { vm.layerOpacity(it) }
        Text("Mode de fusion", style = MaterialTheme.typography.labelLarge)
        ChipRow {
            Blend.entries.forEach { b ->
                FilterChip(selected = active.blend == b, onClick = { vm.setBlend(b) }, label = { Text(b.label) })
            }
        }
    }
}

// =====================================================================================
//  Boîtes de dialogue
// =====================================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SizeDialog(forNew: Boolean, vm: EditorViewModel, onDismiss: () -> Unit) {
    val dm = LocalContext.current.resources.displayMetrics
    val cur = vm.doc
    val landscape = cur != null && cur.width > cur.height
    var wTxt by remember { mutableStateOf((cur?.width ?: 1080).toString()) }
    var hTxt by remember { mutableStateOf((cur?.height ?: 1080).toString()) }
    var mode by remember { mutableStateOf(FitMode.FIT) }
    var bgChoice by remember { mutableIntStateOf(0) }

    val shortSide = min(dm.widthPixels, dm.heightPixels)
    val longSide = max(dm.widthPixels, dm.heightPixels)
    val presets: List<Pair<String, Pair<Int, Int>>> = listOf(
        "Avatar 512" to (512 to 512),
        "Avatar 256" to (256 to 256),
        "Icône 48" to (48 to 48),
        "Icône 96" to (96 to 96),
        "Icône 192" to (192 to 192),
        "Icône 512" to (512 to 512),
        "Fond d'écran" to (shortSide to longSide),
        "Fond d'écran paysage" to (longSide to shortSide),
        "A4 portrait" to (2480 to 3508),
        "A4 paysage" to (3508 to 2480),
        "A4 auto" to (if (landscape) 3508 to 2480 else 2480 to 3508),
        "Full HD" to (1920 to 1080),
        "Story" to (1080 to 1920),
        "Carré 1080" to (1080 to 1080)
    )

    val w = wTxt.toIntOrNull()
    val h = hTxt.toIntOrNull()
    val valid = w != null && h != null && w in 1..8192 && h in 1..8192 && w.toLong() * h <= 40_000_000L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (forNew) "Nouvelle image" else "Changer la taille") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Formats prédéfinis", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (label, size) ->
                        AssistChip(
                            onClick = {
                                wTxt = size.first.toString()
                                hTxt = size.second.toString()
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = wTxt,
                        onValueChange = { wTxt = it.filter(Char::isDigit).take(5) },
                        label = { Text("Largeur (px)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hTxt,
                        onValueChange = { hTxt = it.filter(Char::isDigit).take(5) },
                        label = { Text("Hauteur (px)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!valid) {
                    Hint("Dimensions admises : 1 à 8192 px par côté, 40 millions de pixels au maximum.")
                }
                if (!forNew) {
                    Spacer(Modifier.height(12.dp))
                    Text("Adaptation", style = MaterialTheme.typography.labelLarge)
                    FitMode.entries.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().clickable { mode = m },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = mode == m, onClick = { mode = m })
                            Column {
                                Text(m.label)
                                Text(m.help, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (forNew || mode == FitMode.FIT) {
                    Spacer(Modifier.height(12.dp))
                    Text("Fond", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Transparent", "Blanc", "Couleur").forEachIndexed { i, label ->
                            FilterChip(
                                selected = bgChoice == i,
                                onClick = { bgChoice = i },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val bg = when (bgChoice) {
                        0 -> 0
                        1 -> AColor.WHITE
                        else -> vm.color or (0xFF shl 24)
                    }
                    if (forNew) vm.newBlank(w!!, h!!, bg) else vm.resize(w!!, h!!, mode, bg)
                    onDismiss()
                }
            ) { Text(if (forNew) "Créer" else "Appliquer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun SaveDialog(
    fmt: SaveFormat,
    quality: Int,
    onFmt: (SaveFormat) -> Unit,
    onQuality: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enregistrer") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaveFormat.entries.forEach { f ->
                        FilterChip(selected = fmt == f, onClick = { onFmt(f) }, label = { Text(f.label) })
                    }
                }
                if (fmt != SaveFormat.PNG) {
                    LabeledSlider("Qualité", quality.toFloat(), 40f..100f, "$quality %") { onQuality(it.toInt()) }
                }
                Hint(
                    when (fmt) {
                        SaveFormat.PNG -> "Sans perte, conserve la transparence."
                        SaveFormat.JPEG -> "Pas de transparence : le fond devient blanc."
                        SaveFormat.WEBP -> "Conserve la transparence, fichiers légers. 100 % = sans perte (Android 11+)."
                    }
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Choisir l'emplacement") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun ColorDialog(initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var c by remember { mutableIntStateOf(initial or (0xFF shl 24)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Couleur") },
        text = {
            Column {
                PALETTE.chunked(8).forEach { row ->
                    Row {
                        row.forEach { col ->
                            Box(
                                Modifier
                                    .padding(3.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(col))
                                    .border(1.dp, Color.Gray, CircleShape)
                                    .clickable { c = col }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(1.dp, Color.Gray, CircleShape)
                    )
                    Text("   #%06X".format(c and 0xFFFFFF), style = MaterialTheme.typography.bodyMedium)
                }
                LabeledSlider("Rouge", ((c shr 16) and 255).toFloat(), 0f..255f, "${(c shr 16) and 255}") {
                    c = (c and 0xFF00FFFF.toInt()) or (it.toInt() shl 16)
                }
                LabeledSlider("Vert", ((c shr 8) and 255).toFloat(), 0f..255f, "${(c shr 8) and 255}") {
                    c = (c and 0xFFFF00FF.toInt()) or (it.toInt() shl 8)
                }
                LabeledSlider("Bleu", (c and 255).toFloat(), 0f..255f, "${c and 255}") {
                    c = (c and 0xFFFFFF00.toInt()) or it.toInt()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPick(c)
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}


// =====================================================================================
//  Identité de l'application : logo, titre, « À propos »
// =====================================================================================

/** Icône de l'application (même dessin que l'icône du lanceur). */
@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(26))) {
        Image(
            painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Retouche Pictures Pro",
            modifier = Modifier.fillMaxSize().scale(1.25f)
        )
    }
}

/** Titre de l'application : « Retouche » en dégradé gras, « Pictures » en fin. */
@Composable
private fun AppTitle(size: TextUnit) {
    // Le titre rétrécit tout seul s'il ne tient pas sur une ligne (écrans étroits)
    var shrunk by remember(size) { mutableStateOf(size) }
    val brush = Brush.linearGradient(listOf(Color(0xFF6A5CFF), Color(0xFF1FB8C4)))
    val onSurface = MaterialTheme.colorScheme.onSurface
    Text(
        buildAnnotatedString {
            withStyle(
                SpanStyle(brush = brush, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            ) { append("Retouche") }
            withStyle(
                SpanStyle(color = onSurface, fontWeight = FontWeight.Light, letterSpacing = 1.sp)
            ) { append(" Pictures") }
            withStyle(
                SpanStyle(brush = brush, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            ) { append(" Pro") }
        },
        fontSize = shrunk,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        onTextLayout = { if (it.hasVisualOverflow && shrunk.value > 12f) shrunk = (shrunk.value * 0.92f).sp }
    )
}

@Composable
private fun AboutLine(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Filled.Check, contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("À propos") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painterResource(R.drawable.about_image),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                )
                Spacer(Modifier.height(16.dp))
                AppTitle(28.sp)
                if (version.isNotEmpty()) {
                    Text(
                        "Version $version",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Un éditeur d'images simple et complet, qui respecte votre vie privée.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                AboutLine("Aucune publicité")
                AboutLine("Aucune permission Internet : vos images ne quittent jamais votre téléphone")
                AboutLine("Aucune donnée collectée")
                AboutLine("Suppression du fond par IA haute précision (IS-Net), 100 % hors ligne")
                Spacer(Modifier.height(12.dp))
                Text(
                    "Réalisé avec Jetpack Compose, ONNX Runtime et les modèles IS-Net et U²-Net (licences libres).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}
