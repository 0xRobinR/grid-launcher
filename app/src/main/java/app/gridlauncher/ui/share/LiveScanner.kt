package app.gridlauncher.ui.share

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.gridlauncher.data.ShareQr
import app.gridlauncher.ui.theme.Palette
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors

private const val TAG = "LiveScanner"

/**
 * The live-code scanner: a camera view that reads a share code's frames as they cycle on the
 * other phone, in any order, and finishes by itself once it has them all. The camera runs only
 * while this is open.
 */
@Composable
fun LiveScanner(onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val assembler = remember { ShareQr.Assembler() }
    var have by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    val latestResult by rememberUpdatedState(onResult)
    val ring by animateFloatAsState(if (total == 0) 0f else have / total.toFloat(), label = "progress")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx -> PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } },
                modifier = Modifier.fillMaxSize(),
            ) { preview ->
                // Bound once: the effect below unbinds when the scanner closes.
                if (preview.tag != null) return@AndroidView
                preview.tag = true
                val providerFuture = ProcessCameraProvider.getInstance(context)
                providerFuture.addListener({
                    val provider = runCatching { providerFuture.get() }.getOrNull() ?: return@addListener
                    val previewUse = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setResolutionStrategy(ResolutionStrategy(android.util.Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                                .build(),
                        )
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    val reader = QRCodeReader()
                    val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))
                    var last: String? = null
                    analysis.setAnalyzer(analyzerExecutor) { image ->
                        try {
                            val plane = image.planes[0]
                            val buffer = plane.buffer
                            val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
                            val source = PlanarYUVLuminanceSource(data, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
                            val text = runCatching { reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text }.getOrNull()
                            reader.reset()
                            if (text != null && text != last) {
                                last = text
                                ContextCompat.getMainExecutor(context).execute {
                                    if (done) return@execute
                                    when (val p = assembler.add(text)) {
                                        is ShareQr.Progress.Complete -> {
                                            done = true
                                            have = total.coerceAtLeast(1)
                                            latestResult(p.code)
                                        }
                                        is ShareQr.Progress.Partial -> {
                                            have = p.have
                                            total = p.total
                                        }
                                        ShareQr.Progress.NotACode -> Unit
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Frame skipped", e)
                        } finally {
                            image.close()
                        }
                    }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, previewUse, analysis)
                    }.onFailure { Log.w(TAG, "Camera unavailable", it) }
                }, ContextCompat.getMainExecutor(context))
            }
            DisposableEffect(Unit) {
                onDispose {
                    runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
                }
            }

            // The frame to aim with, and the progress ring around its centre.
            val accent = Palette.Accent
            Canvas(Modifier.fillMaxSize()) {
                val side = size.minDimension * 0.7f
                val left = (size.width - side) / 2
                val top = (size.height - side) / 2.4f
                val arm = side * 0.14f
                val w = 4.dp.toPx()
                for ((x, y, dx, dy) in listOf(listOf(left, top, 1f, 1f), listOf(left + side, top, -1f, 1f), listOf(left, top + side, 1f, -1f), listOf(left + side, top + side, -1f, -1f))) {
                    drawLine(accent, Offset(x, y), Offset(x + dx * arm, y), w, StrokeCap.Round)
                    drawLine(accent, Offset(x, y), Offset(x, y + dy * arm), w, StrokeCap.Round)
                }
                if (total > 0) {
                    val r = 34.dp.toPx()
                    val c = Offset(size.width / 2, top + side + 70.dp.toPx())
                    drawCircle(Color.White.copy(alpha = 0.18f), r, c, style = Stroke(5.dp.toPx()))
                    drawArc(accent, -90f, 360f * ring, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            Column(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 28.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Scan a shared code", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(
                    when {
                        total == 0 -> "Point the camera at the code on the other phone and hold steady."
                        have < total -> "Reading the live code: $have of $total. Keep holding."
                        else -> "Got it."
                    },
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(horizontal = 28.dp, vertical = 12.dp),
            ) {
                Text("Cancel", fontSize = 15.sp, color = Color.White)
            }
        }
    }
}

/** One background thread for reading camera frames, shared by every scanner opened. */
private val analyzerExecutor by lazy { Executors.newSingleThreadExecutor() }
