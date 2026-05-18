package dev.veeso.biangbiangui.ui.screens.camera

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ZoomState
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.veeso.biangbiangui.protocols.ProcessedText
import dev.veeso.biangbiangui.services.camera.LiveOcrAnalyzer
import dev.veeso.biangbiangui.services.camera.OcrBox
import dev.veeso.biangbiangui.services.camera.StillImageOcr
import dev.veeso.biangbiangui.services.camera.availablePresets
import dev.veeso.biangbiangui.services.camera.resolveOcrService
import dev.veeso.biangbiangui.services.camera.capturePhoto
import dev.veeso.biangbiangui.services.camera.clampZoom
import dev.veeso.biangbiangui.ui.AppDesign
import dev.veeso.biangbiangui.ui.BiangBiangContext
import dev.veeso.biangbiangui.ui.components.CopyToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Live camera surface with OCR overlays and controls. Config-driven port of
 * the reference BiangBiang Hanzi Android `camera/CameraLiveView.kt`; mirrors
 * iOS `CameraLiveScreen`.
 *
 * Generalisations vs. the reference:
 * - The hard-coded Chinese recognizer/`TextProcessor` is replaced by the 5.2
 *   `LiveOcrAnalyzer`/`StillImageOcr` built from `ctx.activeProfile.ocrRecognizer`
 *   and `ctx.engine`. The overlay consumes the upright dims the analyzer emits
 *   (correct `OcrRotation` basis — no drift).
 * - All strings from `config.strings`; `showPinyin` -> `showTransliteration`;
 *   toggle button uses `config.branding.buttonLogoAssetName`.
 * - Long-press save -> `settings.addHistory(...)` with the active variant id.
 * - Plugin CAMERA seam: on every recognised-set change, each box emits a
 *   `ProcessedText(source=CAMERA)` to every plugin `onProcessedText`; the
 *   first plugin `inlineResultView` is shown as a modal bottom sheet (the
 *   Android equivalent of the iOS half-sheet). Inert with no plugins.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CameraLiveScreen(ctx: BiangBiangContext) {
    var frameWidth by remember { mutableIntStateOf(1) }
    var frameHeight by remember { mutableIntStateOf(1) }
    var showTransliteration by remember { mutableStateOf(true) }
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    val ocrBoxes = remember { mutableStateListOf<OcrBox>() }
    val liveOcrBoxes = remember { mutableStateListOf<OcrBox>() }

    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var presets by remember { mutableStateOf(listOf(1f)) }
    var showCopyToast by remember { mutableStateOf(false) }
    var showSavedToast by remember { mutableStateOf(false) }
    var pluginSheet by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(showCopyToast) {
        if (showCopyToast) {
            delay(1500)
            showCopyToast = false
        }
    }
    LaunchedEffect(showSavedToast) {
        if (showSavedToast) {
            delay(1500)
            showSavedToast = false
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recognizerKind = ctx.activeProfile.ocrRecognizer
    val engine = ctx.engine
    val ocrService = remember(ctx.activeProfile) {
        resolveOcrService(ctx.activeProfile)
    }

    // Plugin CAMERA seam: dispatch onProcessedText for each box and surface the
    // first plugin inline view as a sheet. Inert when there are no plugins.
    val dispatchPluginHooks: (List<OcrBox>) -> Unit = dispatch@{ boxes ->
        if (ctx.config.plugins.isEmpty()) return@dispatch
        val variantId = ctx.activeVariant?.id ?: ""
        var firstSheet: (@Composable () -> Unit)? = null
        for (box in boxes) {
            val pt = ProcessedText(
                original = box.text,
                transliteration = box.transliteration,
                variantId = variantId,
                source = ProcessedText.Source.CAMERA,
            )
            for (plugin in ctx.config.plugins) {
                plugin.onProcessedText(pt)
                if (firstSheet == null) {
                    firstSheet = plugin.inlineResultView(pt)
                }
            }
        }
        if (firstSheet != null) {
            pluginSheet = firstSheet
        }
    }

    val saveBox: (OcrBox) -> Unit = { box ->
        scope.launch {
            ctx.settings.addHistory(
                original = box.text,
                transliteration = box.transliteration,
                variantId = ctx.activeVariant?.id ?: "",
            )
        }
        showSavedToast = true
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    val analyzer = remember(ocrService, recognizerKind, engine) {
        LiveOcrAnalyzer(
            service = ocrService,
            recognizer = recognizerKind,
            engine = engine,
            onResult = { newBoxes, w, h ->
                liveOcrBoxes.clear()
                liveOcrBoxes.addAll(newBoxes)
                frameWidth = w
                frameHeight = h
                dispatchPluginHooks(newBoxes)
            },
        )
    }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(
                LifecycleCameraController.IMAGE_CAPTURE or
                    LifecycleCameraController.IMAGE_ANALYSIS,
            )
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }

    LaunchedEffect(cameraController, analyzer) {
        cameraController.setImageAnalysisAnalyzer(
            ContextCompat.getMainExecutor(context),
            analyzer,
        )
        cameraController.bindToLifecycle(lifecycleOwner)
    }

    DisposableEffect(cameraController, lifecycleOwner) {
        val observer = Observer<ZoomState> { state ->
            maxZoom = state.maxZoomRatio
            presets = availablePresets(maxZoom = maxZoom)
            zoomRatio = state.zoomRatio
        }
        cameraController.zoomState.observe(lifecycleOwner, observer)
        onDispose { cameraController.zoomState.removeObserver(observer) }
    }

    fun applyZoom(newZoom: Float) {
        val clamped = clampZoom(newZoom, 1f, maxZoom)
        cameraController.setZoomRatio(clamped)
        zoomRatio = clamped
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            controller = cameraController
        }
    }

    LaunchedEffect(capturedImage, ocrService, recognizerKind, engine) {
        ocrBoxes.clear()
        capturedImage?.let { bitmap ->
            val boxes = StillImageOcr(ocrService, recognizerKind, engine)
                .recognize(bitmap)
            ocrBoxes.addAll(boxes)
            dispatchPluginHooks(boxes)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
            }.onSuccess { capturedImage = it }
        }
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (capturedImage == null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { previewView },
                )
                OcrOverlay(
                    boxes = liveOcrBoxes,
                    imageWidth = frameWidth,
                    imageHeight = frameHeight,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            // Pinch-to-zoom. Coexists with OcrOverlay's tap
                            // detector (detectTransformGestures waits for 2+).
                            detectTransformGestures { _, _, gestureZoom, _ ->
                                if (gestureZoom != 1f) {
                                    applyZoom(zoomRatio * gestureZoom)
                                }
                            }
                        },
                    isLive = true,
                    showTransliteration = showTransliteration,
                    onTextCopied = { showCopyToast = true },
                    onSaveBox = saveBox,
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = AppDesign.sectionSpacing),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (presets.isNotEmpty()) {
                        ZoomPresetBar(
                            presets = presets,
                            zoomRatio = zoomRatio,
                            onSelect = ::applyZoom,
                        )
                    }
                    CaptureControlBar(
                        showTransliteration = showTransliteration,
                        buttonLogoAssetName = ctx.config.branding.buttonLogoAssetName,
                        onToggle = { showTransliteration = !showTransliteration },
                        onCapture = {
                            capturePhoto(context, cameraController) { bitmap ->
                                capturedImage = bitmap
                            }
                        },
                        onPickGallery = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                    )
                }
            } else {
                Image(
                    bitmap = capturedImage!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )

                OcrOverlay(
                    boxes = ocrBoxes,
                    imageWidth = capturedImage!!.width,
                    imageHeight = capturedImage!!.height,
                    modifier = Modifier.fillMaxSize(),
                    isLive = false,
                    showTransliteration = showTransliteration,
                    onTextCopied = { showCopyToast = true },
                    onSaveBox = saveBox,
                )

                FilledTonalIconButton(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(AppDesign.stackSpacing),
                    onClick = { capturedImage = null },
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close preview")
                }
            }

            val anyBoxes =
                if (capturedImage == null) liveOcrBoxes.isNotEmpty()
                else ocrBoxes.isNotEmpty()
            if (anyBoxes) {
                Text(
                    text = ctx.config.strings.tapToCopyLongPressToSave,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = AppDesign.stackSpacing),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = AppDesign.bottomToolbarPadding),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CopyToast(
                        visible = showSavedToast,
                        message = ctx.config.strings.savedToHistory,
                    )
                    CopyToast(
                        visible = showCopyToast,
                        message = ctx.config.strings.textCopied,
                    )
                }
            }
        }
    }

    val sheet = pluginSheet
    if (sheet != null) {
        ModalBottomSheet(
            onDismissRequest = { pluginSheet = null },
            sheetState = sheetState,
        ) {
            sheet()
        }
    }
}

