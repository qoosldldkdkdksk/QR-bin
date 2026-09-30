package com.example.qrbinary.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.qrbinary.R
import com.example.qrbinary.data.FileSaver
import com.example.qrbinary.data.Hashing
import com.example.qrbinary.data.HistoryEntry
import com.example.qrbinary.data.HistoryStore
import com.example.qrbinary.data.ImageFormatDetector
import com.example.qrbinary.data.QrDecoder
import com.google.android.material.button.MaterialButton
import java.io.File
import java.util.concurrent.Executors

class ScannerFragment : Fragment() {
    private lateinit var preview: PreviewView
    private lateinit var result: ImageView
    private lateinit var torch: MaterialButton
    private lateinit var cameraExecutor: java.util.concurrent.ExecutorService
    private var camera: Camera? = null
    private var decoder: QrDecoder? = null
    private var lastHash: String? = null
    private var cameraStarted = false

    private val pickQr = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        val bitmap = requireContext().contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return@registerForActivityResult
        decoder?.processBitmap(bitmap, ::handleBytes) {
            toast("Не удалось прочитать QR")
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasCameraPermission()) startCamera()
            else toast("Нужно разрешение на камеру")
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_scanner, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        preview = view.findViewById(R.id.preview)
        result = view.findViewById(R.id.result_preview)
        torch = view.findViewById(R.id.torch_button)
        decoder = QrDecoder()
        cameraExecutor = Executors.newSingleThreadExecutor()

        torch.setOnClickListener {
            camera?.let {
                if (it.cameraInfo.hasFlashUnit()) {
                    val on = it.cameraInfo.torchState.value == TorchState.ON
                    it.cameraControl.enableTorch(!on)
                }
            }
        }

        view.findViewById<MaterialButton>(R.id.import_button).setOnClickListener {
            pickQr.launch("image/*")
        }

        preview.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val factory = SurfaceOrientedMeteringPointFactory(
                    preview.width.toFloat(), preview.height.toFloat()
                )
                val point = factory.createPoint(event.x, event.y)
                val focusAction = FocusMeteringAction.Builder(point).build()
                camera?.cameraControl?.startFocusAndMetering(focusAction)
                true
            } else true
        }

        if (!hasCameraPermission()) {
            val permissions = mutableListOf(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.READ_MEDIA_IMAGES
            else permissions += Manifest.permission.READ_EXTERNAL_STORAGE
            if (Build.VERSION.SDK_INT <= 28) permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
            permissionLauncher.launch(permissions.toTypedArray())
        } else startCamera()
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        if (cameraStarted) return
        cameraStarted = true
        val future = ProcessCameraProvider.getInstance(requireContext())
        future.addListener({
            val provider = future.get()
            val previewUseCase = Preview.Builder().build().also {
                it.setSurfaceProvider(preview.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(cameraExecutor) { proxy ->
                val image = proxy.image
                if (image == null) {
                    proxy.close()
                    return@setAnalyzer
                }
                decoder?.process(
                    image,
                    proxy.imageInfo.rotationDegrees,
                    ::handleBytes,
                    onComplete = { proxy.close() }
                )
            }

            provider.unbindAll()
            camera = provider.bindToLifecycle(
                viewLifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                previewUseCase,
                analysis
            )
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun handleBytes(bytes: ByteArray) {
        val hash = Hashing.sha256(bytes)
        requireActivity().runOnUiThread {
            if (hash == lastHash) {
                vibrate()
                return@runOnUiThread
            }
            lastHash = hash

            val format = ImageFormatDetector.detect(bytes)
            if (format == null) {
                toast("Не похоже на изображение\n${ImageFormatDetector.hexDump(bytes)}")
                vibrate()
                return@runOnUiThread
            }

            val ext = format.extension
            val name = "QR_${System.currentTimeMillis()}.$ext"
            try {
                val uri = FileSaver.saveToDownloads(
                    requireContext(), bytes, name, format.mime
                )
                showPreview(bytes, format)
                HistoryStore(requireContext()).add(
                    HistoryEntry(name, bytes.size, format.name, hash, System.currentTimeMillis())
                )
                toast("Сохранено: $name")
                vibrate()
            } catch (e: Exception) {
                toast("Ошибка сохранения: ${e.message}")
            }
        }
    }

    private fun showPreview(bytes: ByteArray, format: ImageFormatDetector.Format) {
        result.visibility = View.VISIBLE
        if (format == ImageFormatDetector.Format.GIF) {
            Glide.with(this).asGif().load(bytes).into(result)
        } else {
            result.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        }
    }

    private fun vibrate() {
        val c = requireContext()
        if (Build.VERSION.SDK_INT >= 31) {
            c.getSystemService(VibratorManager::class.java).defaultVibrator
                .vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            (c.getSystemService(Vibrator::class.java)).vibrate(60)
        }
    }

    private fun toast(s: String) =
        Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        camera?.cameraControl?.enableTorch(false)
        decoder?.close()
        cameraExecutor.shutdown()
        super.onDestroyView()
    }
}
