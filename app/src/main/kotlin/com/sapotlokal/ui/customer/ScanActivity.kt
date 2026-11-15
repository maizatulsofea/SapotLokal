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
 * The shop code box under the camera takes the 6-character code shown under the owner's QR,
 *   for when the camera can't scan. It runs the same check as a scan.
 */
class ScanActivity : AppCompatActivity() {
    private var badgeWatch: com.google.firebase.database.ValueEventListener? = null

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
        if (granted) startCamera() else showCameraOff()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth.currentUser?.uid?.let { badgeWatch = Badges.watchUnlocks(this, it, binding.root) }
        CustomerNav.bind(this, binding.customerNav, CustomerNav.Tab.SCAN)

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

        setupCodeEntry()
    }

    override fun onDestroy() {
        super.onDestroy()
        auth.currentUser?.uid?.let { Badges.stopWatching(it, badgeWatch) }
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
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
                binding.tvScanStatus.text = getString(com.sapotlokal.R.string.k_camera_active)
                var torchOn = false
                if (camera.cameraInfo.hasFlashUnit()) {
                    binding.btnFlash.setOnClickListener {
                        torchOn = !torchOn
                        camera.cameraControl.enableTorch(torchOn)
                        binding.btnFlash.alpha = if (torchOn) 1f else 0.6f
                    }
                    binding.btnFlash.alpha = 0.6f
                } else {
                    binding.btnFlash.visibility = android.view.View.INVISIBLE
                }
            } catch (e: Exception) {
                binding.tvScanStatus.text = getString(com.sapotlokal.R.string.k_camera_unavailable)
                Log.e("ScanActivity", "Camera bind failed", e)
                showError(getString(com.sapotlokal.R.string.k_could_not_start_camera))
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
                showError(getString(com.sapotlokal.R.string.k_could_not_check_code))
                isProcessing = false
            }
        )
    }

    private fun recordPointsAndShowSuccess(vendorID: String, vendorName: String) {
        val userID = auth.currentUser?.uid ?: run {
            showError(getString(com.sapotlokal.R.string.games_error_not_logged_in))
            isProcessing = false
            return
        }

        // One point-earning scan per shop per cooldown window (AppConfig.SHOP_VISIT_COOLDOWN_MINUTES).
        PointRepository.hasRecentShopVisit(
            userID   = userID,
            vendorID = vendorID,
            onResult = { recent ->
                if (recent) {
                    mainHandler.post {
                        showError(getString(com.sapotlokal.R.string.k_already_collected, vendorName))
                        isProcessing = false
                    }
                } else {
                    awardShopVisit(userID, vendorID, vendorName)
                }
            },
            onError = {
                mainHandler.post {
                    showError(getString(com.sapotlokal.R.string.k_could_not_check_visits))
                    isProcessing = false
                }
            }
        )
    }

    private fun awardShopVisit(userID: String, vendorID: String, vendorName: String) {
        PointRepository.recordShopVisit(
            userID   = userID,
            vendorID = vendorID,
            onResult = { result ->
                if (result != null) {
                    com.sapotlokal.util.FollowedShops.follow(this@ScanActivity, vendorID)
                    // Follow this shop for "now open" notifications
                    com.google.firebase.messaging.FirebaseMessaging.getInstance()
                        .subscribeToTopic("shop_${vendorID}")
                        .addOnSuccessListener {
                            Log.d("ScanActivity", "Subscribed to FCM topic: shop_${vendorID}")
                        }
                        .addOnFailureListener { e ->
                            Log.w("ScanActivity", "Could not subscribe to FCM topic: shop_${vendorID}", e)
                        }

                    mainHandler.post {
                        showSuccessOverlay(vendorName, result)
                    }
                } else {
                    mainHandler.post {
                        showError(getString(com.sapotlokal.R.string.k_points_not_saved))
                        isProcessing = false
                    }
                }
            }
        )
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun showSuccessOverlay(vendorName: String, result: PointRepository.VisitResult) {
        binding.tvPointsAwarded.text = if (result.rankBonus > 1.0) {
            getString(com.sapotlokal.R.string.scan_points_at_bonus, result.points, vendorName, com.sapotlokal.util.RankText.bonus(result.rankBonus))
        } else {
            getString(com.sapotlokal.R.string.scan_points_at, result.points, vendorName)
        }
        binding.tvXpAwarded.text = getString(com.sapotlokal.R.string.scan_xp, result.xp)
        binding.successOverlay.visibility = View.VISIBLE
        binding.successOverlay.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in))

        // Back to where the customer came from once they have read it.
        // Stay a little longer when a badge unlocked, so its snackbar can be read.
        mainHandler.postDelayed({ finish() }, if (result.badgesUnlocked.isEmpty()) 2500L else 4000L)
    }

    private fun showError(message: String) {
        binding.tvError.text       = message
        binding.tvError.visibility = View.VISIBLE

        // Hide error after 3 seconds so the camera can be used again
        mainHandler.postDelayed({
            binding.tvError.visibility = View.GONE
        }, 3000)
    }

    // ── Typed shop code ───────────────────────────────────────────────────────

    /** The camera is blocked: say so once in the camera card and point to the code box below. */
    private fun showCameraOff() {
        binding.tvScanStatus.text = getString(com.sapotlokal.R.string.k_camera_unavailable)
        binding.tvInstruction.text = getString(com.sapotlokal.R.string.scan_camera_off)
    }

    /** The code under the owner's QR, typed by hand. Same check as a scan. */
    private fun setupCodeEntry() {
        fun submit() {
            val code = binding.etShopCode.text?.toString()?.trim()?.uppercase().orEmpty()
            if (code.isNotEmpty() && !isProcessing) {
                isProcessing = true
                handleScannedValue(code)
            }
        }
        binding.btnSubmitCode.setOnClickListener { submit() }
        binding.etShopCode.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }
    }
}
