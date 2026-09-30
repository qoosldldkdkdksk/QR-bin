package com.example.qrbinary.data

object ImageFormatDetector {
    enum class Format(val extension: String, val mime: String) {
        GIF("gif", "image/gif"),
        PNG("png", "image/png"),
        JPEG("jpg", "image/jpeg")
    }

    fun detect(bytes: ByteArray): Format? {
        if (bytes.size >= 4 &&
            bytes[0] == 0x47 && bytes[1] == 0x49 &&
            bytes[2] == 0x46 && bytes[3] == 0x38) return Format.GIF

        if (bytes.size >= 8 &&
            bytes.copyOfRange(0, 8).contentEquals(
                byteArrayOf(
                    0x89.toByte(), 0x50, 0x4E, 0x47,
                    0x0D, 0x0A, 0x1A, 0x0A
                )
            )) return Format.PNG

        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()) return Format.JPEG

        return null
    }

    fun hexDump(bytes: ByteArray, count: Int = 64): String =
        bytes.take(count).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
