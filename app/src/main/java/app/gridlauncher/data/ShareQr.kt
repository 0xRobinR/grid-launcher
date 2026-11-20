package app.gridlauncher.data

/**
 * Puts a share code into QR codes and back. The code is written in Base45 (QR codes' compact
 * alphanumeric set). A small code fits in one QR code; a bigger one becomes numbered frames that
 * the share sheet plays as one live, cycling code, scanned in a single go:
 *
 *     GLP2:<ID>:<frame>/<count>:<Base45 piece>
 *
 * Older GLP1 frames (Base64 text) are still read.
 */
object ShareQr {
    /**
     * The most code text in one QR frame. About QR version 21 at medium error correction: open
     * enough to read off another phone's screen while it cycles, with room for the logo.
     */
    const val PART_SIZE = 1000

    private const val PART_TAG = "GLP2:"
    private const val OLD_PART_TAG = "GLP1:"

    /** The texts to put in QR codes for [code], in order. */
    fun parts(code: String): List<String> {
        val qr = ShareCode.forQr(code)
        if (qr.length <= PART_SIZE) return listOf(qr)
        val body = qr.removePrefix("GL2:")
        val pieces = body.chunked(PART_SIZE - 20)
        val id = (code.hashCode() and 0xFFFF).toString(16).uppercase().padStart(4, '0')
        return pieces.mapIndexed { i, piece -> "$PART_TAG$id:${i + 1}/${pieces.size}:$piece" }
    }

    /** What scanning has gathered so far. */
    sealed interface Progress {
        /** Every part is in: the whole code. */
        data class Complete(val code: String) : Progress

        /** Some parts of a code are in. */
        data class Partial(val have: Int, val total: Int) : Progress

        /** That wasn't a Grid Launcher code. */
        data object NotACode : Progress
    }

    /** Collects scanned texts until a whole code is in. Scanning a frame twice is harmless. */
    class Assembler {
        private var tag: String? = null
        private var id: String? = null
        private var total = 0
        private val pieces = mutableMapOf<Int, String>()

        fun add(text: String): Progress {
            // Base45 includes the space, so only line breaks are trimmed.
            val t = text.trim { it == '\n' || it == '\r' || it == '\t' }
            val partTag = when {
                t.startsWith(PART_TAG) -> PART_TAG
                t.startsWith(OLD_PART_TAG) -> OLD_PART_TAG
                else -> null
            }
            if (partTag == null) {
                // A whole code in one QR (or pasted): use it as it is.
                val whole = t.trim()
                return if (whole.startsWith("GL1:") || whole.startsWith("GL2:") || whole.startsWith("{")) Progress.Complete(if (whole.startsWith("GL2:")) t.trimStart() else whole) else Progress.NotACode
            }
            val fields = t.removePrefix(partTag).split(':', limit = 3)
            if (fields.size != 3) return Progress.NotACode
            val (partId, position, piece) = fields
            val (index, count) = position.split('/').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 } ?: return Progress.NotACode
            if (count < 1 || index !in 1..count) return Progress.NotACode
            // A frame of a different code starts over.
            if (partTag != tag || partId != id || count != total) {
                tag = partTag
                id = partId
                total = count
                pieces.clear()
            }
            pieces[index] = piece
            if (pieces.size < total) return Progress.Partial(pieces.size, total)
            val body = (1..total).joinToString("") { pieces.getValue(it) }
            return Progress.Complete(if (partTag == PART_TAG) "GL2:$body" else body)
        }

        /** Which frames are still missing, 1-based. */
        fun missing(): List<Int> = (1..total).filter { it !in pieces }

        /** How many frames are in, and how many there are (0 until a frame has been seen). */
        fun counts(): Pair<Int, Int> = pieces.size to total

        fun reset() {
            tag = null
            id = null
            total = 0
            pieces.clear()
        }
    }
}
