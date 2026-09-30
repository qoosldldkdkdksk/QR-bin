package com.example.qrbinary.data

import android.graphics.Bitmap
import android.media.Image
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

class QrDecoder {
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    private val busy = AtomicBoolean(false)

    fun process(
        image: Image,
        rotationDegrees: Int,
        onBytes: (ByteArray) -> Unit,
        onError: (Exception) -> Unit = {},
        onComplete: () -> Unit = {}
    ) {
        if (!busy.compareAndSet(false, true)) {
            onComplete()
            return
        }
        val input = InputImage.fromMediaImage(image, rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { codes ->
                codes.firstOrNull()?.rawBytes?.let(onBytes)
            }
            .addOnFailureListener { onError(it) }
            .addOnCompleteListener {
                busy.set(false)
                onComplete()
            }
    }

    fun processBitmap(
        bitmap: Bitmap,
        onBytes: (ByteArray) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        val input = InputImage.fromBitmap(bitmap, 0)
        scanner.process(input)
            .addOnSuccessListener { codes ->
                codes.firstOrNull()?.rawBytes?.let(onBytes)
            }
            .addOnFailureListener(onError)
    }

    fun close() {
        scanner.close()
    }
}
