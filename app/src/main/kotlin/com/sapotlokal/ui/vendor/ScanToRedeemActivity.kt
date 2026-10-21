package com.sapotlokal.ui.vendor

import android.Manifest
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
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
import com.sapotlokal.data.model.VoucherUser
import com.sapotlokal.data.repository.VoucherRepository
import com.sapotlokal.databinding.ActivityScanRedeemBinding
import com.sapotlokal.util.isNetworkAvailable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * ScanToRedeemActivity — Phase 4: Shop owner redeems a customer's voucher.
 *
 * TWO WAYS TO SUBMIT A VOUCHER CODE:
 *   1. Camera scan (ML Kit) — preferred; same scanning pipeline as ScanActivity.
 *   2. Manual entry — type the voucher code in the text field and tap "Verify Code".
 *      This is a required fallback (build guide Section 6, "Redemption has a manual fallback").
 *
 * VALIDATION CHAIN (all against the live database — build guide Rule 43):
 *   VoucherRepository.validateVoucherForRedemption() checks, in order:
 *     1. Code exists in /voucherUsers
 *     2. vendorID matches THIS shop (voucher from another shop is rejected)
 *     3. status is "active" (not used/expired)
 *     4. voucherExpiryDate has not passed
 *     5. Customer's per-shop balance >= pointsRequiredSnapshot at scan time
 *
 * ON SUCCESS:
 *   Shows the success overlay: "Give: <reward name>" so the shop owner knows what to hand over.
 *   The vendor taps "Done" → VoucherRepository.redeemVoucher() atomically:
 *     - Sets voucherStatus="used"
 *     - Writes a pointRedeemedAmount ledger row
 *   Then the activity closes.
 *
 * WHY TWO-STEP (validate THEN confirm):
 *   The shop owner should SEE what they're giving before the redemption is committed.
 *   This prevents accidentally marking a voucher used without actually giving the reward.
 */
class ScanToRedeemActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanRedeemBinding
    private lateinit var cameraExecutor: ExecutorService

    private var vendorID: String = ""
    private val auth = FirebaseAuth.getInstance()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Prevents multiple simultaneous validations
    private var isProcessing = false

    // The validated voucher waiting for the "Done" tap
    private var pendingVoucherUser: VoucherUser? = null

    // ── Permission launcher ───────────────────────────────────────────────────

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else showError("Camera permission is required. Use manual entry below.")
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanRedeemBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        vendorID = intent.getStringExtra(VendorHomeActivity.EXTRA_VENDOR_ID) ?: ""
        if (vendorID.isEmpty()) { finish(); return }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Request camera or start directly
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setupManualEntry()
        setupSuccessOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    // ── CameraX ───────────────────────────────────────────────────────────────

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview  = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor, QrAnalyzer { scannedValue ->
                        if (!isProcessing) {
                            isProcessing = true
                            mainHandler.post { handleCode(scannedValue) }
                        }
                    })
                }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
                showError("Camera could not start. Use manual entry below.")
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

    // ── Manual entry ──────────────────────────────────────────────────────────

    private fun setupManualEntry() {
        binding.btnSubmitCode.setOnClickListener {
            val code = binding.etVoucherCode.text?.toString()?.trim() ?: ""
            if (code.isNotEmpty() && !isProcessing) {
                isProcessing = true
                handleCode(code)
            }
        }
        binding.etVoucherCode.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val code = binding.etVoucherCode.text?.toString()?.trim() ?: ""
                if (code.isNotEmpty() && !isProcessing) {
                    isProcessing = true
                    handleCode(code)
                }
                true
            } else false
        }
    }

    // ── Validation flow ───────────────────────────────────────────────────────

    private fun handleCode(code: String) {
        if (!isNetworkAvailable()) {
            showError("No internet connection. Please reconnect and try again.")
            isProcessing = false
            return
        }

        showLoading(true)

        VoucherRepository.validateVoucherForRedemption(
            voucherCode = code,
            vendorID    = vendorID,
            onValid     = { voucherUser, rewardName ->
                runOnUiThread {
                    showLoading(false)
                    showSuccessOverlay(voucherUser, rewardName)
                }
            },
            onInvalid = { reason ->
                runOnUiThread {
                    showLoading(false)
                    showError(reason)
                    isProcessing = false
                }
            },
            onError = { msg ->
                runOnUiThread {
                    showLoading(false)
                    showError("Could not verify voucher: $msg")
                    isProcessing = false
                }
            }
        )
    }

    // ── Success overlay ───────────────────────────────────────────────────────

    private fun setupSuccessOverlay() {
        binding.btnDoneRedeem.setOnClickListener {
            val vu = pendingVoucherUser ?: run {
                binding.successOverlay.visibility = View.GONE
                isProcessing = false
                return@setOnClickListener
            }
            pendingVoucherUser = null
            binding.btnDoneRedeem.isEnabled = false

            VoucherRepository.redeemVoucher(
                voucherUser = vu,
                onResult = { success ->
                    runOnUiThread {
                        binding.btnDoneRedeem.isEnabled = true
                        binding.successOverlay.visibility = View.GONE
                        if (success) {
                            finish()
                        } else {
                            showError("Redemption failed to save. Please try again.")
                            isProcessing = false
                        }
                    }
                }
            )
        }
    }

    private fun showSuccessOverlay(voucherUser: VoucherUser, rewardName: String) {
        pendingVoucherUser = voucherUser
        binding.tvSuccessReward.text = "Give: $rewardName"
        binding.tvSuccessCode.text   = "Code: ${voucherUser.voucherCode}"
        binding.successOverlay.visibility = View.VISIBLE
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun showError(message: String) {
        // Recycle the tvSuccessCode to display errors outside the overlay
        // (the layout has no separate tvError — we reuse a toast)
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSubmitCode.isEnabled = !loading
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    companion object {
        private const val TAG = "ScanToRedeem"
    }
}
