package app.gridlauncher.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import app.gridlauncher.ui.theme.LauncherIcons
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gridlauncher.data.ShareCode
import app.gridlauncher.ui.LauncherViewModel
import app.gridlauncher.ui.theme.Palette

/** Copies a share code and opens the share sheet for it, with a line saying how to use it. */
fun shareCode(context: Context, code: String, what: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Grid Launcher $what", code))
    val text = "My Grid Launcher $what. In Grid Launcher, open Settings › Import a shared code and paste this:\n\n$code"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, "Share $what").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Import a share code: scan its QR code (or series of codes) with the camera, read them from a
 * screenshot, or paste the text. It shows what the code holds; nothing changes until a button is
 * tapped.
 */
@Composable
fun ImportCodeDialog(vm: LauncherViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var pasting by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<String?>(null) }
    val assembler = remember { app.gridlauncher.data.ShareQr.Assembler() }
    val shared = remember(text) { if (text.isBlank()) null else vm.readShareCode(text) }

    /** Feeds scanned texts in; a whole code shows its preview, a part says how many remain. */
    fun take(found: List<String>) {
        if (found.isEmpty()) {
            progress = "No code found there. Try again, closer and in good light."
            return
        }
        var last: app.gridlauncher.data.ShareQr.Progress = app.gridlauncher.data.ShareQr.Progress.NotACode
        for (f in found) {
            last = assembler.add(f)
            if (last is app.gridlauncher.data.ShareQr.Progress.Complete) break
        }
        when (val p = last) {
            is app.gridlauncher.data.ShareQr.Progress.Complete -> {
                progress = null
                text = p.code
                assembler.reset()
            }
            is app.gridlauncher.data.ShareQr.Progress.Partial ->
                progress = "Scanned ${p.have} of ${p.total}. Still to scan: part ${assembler.missing().joinToString(", ")}."
            app.gridlauncher.data.ShareQr.Progress.NotACode -> progress = "That isn't a Grid Launcher code."
        }
    }

    // Without the camera permission: Google's scanner, one frame per scan, with no permission needed.
    fun scanWithGoogle() {
        val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode -> take(listOfNotNull(barcode.rawValue)) }
            .addOnFailureListener { progress = "The scanner isn't available. Use From a screenshot instead." }
    }

    // The live scanner reads every frame of a live code in one go (it needs the camera, asked once).
    var scanningLive by remember { mutableStateOf(false) }
    val askCamera = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanningLive = true else scanWithGoogle()
    }
    fun scan() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) scanningLive = true else askCamera.launch(android.Manifest.permission.CAMERA)
    }
    if (scanningLive) {
        app.gridlauncher.ui.share.LiveScanner(
            onResult = { code ->
                scanningLive = false
                take(listOf(code))
            },
            onDismiss = { scanningLive = false },
        )
    }


    val pickImage = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                        ?.let(app.gridlauncher.ui.share.QrArt::decode)
                }.getOrNull().orEmpty()
            }
            take(found)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface3,
        titleContentColor = Palette.Fg,
        textContentColor = Palette.FgSoft,
        title = { Text("Import a shared code", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (shared == null) {
                    ImportOption("Scan a code", "Point the camera at the code on the other phone", LauncherIcons.Screens) { scan() }
                    ImportOption("From a screenshot", "Pick a picture of the code, such as one sent in a chat", LauncherIcons.Wallpaper) {
                        pickImage.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    if (pasting) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = { Text("GL1:…", fontFamily = FontFamily.Monospace) },
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Palette.Fg),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.LineStrong, cursorColor = Palette.Accent),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 140.dp),
                        )
                        TextButton(onClick = {
                            val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                            text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        }) { Text("Paste from clipboard", color = Palette.Accent) }
                    } else {
                        TextButton(onClick = { pasting = true }) { Text("Or paste a text code", color = Palette.Accent) }
                    }
                }
                val status = when (val s = shared) {
                    null -> progress ?: if (text.isNotBlank()) "That isn't a Grid Launcher code." else null
                    is ShareCode.Shared.Launcher -> "A whole launcher: ${s.settings.pages.size} screens and its settings. Replacing keeps your hidden and locked apps. Apps you don't have are left out." + slotNote(s.settings.pages)
                    is ShareCode.Shared.Screens -> (if (s.pages.size == 1) "A screen${s.pages[0].name.takeIf { it.isNotBlank() }?.let { " called $it" } ?: ""}. Apps you don't have are left out." else "${s.pages.size} screens. Apps you don't have are left out.") + slotNote(s.pages)
                }
                if (status != null) {
                    Text(
                        status,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = if (shared == null && (text.isNotBlank() || progress?.startsWith("That") == true || progress?.startsWith("No code") == true)) Palette.Danger else Palette.Muted,
                    )
                }
            }
        },
        confirmButton = {
            when (val s = shared) {
                is ShareCode.Shared.Launcher -> Column {
                    TextButton(onClick = { vm.applySharedLauncher(s.settings); onDismiss() }) { Text("Replace my launcher", color = Palette.Accent, fontWeight = FontWeight.SemiBold) }
                    TextButton(onClick = { vm.addSharedScreens(s.settings.pages); onDismiss() }) { Text("Only add its screens", color = Palette.Accent) }
                }
                is ShareCode.Shared.Screens -> TextButton(onClick = { vm.addSharedScreens(s.pages); onDismiss() }) {
                    Text(if (s.pages.size == 1) "Add screen" else "Add screens", color = Palette.Accent, fontWeight = FontWeight.SemiBold)
                }
                null -> Unit
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.FgSoft) } },
    )
}

@Composable
private fun ImportOption(title: String, hint: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .background(Palette.Surface)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = Palette.Fg)
            Text(hint, fontSize = 12.sp, color = Palette.Muted)
        }
    }
}

/** A line about widgets from other apps waiting to be added, or nothing. */
private fun slotNote(pages: List<app.gridlauncher.data.LauncherPage>): String {
    val n = pages.sumOf { p -> p.widgets.count { it.kind == app.gridlauncher.data.WidgetKind.WIDGET_SLOT } }
    return when (n) {
        0 -> ""
        1 -> " It has a widget from another app: tap its placeholder to add it."
        else -> " It has $n widgets from other apps: tap each placeholder to add it."
    }
}
