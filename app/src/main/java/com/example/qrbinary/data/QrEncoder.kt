package com.example.qrbinary.data

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.common.BitMatrix
import java.nio.charset.StandardCharsets

object QrEncoder {
    const val MAX_BYTES = 2953

    fun encode(bytes: ByteArray, scale: Int = 20): Bitmap {
        require(bytes.size <= MAX_BYTES) {
            "Слишком много данных: ${bytes.size} байт. Максимум $MAX_BYTES."
        }

        val payload = String(bytes, StandardCharsets.ISO_8859_1)

        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "ISO-8859-1",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            EncodeHintType.QR_VERSION to 40,
            EncodeHintType.MARGIN to 4
        )

        val matrix = QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            177 * scale,
            177 * scale,
            hints
        )
        return bitMatrixToBitmap(matrix)
    }

    private fun bitMatrixToBitmap(matrix: BitMatrix): Bitmap {
        val bitmap = Bitmap.createBitmap(
            matrix.width,
            matrix.height,
            Bitmap.Config.ARGB_8888
        )
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                bitmap.setPixel(
                    x, y,
                    if (matrix[x, y]) android.graphics.Color.BLACK
                    else android.graphics.Color.WHITE
                )
            }
        }
        return bitmap
    }
}
