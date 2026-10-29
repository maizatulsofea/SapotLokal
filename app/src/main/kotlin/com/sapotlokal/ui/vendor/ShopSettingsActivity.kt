package com.sapotlokal.ui.vendor

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.sapotlokal.data.model.Vendor
import com.sapotlokal.data.repository.VendorRepository
import com.sapotlokal.databinding.ActivityShopSettingsBinding

/**
 * ShopSettingsActivity — Phase 6 Task 6.3:
 * Allows shop owners to view and edit their store details:
 * vendorName, vendorCategory, vendorAddress, vendorDescription.
 */
class ShopSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShopSettingsBinding
    private val auth = FirebaseAuth.getInstance()
    private var currentVendor: Vendor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShopSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val vendorID = intent.getStringExtra(VendorHomeActivity.EXTRA_VENDOR_ID) ?: ""
        val user = auth.currentUser

        if (user == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (vendorID.isNotBlank()) {
            loadVendorById(vendorID)
        } else {
            loadVendorByUserId(user.uid)
        }

        binding.btnSave.setOnClickListener {
            saveChanges()
        }
    }

    private fun loadVendorById(vendorID: String) {
        setLoading(true)
        VendorRepository.fetchVendorById(
            vendorID = vendorID,
            onResult = { vendor ->
                setLoading(false)
                if (vendor != null) {
                    populateFields(vendor)
                } else {
                    Toast.makeText(this, "Could not load shop details", Toast.LENGTH_SHORT).show()
                }
            },
            onError = { err ->
                setLoading(false)
                Toast.makeText(this, "Error: $err", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun loadVendorByUserId(userID: String) {
        setLoading(true)
        VendorRepository.fetchVendorByUserId(
            userID = userID,
            onResult = { vendor ->
                setLoading(false)
                if (vendor != null) {
                    populateFields(vendor)
                } else {
                    Toast.makeText(this, "Could not find shop for current user", Toast.LENGTH_SHORT).show()
                }
            },
            onError = { err ->
                setLoading(false)
                Toast.makeText(this, "Error: $err", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun populateFields(vendor: Vendor) {
        currentVendor = vendor
        binding.etShopName.setText(vendor.vendorName)
        binding.etShopCategory.setText(vendor.vendorCategory)
        binding.etShopAddress.setText(vendor.vendorAddress)
        binding.etShopDesc.setText(vendor.vendorDescription)
        binding.etSsmNumber.setText(vendor.ssmNumber)
    }

    private fun saveChanges() {
        val vendor = currentVendor ?: return

        val name = binding.etShopName.text.toString().trim()
        val category = binding.etShopCategory.text.toString().trim()
        val address = binding.etShopAddress.text.toString().trim()
        val desc = binding.etShopDesc.text.toString().trim()

        var hasError = false
        if (name.isEmpty()) {
            binding.tilShopName.error = "Shop name cannot be empty"
            hasError = true
        } else {
            binding.tilShopName.error = null
        }

        if (category.isEmpty()) {
            binding.tilShopCategory.error = "Shop category cannot be empty"
            hasError = true
        } else {
            binding.tilShopCategory.error = null
        }

        if (address.isEmpty()) {
            binding.tilShopAddress.error = "Shop address cannot be empty"
            hasError = true
        } else {
            binding.tilShopAddress.error = null
        }

        if (hasError) return

        val updatedVendor = vendor.copy(
            vendorName = name,
            vendorCategory = category,
            vendorAddress = address,
            vendorDescription = desc
        )

        setLoading(true)
        VendorRepository.updateVendor(updatedVendor) { success, errorMsg ->
            setLoading(false)
            if (success) {
                currentVendor = updatedVendor
                Toast.makeText(this, "Shop profile updated successfully! ✓", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, errorMsg ?: "Failed to update profile", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !loading
        binding.btnSave.text = if (loading) "" else "Save Changes"
    }
}

