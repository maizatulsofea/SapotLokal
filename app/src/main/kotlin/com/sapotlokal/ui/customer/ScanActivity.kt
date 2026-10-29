package com.sapotlokal.ui.customer

import android.Manifest
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.sapotlokal.AppConfig
import com.sapotlokal.data.repository.QrCodeRepository
import com.sapotlokal.data.repository.PointRepository
import com.sapotlokal.data.repository.VendorRepository
import com.sapotlokal.data.repository.VoucherRepository
import com.sapotlokal.util.isNetworkAvailable
import com.sapotlokal.databinding.ActivityScanBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * ScanActivity — Phase 3 QR scan screen.
 *
 * HOW IT WORKS:
 *   CameraX opens the rear camera. Each frame is handed to ML Kit's barcode
 *   scanner via ImageAnalysis. When ML Kit decodes a QR string, we pause
 *   scanning (so we don't process the same code twice), then call
 *   QrCodeRepository.validateScannedCode() against the live Firebase database.
 *   On success: write a POINT ledger row, show the "+N points" overlay,
 *   then finish after 2 seconds so CustomerHomeActivity's live listener
 *   picks up the change automatically.
 *
 * WHY VALIDATE SERVER-SIDE (build guide Rule 43):
 *   The code string alone means nothing. We check /qrcodes in Firebase
 *   to confirm it's real, active, not expired, and belongs to a real vendor.
 *   A modified APK can't forge this — the Security Rules on /qrcodes
 *   prevent clients from writing arbitrary records there.
 *
 * ⚠️ DEBUG HELPER: The debug panel at the bottom lets you paste a QR value
 *   manually. This is for testing without two devices. REMOVE BEFORE PHASE 7.
 */
class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private lateinit var cameraExecutor: ExecutorService
    private val auth = FirebaseAuth.getInstance()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Prevents multiple simultaneous validations from the same scan
    private var isProcessing = false

    // ── Permission launcher ───────────────────────────────────────────────────

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            showError("Camera permission is required to scan QR codes.")
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnBack.setOnClickListener { finish() }

        // Request camera permission or start camera directly if already granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setupDebugPanel()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    // ── CameraX setup ─────────────────────────────────────────────────────────

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview use-case: shows the live feed in PreviewView
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            // ImageAnalysis use-case: passes each frame to ML Kit
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor, QrAnalyzer { scannedValue ->
                        if (!isProcessing) {
                            isProcessing = true
                            mainHandler.post { handleScannedValue(scannedValue) }
                        }
                    })
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e("ScanActivity", "Camera bind failed", e)
                showError("Could not start camera. Please try again.")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // ── ML Kit analyser ───────────────────────────────────────────────────────

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private inner class QrAnalyzer(
        private val onQrDetected: (String) -> Unit
    ) : ImageAnalysis.Analyzer {

        private val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )

        override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
            val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    barcodes.firstOrNull()?.rawValue?.let { onQrDetected(it) }
                }
                .addOnCompleteListener { imageProxy.close() }
        }
    }

    // ── Validation flow ───────────────────────────────────────────────────────

    /**
     * Called on the main thread whenever a QR value is decoded (by camera or debug panel).
     * Checks network, then delegates to QrCodeRepository.
     */
    private fun handleScannedValue(scannedValue: String) {
        if (!isNetworkAvailable()) {
            showError(getString(com.sapotlokal.R.string.error_no_internet))
            isProcessing = false
            return
        }

        QrCodeRepository.validateScannedCode(
            scannedValue = scannedValue,
            onValid = { qrCode ->
                // Look up vendor name to show on success overlay
                VendorRepository.fetchVendorById(
                    vendorID = qrCode.vendorID,
                    onResult = { vendor ->
                        recordPointsAndShowSuccess(
                            vendorID   = qrCode.vendorID,
                            vendorName = vendor?.vendorName ?: "this shop"
                        )
                    },
                    onError = { _ ->
                        recordPointsAndShowSuccess(qrCode.vendorID, "this shop")
                    }
                )
            },
            onInvalid = { reason ->
                showError(reason)
                isProcessing = false
            },
            onError = { _ ->
                showError("Could not connect to check the code. Please try again.")
                isProcessing = false
            }
        )
    }

    private fun recordPointsAndShowSuccess(vendorID: String, vendorName: String) {
        val userID = auth.currentUser?.uid ?: run {
            showError("Not logged in. Please restart the app.")
            isProcessing = false
            return
        }

        PointRepository.recordShopVisit(
            userID   = userID,
            vendorID = vendorID,
            onResult = { success ->
                if (success) {
                    // Task 5.1: Immediately evaluate and auto-award vouchers for this shop visit
                    VoucherRepository.evaluateAndAwardVouchers(
                        userID   = userID,
                        vendorID = vendorID,
                        onResult = { newVouchers ->
                            if (newVouchers.isNotEmpty()) {
                                Log.i("ScanActivity", "Auto-awarded ${newVouchers.size} new vouchers")
                            }
                        }
                    )
                    // Task 6.1: Sync challenge progress on shop visit
                    com.sapotlokal.data.repository.ChallengeRepository.syncUserProgress(userID)
                    mainHandler.post {
                        showSuccessOverlay(vendorName)
                    }
                } else {
                    mainHandler.post {
                        showError("Points could not be saved. Please try again.")
                        isProcessing = false
                    }
                }
            }
        )
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun showSuccessOverlay(vendorName: String) {
        binding.tvPointsAwarded.text = "+${AppConfig.POINTS_PER_SHOP_VISIT} Points!"
        binding.tvShopName.text      = vendorName
        binding.successOverlay.visibility = View.VISIBLE

        val anim = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        binding.successOverlay.startAnimation(anim)

        // Auto-close after 2 seconds and return to CustomerHomeActivity
        mainHandler.postDelayed({ finish() }, 2000)
    }

    private fun showError(message: String) {
        binding.tvError.text       = message
        binding.tvError.visibility = View.VISIBLE

        // Hide error after 3 seconds so the camera can be used again
        mainHandler.postDelayed({
            binding.tvError.visibility = View.GONE
        }, 3000)
    }

    // ── ⚠️ DEBUG HELPER — REMOVE BEFORE PHASE 7 ──────────────────────────────

    private fun setupDebugPanel() {
        binding.btnDebugSubmit.setOnClickListener {
            val code = binding.etDebugCode.text?.toString()?.trim() ?: return@setOnClickListener
            if (code.isNotEmpty() && !isProcessing) {
                isProcessing = true
                handleScannedValue(code)
            }
        }

        // Also trigger on keyboard "Done" action
        binding.etDebugCode.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val code = binding.etDebugCode.text?.toString()?.trim() ?: ""
                if (code.isNotEmpty() && !isProcessing) {
                    isProcessing = true
                    handleScannedValue(code)
                }
                true
            } else false
        }
    }
}
