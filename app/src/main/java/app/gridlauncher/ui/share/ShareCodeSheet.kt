package app.gridlauncher.ui.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.gridlauncher.data.ShareQr
import app.gridlauncher.ui.settings.shareCode
import app.gridlauncher.ui.theme.LauncherIcons
import app.gridlauncher.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shows a share code as styled QR codes to scan from another phone: one code, or a short series
 * to swipe through. The whole series can be saved or sent as one picture, or the code copied as
 * text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCodeSheet(code: String, title: String, what: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val parts = remember(code) { ShareQr.parts(code) }
    val images by produceState<List<Bitmap>?>(null, parts) {
        value = withContext(Dispatchers.Default) { parts.map { QrArt.render(it, 900) } }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
        contentColor = Palette.Fg,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Scan to import", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (parts.size == 1) {
                    "On the other phone: Grid Launcher › Settings › Import a shared code › Scan a code."
                } else {
                    "A live code: it plays its ${parts.size} frames on a loop. On the other phone, tap Scan a code and hold it here for a moment."
                },
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Palette.Muted,
                textAlign = TextAlign.Center,
            )
            // One code on screen. With several frames it cycles through them by itself, quickly
            // enough that a camera held on it reads them all in a second or two.
            var frame by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
            androidx.compose.runtime.LaunchedEffect(parts.size) {
                while (parts.size > 1) {
                    kotlinx.coroutines.delay(FRAME_MS)
                    frame = (frame + 1) % parts.size
                }
            }
            Box(Modifier.widthIn(max = 340.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(androidx.compose.ui.graphics.Color.White), contentAlignment = Alignment.Center) {
                val image = images?.getOrNull(frame)
                if (image != null) {
                    Image(image.asImageBitmap(), contentDescription = if (parts.size == 1) "Share code" else "Live share code, frame ${frame + 1} of ${parts.size}", modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                } else {
                    Text("Drawing…", fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.Gray)
                }
            }
            if (parts.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(parts.size) { i ->
                        Box(Modifier.size(6.dp).clip(CircleShape).background(if (i == frame) Palette.Accent else Palette.LineStrong))
                    }
                    Text("  Live · ${parts.size} frames", fontSize = 12.sp, color = Palette.Muted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetButton("Save image", LauncherIcons.Wallpaper) {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { saveToPictures(context, QrArt.card(parts, title), title) }
                        android.widget.Toast.makeText(context, if (ok) "Saved to Pictures › Grid Launcher" else "Couldn't save the image", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                SheetButton("Share image", LauncherIcons.Shortcut) {
                    scope.launch {
                        val uri = withContext(Dispatchers.IO) { cacheForSharing(context, QrArt.card(parts, title)) }
                        if (uri != null) {
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("image/png")
                                .putExtra(Intent.EXTRA_STREAM, uri)
                                .putExtra(Intent.EXTRA_TEXT, "My Grid Launcher $what. In Grid Launcher: Settings › Import a shared code › From a screenshot.")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            runCatching { context.startActivity(Intent.createChooser(send, "Share $what").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    }
                }
            }
            Text(
                "Copy as text instead",
                fontSize = 13.sp,
                color = Palette.Accent,
                modifier = Modifier.clip(CircleShape).clickable(role = Role.Button) { shareCode(context, code, what) }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SheetButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(Palette.Surface3)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Palette.Fg, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 14.sp)
    }
}

/** Saves [bitmap] to Pictures/Grid Launcher. No storage permission is needed for the app's own pictures. */
private fun saveToPictures(context: Context, bitmap: Bitmap, title: String): Boolean = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Grid Launcher - ${title.replace(Regex("[^A-Za-z0-9 ]"), "").trim().ifEmpty { "code" }}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Grid Launcher")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } != null
}.getOrDefault(false)

/** Writes [bitmap] to the app's cache and returns a link other apps can read, for sharing. */
private fun cacheForSharing(context: Context, bitmap: Bitmap) = runCatching {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "grid-launcher-code.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    FileProvider.getUriForFile(context, "${context.packageName}.share", file)
}.getOrNull()

/** How long each frame of a live code shows: about four a second, easy for a camera to catch. */
private const val FRAME_MS = 260L
