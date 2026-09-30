package com.example.qrbinary.data

object ImageFormatDetector {
    enum class Format(val extension: String, val mime: String) {
        GIF("gif", "image/gif"),
        PNG("png", "image/png"),
        JPEG("jpg", "image/jpeg")
    }

    fun detect(bytes: ByteArray): Format? {
        if (bytes.size >= 4 &&
            bytes[0].toInt() == 0x47 && bytes[1].toInt() == 0x49 &&
            bytes[2].toInt() == 0x46 && bytes[3].toInt() == 0x38) return Format.GIF

        if (bytes.size >= 8 &&
            bytes.copyOfRange(0, 8).contentEquals(
                byteArrayOf(
                    0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
                    0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
                )
            )) return Format.PNG

        if (bytes.size >= 3 &&
            bytes[0].toInt() == 0xFF &&
            bytes[1].toInt() == 0xD8 &&
            bytes[2].toInt() == 0xFF) return Format.JPEG

        return null
    }

    fun hexDump(bytes: ByteArray, count: Int = 64): String =
        bytes.take(count).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