@Composable
private fun ZoomPresetBar(
    presets: List<Float>,
    zoomRatio: Float,
    onSelect: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = AppDesign.stackSpacing),
        horizontalArrangement = Arrangement.Center,
    ) {
        presets.forEach { preset ->
            val isActive = abs(zoomRatio - preset) < 0.05f
            FilledTonalIconButton(
                modifier = Modifier
                    .size(AppDesign.tapTarget)
                    .padding(horizontal = 4.dp),
                onClick = { onSelect(preset) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (isActive) AppDesign.accentRed else Color.Unspecified,
                    contentColor = if (isActive) Color.White else Color.Unspecified,
                ),
            ) {
                Text(
                    text = "${preset.toInt()}x",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun CaptureControlBar(
    showTransliteration: Boolean,
    buttonLogoAssetName: String,
    onToggle: () -> Unit,
    onCapture: () -> Unit,
    onPickGallery: () -> Unit,
) {
    val context = LocalContext.current
    val logoResId = remember(buttonLogoAssetName) {
        context.resources.getIdentifier(
            buttonLogoAssetName,
            "drawable",
            context.packageName,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            modifier = Modifier.size(48.dp),
            onClick = onToggle,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor =
                    if (showTransliteration) AppDesign.accentRed else Color.Unspecified,
                contentColor =
                    if (showTransliteration) Color.White else Color.Unspecified,
            ),
        ) {
            if (logoResId != 0) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(logoResId),
                    contentDescription = "Toggle transliteration",
                    modifier = Modifier.padding(8.dp),
                )
            } else {
                Icon(
                    Icons.Default.Lens,
                    contentDescription = "Toggle transliteration",
                    modifier = Modifier.padding(8.dp),
                )
            }
        }

        FilledIconButton(
            modifier = Modifier.size(AppDesign.shutterSize),
            onClick = onCapture,
            shape = CircleShape,
        ) {
            Icon(
                Icons.Default.Lens,
                contentDescription = "Shutter",
                modifier = Modifier.size(40.dp),
            )
        }

        FilledTonalIconButton(
            modifier = Modifier.size(48.dp),
            onClick = onPickGallery,
        ) {
            Icon(Icons.Default.Image, contentDescription = "Pick from gallery")
        }
    }
}
