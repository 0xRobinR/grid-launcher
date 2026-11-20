package app.gridlauncher.data

import app.gridlauncher.domain.WidgetBounds
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareQrTest {
    /** A busy launcher: 8 screens, each with 24 apps, a folder and a few widgets. */
    private fun bigLauncher(): LauncherSettings {
        val b = WidgetBounds(0f, 0f, 160f, 100f)
        val pages = (1..8).map { p ->
            val apps = (1..24).map { "com.example.app$p$it/com.example.app$p$it.MainActivity#0" }
            LauncherPage(
                uid = "page$p",
                name = "Screen $p",
                appsMode = PageApps.CUSTOM,
                apps = apps + "folder:f$p",
                folders = listOf(AppFolder("f$p", "Folder $p", apps.take(4))),
                widgets = listOf(
                    PlacedWidget("c$p", WidgetKind.CLOCK, -1, b),
                    PlacedWidget("t$p", WidgetKind.TEXT, -1, b, text = TextSpec("Focus $p")),
                    PlacedWidget("n$p", WidgetKind.NOTE, -1, b, text = TextSpec("Buy milk")),
                ),
            )
        }
        return LauncherSettings(pages = pages)
    }

    /** Draws [text] as a QR code (as the app does: level Q) and reads it back with a QR reader. */
    private fun roundTrip(text: String): String {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4))
        val scale = 4
        val w = m.width * scale
        val pixels = IntArray(w * w) { i -> if (m[(i % w) / scale, (i / w) / scale]) 0xFF000000.toInt() else -1 }
        val image = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, w, pixels)))
        return QRCodeReader().decode(image, mapOf(DecodeHintType.PURE_BARCODE to true)).text
    }

    @Test
    fun aBigLauncherSplitsIntoAFewCodesThatPutItBackTogether() {
        val code = ShareCode.launcherCode(bigLauncher())
        val parts = ShareQr.parts(code)
        println("big launcher: ${code.length} characters in ${parts.size} QR frames")
        assertTrue(parts.size in 2..4)
        assertTrue(parts.all { it.length <= ShareQr.PART_SIZE })
        val assembler = ShareQr.Assembler()
        // Scanned out of order, one twice.
        val scans = (parts.reversed() + parts.first()).map(::roundTrip)
        var result: ShareQr.Progress = ShareQr.Progress.NotACode
        for (s in scans) {
            result = assembler.add(s)
            if (result is ShareQr.Progress.Complete) break
        }
        val assembled = (result as ShareQr.Progress.Complete).code
        assertEquals(ShareCode.forQr(code), assembled)
        // The reassembled QR form opens just like the text form.
        val local: (String) -> String? = { "$it#0" }
        assertEquals(ShareCode.read(code, local), ShareCode.read(assembled, local))
    }

    @Test
    fun aSmallCodeIsOneQrCodeAsItIs() {
        val code = ShareCode.screenCode(LauncherPage(uid = "p", name = "Work", appsMode = PageApps.CUSTOM, apps = listOf("com.a/.Main#0")))
        val parts = ShareQr.parts(code)
        assertEquals(listOf(ShareCode.forQr(code)), parts)
        assertTrue(parts.single().startsWith("GL2:"))
        assertEquals(ShareQr.Progress.Complete(parts.single()), ShareQr.Assembler().add(roundTrip(parts.single())))
        assertTrue(ShareCode.read(parts.single()) { it } is ShareCode.Shared.Screens)
    }

    @Test
    fun partsOfAnotherCodeStartOverAndJunkIsRejected() {
        val a = ShareQr.parts("GL1:" + "a".repeat(2000))
        val b = ShareQr.parts("GL1:" + "b".repeat(2000))
        val assembler = ShareQr.Assembler()
        assertEquals(ShareQr.Progress.Partial(1, a.size), assembler.add(a[0]))
        assertEquals(ShareQr.Progress.Partial(1, b.size), assembler.add(b[0]))
        assertEquals((2..b.size).toList(), assembler.missing())
        assertEquals(ShareQr.Progress.NotACode, assembler.add("https://example.com"))
        assertEquals(ShareQr.Progress.NotACode, assembler.add("GLP1:zz:9/3:x"))
    }

    @Test
    fun base45MatchesTheStandard() {
        // The examples from RFC 9285.
        assertEquals("BB8", Base45.encode("AB".toByteArray()))
        assertEquals("%69 VD92EX0", Base45.encode("Hello!!".toByteArray()))
        assertEquals("UJCLQE7W581", Base45.encode("base-45".toByteArray()))
        assertEquals("ietf!", String(Base45.decode("QED8WEX0")!!))
        assertEquals(null, Base45.decode("GGW"))
        val bytes = ByteArray(1001) { (it * 37).toByte() }
        assertTrue(bytes.contentEquals(Base45.decode(Base45.encode(bytes))))
    }

    @Test
    fun theAppOrderTravelsOnlyWhenItsTheSort() {
        val apps = (1..150).map { "com.example.a$it/.Main#0" }
        val az = ShareCode.launcherCode(LauncherSettings(pages = listOf(LauncherPage(uid = "p")), customOrder = apps))
        val custom = ShareCode.launcherCode(LauncherSettings(pages = listOf(LauncherPage(uid = "p")), customOrder = apps, sortMode = app.gridlauncher.domain.SortMode.CUSTOM))
        val read = { code: String -> (ShareCode.read(code) { it } as ShareCode.Shared.Launcher).settings.customOrder }
        assertEquals(emptyList<String>(), read(az))
        assertEquals(150, read(custom).size)
    }
}
