package com.sapotlokal.ui.customer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.sapotlokal.data.model.Vendor
import com.sapotlokal.data.model.VoucherStatus
import com.sapotlokal.data.model.VoucherUser
import com.sapotlokal.data.repository.PointRepository
import com.sapotlokal.data.repository.VendorRepository
import com.sapotlokal.data.repository.VoucherRepository
import com.sapotlokal.databinding.ActivityMyVouchersBinding
import com.sapotlokal.databinding.DialogVoucherQrBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MyVouchersActivity — Phase 5: Displays customer vouchers, indicates "Best Choice",
 * shows locked status if balance dropped below required snapshot, and generates
 * high-resolution ZXing QR codes for vendor redemption.
 */
class MyVouchersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyVouchersBinding
    private val auth = FirebaseAuth.getInstance()

    private lateinit var voucherAdapter: VoucherAdapter
    private var vouchersListener: ValueEventListener? = null
    private var currentUserId: String = ""

    // In-memory caches to enrich voucher data
    private var rawVouchers: List<VoucherUser> = emptyList()
    private var vendorMap: Map<String, Vendor> = emptyMap()
    private var perShopBalance: Map<String, Int> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyVouchersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        currentUserId = user.uid

        setupRecyclerView()
        setupListeners()
        loadVendorData()
    }

    override fun onDestroy() {
        super.onDestroy()
        vouchersListener?.let {
            VoucherRepository.removeVouchersListener(currentUserId, it)
        }
    }

    private fun setupRecyclerView() {
        voucherAdapter = VoucherAdapter { item ->
            handleVoucherClick(item)
        }
        binding.rvVouchers.layoutManager = LinearLayoutManager(this)
        binding.rvVouchers.adapter = voucherAdapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnExploreShops.setOnClickListener {
            startActivity(Intent(this, FindShopsActivity::class.java))
            finish()
        }

        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, _ ->
            refreshVoucherList()
        }
    }

    private fun loadVendorData() {
        binding.pbLoading.visibility = View.VISIBLE

        // 1. Fetch vendor directory for shop names
        VendorRepository.fetchAllVendors(
            onResult = { vendors ->
                vendorMap = vendors.associateBy { it.vendorID }
                loadUserPoints()
            },
            onError = {
                loadUserPoints()
            }
        )
    }

    private fun loadUserPoints() {
        // 2. Fetch point ledger to compute per-shop balances
        PointRepository.fetchAllPointsForUser(
            userID = currentUserId,
            onResult = { balances, _ ->
                perShopBalance = balances
                startListeningToVouchers()
            },
            onError = {
                startListeningToVouchers()
            }
        )
    }

    private fun startListeningToVouchers() {
        vouchersListener = VoucherRepository.listenToVouchersForUser(
            userID = currentUserId,
            onUpdate = { vouchers ->
                rawVouchers = vouchers
                binding.pbLoading.visibility = View.GONE
                refreshVoucherList()
            },
            onError = { errorMsg ->
                binding.pbLoading.visibility = View.GONE
                Toast.makeText(this, "Could not load vouchers: $errorMsg", Toast.LENGTH_SHORT).show()
                refreshVoucherList()
            }
        )
    }

    private fun refreshVoucherList() {
        val nowMs = System.currentTimeMillis()

        // Group vouchers by vendor to compute "Best Choice" for each shop
        // "Best Choice" is the active, unlocked voucher with the highest pointsRequiredSnapshot
        val bestChoiceVoucherIds = mutableSetOf<String>()
        val byVendor = rawVouchers.groupBy { it.vendorID }

        for ((vendorId, list) in byVendor) {
            val balance = perShopBalance[vendorId] ?: 0
            val eligibleActive = list.filter { vu ->
                vu.voucherStatus == VoucherStatus.ACTIVE &&
                (vu.voucherExpiryDate == 0L || vu.voucherExpiryDate > nowMs) &&
                balance >= vu.pointsRequiredSnapshot
            }
            val best = eligibleActive.maxByOrNull { it.pointsRequiredSnapshot }
            best?.let { bestChoiceVoucherIds.add(it.voucherUserID) }
        }

        val uiList = rawVouchers.map { vu ->
            val vendor = vendorMap[vu.vendorID]
            val vendorName = vendor?.vendorName ?: vu.vendorID
            val balance = perShopBalance[vu.vendorID] ?: 0
            val isLocked = vu.voucherStatus == VoucherStatus.ACTIVE &&
                           (vu.voucherExpiryDate == 0L || vu.voucherExpiryDate > nowMs) &&
                           balance < vu.pointsRequiredSnapshot

            VoucherUiModel(
                voucherUser     = vu,
                vendorName      = vendorName,
                isBestChoice    = vu.voucherUserID in bestChoiceVoucherIds,
                isLocked        = isLocked,
                currentBalance  = balance
            )
        }

        // Apply tab filtering
        val filteredList = when {
            binding.chipFilterActive.isChecked -> {
                uiList.filter {
                    it.voucherUser.voucherStatus == VoucherStatus.ACTIVE &&
                    (it.voucherUser.voucherExpiryDate == 0L || it.voucherUser.voucherExpiryDate > nowMs)
                }
            }
            binding.chipFilterRedeemed.isChecked -> {
                uiList.filter {
                    it.voucherUser.voucherStatus == VoucherStatus.USED ||
                    (it.voucherUser.voucherExpiryDate in 1 until nowMs) ||
                    it.voucherUser.voucherStatus == VoucherStatus.EXPIRED
                }
            }
            else -> uiList
        }

        voucherAdapter.submitList(filteredList)

        if (filteredList.isEmpty()) {
            binding.rvVouchers.visibility = View.GONE
            binding.llEmptyState.visibility = View.VISIBLE
        } else {
            binding.rvVouchers.visibility = View.VISIBLE
            binding.llEmptyState.visibility = View.GONE
        }
    }

    private fun handleVoucherClick(item: VoucherUiModel) {
        val vu = item.voucherUser
        val nowMs = System.currentTimeMillis()

        if (vu.voucherStatus == VoucherStatus.USED) {
            Toast.makeText(this, "This voucher has already been redeemed.", Toast.LENGTH_SHORT).show()
            return
        }

        if (vu.voucherExpiryDate in 1 until nowMs || vu.voucherStatus == VoucherStatus.EXPIRED) {
            Toast.makeText(this, "This voucher has expired.", Toast.LENGTH_SHORT).show()
            return
        }

        if (item.isLocked) {
            val needed = vu.pointsRequiredSnapshot - item.currentBalance
            AlertDialog.Builder(this)
                .setTitle("Voucher Locked")
                .setMessage(
                    "You currently have ${item.currentBalance} points at ${item.vendorName}, " +
                    "but this voucher requires ${vu.pointsRequiredSnapshot} points.\n\n" +
                    "Earn $needed more points by visiting ${item.vendorName} to unlock!"
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }

        // Voucher is active and valid: show the QR modal dialog
        showVoucherQrDialog(item)
    }

    private fun showVoucherQrDialog(item: VoucherUiModel) {
        val dialogBinding = DialogVoucherQrBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.tvDialogShopName.text = item.vendorName
        dialogBinding.tvDialogRewardTitle.text = item.voucherUser.voucherTitle.ifBlank { "Reward Voucher" }
        dialogBinding.tvDialogVoucherCode.text = item.voucherUser.voucherCode

        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val expiryStr = if (item.voucherUser.voucherExpiryDate > 0L) {
            "Valid until " + dateFormat.format(Date(item.voucherUser.voucherExpiryDate))
        } else {
            "No expiry date"
        }
        dialogBinding.tvDialogExpiry.text = expiryStr

        dialogBinding.btnDialogClose.setOnClickListener { dialog.dismiss() }

        // Generate ZXing QR code bitmap asynchronously
        dialogBinding.pbQrLoading.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.Default) {
            val qrBitmap = generateQrBitmap(item.voucherUser.voucherCode, 512)
            withContext(Dispatchers.Main) {
                dialogBinding.pbQrLoading.visibility = View.GONE
                if (qrBitmap != null) {
                    dialogBinding.ivVoucherQr.setImageBitmap(qrBitmap)
                } else {
                    Toast.makeText(this@MyVouchersActivity, "Could not generate QR code", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun generateQrBitmap(value: String, size: Int): Bitmap? {
        return try {
            val matrix: BitMatrix = MultiFormatWriter().encode(
                value, BarcodeFormat.QR_CODE, size, size
            )
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}

