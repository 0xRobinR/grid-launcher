package app.gridlauncher.ui.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Share codes drawn as styled QR codes: round dots in a deep blue-to-violet gradient, rounded
 * corner "eyes", and the launcher's mark in the middle. Any QR scanner still reads them: the
 * dots sit on the module centres scanners sample, and error correction covers the mark.
 */
object QrArt {
    private const val QUIET = 4
    private val DotStart = Color.rgb(0x1B, 0x2A, 0x6B)
    private val DotEnd = Color.rgb(0x4B, 0x2C, 0x82)
    private val Paper = Color.WHITE

    /** The QR modules for [text], with no margin. */
    fun matrix(text: String): BitMatrix =
        QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0),
        )

    /** One styled code, [sizePx] square, quiet zone included, on white. */
    fun render(text: String, sizePx: Int): Bitmap {
        val m = matrix(text)
        val n = m.width
        val total = n + QUIET * 2
        val cell = sizePx / total.toFloat()
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Paper }
        canvas.drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), cell * 3, cell * 3, paper)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), DotStart, DotEnd, Shader.TileMode.CLAMP)
        }
        val origin = QUIET * cell
        // The middle, kept clear for the mark: about a fifth of the code, always odd.
        val logo = (n / 5).let { if (it % 2 == 0) it + 1 else it }.coerceAtLeast(5)
        val logoStart = (n - logo) / 2
        fun inFinder(x: Int, y: Int) = (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7)
        fun inLogo(x: Int, y: Int) = x in logoStart until logoStart + logo && y in logoStart until logoStart + logo
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (!m[x, y] || inFinder(x, y) || inLogo(x, y)) continue
                canvas.drawCircle(origin + (x + 0.5f) * cell, origin + (y + 0.5f) * cell, cell * 0.43f, ink)
            }
        }
        // The three eyes: a rounded ring with a rounded centre.
        for ((fx, fy) in listOf(0 to 0, n - 7 to 0, 0 to n - 7)) {
            val left = origin + fx * cell
            val top = origin + fy * cell
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = ink.shader
                style = Paint.Style.STROKE
                strokeWidth = cell
            }
            canvas.drawRoundRect(RectF(left + cell / 2, top + cell / 2, left + 6.5f * cell, top + 6.5f * cell), cell * 2f, cell * 2f, ring)
            canvas.drawRoundRect(RectF(left + 2 * cell, top + 2 * cell, left + 5 * cell, top + 5 * cell), cell * 1.1f, cell * 1.1f, ink)
        }
        // The mark: a white tile with the launcher's four-square grid.
        val l = origin + logoStart * cell
        val size = logo * cell
        canvas.drawRoundRect(RectF(l + cell * 0.4f, l + cell * 0.4f, l + size - cell * 0.4f, l + size - cell * 0.4f), cell * 1.6f, cell * 1.6f, paper)
        val tile = size * 0.26f
        val gap = size * 0.08f
        val start = l + (size - tile * 2 - gap) / 2
        for (i in 0..1) for (j in 0..1) {
            val x = start + i * (tile + gap)
            val y = start + j * (tile + gap)
            canvas.drawRoundRect(RectF(x, y, x + tile, y + tile), tile * 0.32f, tile * 0.32f, ink)
        }
        return bitmap
    }

    /**
     * A card with every part of a code, to save or send as one picture: a title, the codes in a
     * grid with "1 of 3" under each, and how to import them.
     */
    fun card(parts: List<String>, title: String): Bitmap {
        val cols = if (parts.size == 1) 1 else 2
        val rows = (parts.size + cols - 1) / cols
        val code = if (parts.size == 1) 900 else 520
        val pad = 64
        val gap = 48
        val header = 170
        val label = if (parts.size == 1) 0 else 64
        val footer = 120
        val width = pad * 2 + cols * code + (cols - 1) * gap
        val height = pad + header + rows * (code + label) + (rows - 1) * gap + footer
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(0x0F, 0x10, 0x12))
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xEC, 0xED, 0xEF); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = 56f }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x9C, 0xA1, 0xA9); textSize = 34f }
        canvas.drawText(title, pad.toFloat(), (pad + 64).toFloat(), text)
        canvas.drawText(if (parts.size == 1) "Grid Launcher share code" else "Grid Launcher share code · ${parts.size} frames, read in one go", pad.toFloat(), (pad + 120).toFloat(), muted)
        parts.forEachIndexed { i, part ->
            val c = i % cols
            val r = i / cols
            val x = pad + c * (code + gap)
            val y = pad + header + r * (code + label + gap)
            canvas.drawBitmap(render(part, code), x.toFloat(), y.toFloat(), null)
            if (parts.size > 1) canvas.drawText("${i + 1} of ${parts.size}", x.toFloat(), (y + code + 46).toFloat(), muted)
        }
        canvas.drawText("Grid Launcher › Settings › Import a shared code › From a screenshot", pad.toFloat(), (height - pad).toFloat(), muted)
        return bitmap
    }

    /** Every QR code text found in [bitmap], such as a screenshot of a share card. */
    fun decode(bitmap: Bitmap): List<String> {
        // Big photos are scaled down: plenty for QR codes, and much quicker.
        val scale = minOf(1f, 2000f / maxOf(bitmap.width, bitmap.height))
        val src = if (scale < 1f) bitmap.scale((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt()) else bitmap
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        val image = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(src.width, src.height, pixels)))
        val hints = mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))
        val many = runCatching { QRCodeMultiReader().decodeMultiple(image, hints).map { it.text } }.getOrNull()
        if (!many.isNullOrEmpty()) return many.distinct()
        return runCatching { listOf(QRCodeReader().decode(image, hints).text) }.getOrDefault(emptyList())
    }
}
