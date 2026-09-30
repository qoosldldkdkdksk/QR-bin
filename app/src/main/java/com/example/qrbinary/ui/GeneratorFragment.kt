package com.example.qrbinary.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.qrbinary.R
import com.example.qrbinary.data.FileSaver
import com.example.qrbinary.data.Hashing
import com.example.qrbinary.data.ImageFormatDetector
import com.example.qrbinary.data.QrEncoder
import com.google.android.material.button.MaterialButton
import java.io.ByteArrayOutputStream

class GeneratorFragment : Fragment() {
    private lateinit var qrPreview: ImageView
    private lateinit var sourcePreview: ImageView
    private lateinit var status: TextView
    private var originalBytes: ByteArray? = null
    private var qrBitmap: Bitmap? = null

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { loadImage(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_generator, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        qrPreview = view.findViewById(R.id.qr_preview)
        sourcePreview = view.findViewById(R.id.source_preview)
        status = view.findViewById(R.id.status)

        view.findViewById<MaterialButton>(R.id.select_image).setOnClickListener {
            picker.launch("image/*")
        }

        view.findViewById<MaterialButton>(R.id.save_qr).setOnClickListener {
            saveQr()
        }
        view.findViewById<MaterialButton>(R.id.share_qr).setOnClickListener {
            shareQr()
        }
        view.findViewById<MaterialButton>(R.id.verify_qr).setOnClickListener {
            verify()
        }
    }

    private fun loadImage(uri: Uri) {
        val bytes = requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return
        val name = getName(uri)
        originalBytes = bytes
        sourcePreview.visibility = View.VISIBLE
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { sourcePreview.setImageBitmap(it) }

        val format = ImageFormatDetector.detect(bytes)
        val text = "$name — ${bytes.size} байт"
        view?.findViewById<TextView>(R.id.file_info)?.text = text

        if (bytes.size <= QrEncoder.MAX_BYTES) {
            generate(bytes)
            return
        }

        if (format == ImageFormatDetector.Format.GIF) {
            status.text = "GIF: ${bytes.size} байт. GIF не сжимается автоматически, чтобы не сломать анимацию."
            hideQrButtons()
            return
        }

        val compressed = compressUntilFits(bytes, format)
        if (compressed != null) {
            status.text = "Исходный файл ${bytes.size} байт. Получено JPEG ${compressed.size} байт."
            originalBytes = compressed
            sourcePreview.setImageBitmap(
                BitmapFactory.decodeByteArray(compressed, 0, compressed.size)
            )
            generate(compressed)
        } else {
            status.text = "Не удалось сжать файл до ${QrEncoder.MAX_BYTES} байт."
            hideQrButtons()
        }
    }

    private fun compressUntilFits(
        bytes: ByteArray,
        format: ImageFormatDetector.Format?
    ): ByteArray? {
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        var bitmap = original
        var quality = 90
        while (quality >= 20) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val result = out.toByteArray()
            if (result.size <= QrEncoder.MAX_BYTES) return result
            quality -= 20
        }

        var width = bitmap.width
        var height = bitmap.height
        while (width > 80 && height > 80) {
            width = (width * 0.9).toInt()
            height = (height * 0.9).toInt()
            bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
            for (quality2 in listOf(90, 70, 50, 30)) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality2, out)
                val result = out.toByteArray()
                if (result.size <= QrEncoder.MAX_BYTES) return result
            }
        }
        return null
    }

    private fun generate(bytes: ByteArray) {
        if (bytes.size > QrEncoder.MAX_BYTES) return
        try {
            qrBitmap = QrEncoder.encode(bytes, 20)
            qrPreview.visibility = View.VISIBLE
            qrPreview.setImageBitmap(qrBitmap)
            status.text = "Готово: ${bytes.size} / ${QrEncoder.MAX_BYTES} байт. QR Version 40, EC L."
            showQrButtons()
        } catch (e: Exception) {
            status.text = "Ошибка генерации: ${e.message}"
            hideQrButtons()
        }
    }

    private fun saveQr() {
        val bitmap = qrBitmap ?: return
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        try {
            FileSaver.saveToDownloads(
                requireContext(), out.toByteArray(),
                "QR_Binary_${System.currentTimeMillis()}.png",
                "image/png"
            )
            toast("QR сохранён в Загрузки")
        } catch (e: Exception) {
            toast("Ошибка: ${e.message}")
        }
    }

    private fun shareQr() {
        val bitmap = qrBitmap ?: return
        val file = java.io.File(requireContext().cacheDir, "qr_share.png")
        file.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.fileprovider", file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(android.content.Intent.createChooser(intent, "Поделиться QR"))
    }

    private fun verify() {
        val bitmap = qrBitmap ?: return
        val expected = originalBytes ?: return
        com.example.qrbinary.data.QrDecoder().also { decoder ->
            decoder.processBitmap(bitmap, { recovered ->
                val ok = Hashing.sha256(expected) == Hashing.sha256(recovered)
                status.text = if (ok) "✅ Проверка SHA-256 пройдена: байты совпадают."
                else "❌ SHA-256 не совпал."
                decoder.close()
            }, {
                status.text = "❌ Не удалось декодировать созданный QR."
                decoder.close()
            })
        }
    }

    private fun showQrButtons() {
        listOf(R.id.save_qr, R.id.share_qr, R.id.verify_qr).forEach {
            view?.findViewById<MaterialButton>(it)?.visibility = View.VISIBLE
        }
    }

    private fun hideQrButtons() {
        listOf(R.id.save_qr, R.id.share_qr, R.id.verify_qr).forEach {
            view?.findViewById<MaterialButton>(it)?.visibility = View.GONE
        }
        qrPreview.visibility = View.GONE
    }

    private fun getName(uri: Uri): String {
        requireContext().contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return "image"
    }

    private fun toast(s: String) =
        Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show()
}
