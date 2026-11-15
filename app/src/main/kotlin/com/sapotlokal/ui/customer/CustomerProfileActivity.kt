package com.sapotlokal.ui.customer

import android.content.Intent
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.AppConfig
import com.sapotlokal.R
import com.sapotlokal.databinding.ActivityCustomerProfileBinding
import com.sapotlokal.ui.auth.LoginActivity
import java.util.Locale

/**
 * CustomerProfileActivity — Phase 4 implementation.
 *
 * Displays:
 * 1. Profile header with user avatar, name, email, and current tier pill.
 * 2. Dual Points Metric Card clearly separating Spendable vs Lifetime Accumulated points.
 * 3. Tier level progression bar showing progress to the next Malaysian tier.
 * 4. Badges showcase grid displaying all 8 collectible badges (with color/grayscale distinction).
 * 5. Edit profile name functionality and sign out button routing to RolePickerActivity.
 */
class CustomerProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomerProfileBinding
    private val auth = FirebaseAuth.getInstance()
    private var userListener: ValueEventListener? = null

    private var currentUserId: String = ""
    private var currentUserName: String = ""
    private var badgeListener: com.google.firebase.database.ValueEventListener? = null

    private lateinit var badgeAdapter: BadgeGridAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        com.sapotlokal.util.AppLanguage.bindToggle(binding.languageToggle.root)
        // Me is a page opened from Home or Play: back arrow, no bottom bar.
        binding.customerNav.root.visibility = android.view.View.GONE

        val currentUser = auth.currentUser
        if (currentUser == null) {
            goToLogin()
            return
        }

        currentUserId = currentUser.uid
        binding.tvUserEmail.text = currentUser.email ?: ""

        setupBadgesGrid()
        setupListeners(currentUserId)
        bindMemberSince()
        setupActions()
    }

    /** Badges live on Me (REFRACTOR.md M2): 4 per row, 3 on narrow phones. Tap one for its sheet. */
    private fun setupBadgesGrid() {
        badgeAdapter = BadgeGridAdapter { badge -> Badges.showSheet(this, currentUserId, badge) }
        val columns = if (resources.configuration.screenWidthDp < 360) 3 else 4
        binding.rvBadges.layoutManager = GridLayoutManager(this, columns)
        binding.rvBadges.adapter = badgeAdapter
    }

    private fun listenToBadges(userId: String) {
        badgeListener = com.sapotlokal.data.repository.BadgeRepository.listen(userId) { states ->
            badgeAdapter.submitList(states)
            binding.tvBadgesUnlockedCount.text = getString(com.sapotlokal.R.string.badge_count, states.count { it.unlocked }, states.size)
        }
    }

    private fun setupListeners(userId: String) {
        // 1. User profile listener (/users/{userId})
        val userRef = FirebaseDatabase.getInstance(AppConfig.DATABASE_URL)
            .reference.child("users").child(userId)

        userListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val name = snapshot.child("userName").getValue(String::class.java)?.trim() ?: ""
                currentUserName = name
                binding.tvUserName.text = if (name.isNotBlank()) name else getString(com.sapotlokal.R.string.k_shopper)

                val email = snapshot.child("userEmail").getValue(String::class.java)?.trim() ?: ""
                if (email.isNotBlank()) {
                    binding.tvUserEmail.text = email
                }

                // Update initials
                if (name.isNotBlank()) {
                    val initials = name.split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .map { it.first().uppercaseChar() }
                        .joinToString("")
                    if (initials.isNotBlank()) {
                        binding.tvAvatarInitials.text = initials
                        binding.tvAvatarInitials.visibility = View.VISIBLE
                        binding.ivAvatar.visibility = View.GONE
                    } else {
                        binding.tvAvatarInitials.visibility = View.GONE
                        binding.ivAvatar.visibility = View.VISIBLE
                    }
                } else {
                    binding.tvAvatarInitials.visibility = View.GONE
                    binding.ivAvatar.visibility = View.VISIBLE
                }

                currentAvatarId = snapshot.child("avatar").getValue(String::class.java).orEmpty()
                com.sapotlokal.util.AvatarUi.bind(binding.ivAvatar, binding.tvAvatarInitials, currentAvatarId)

                val standing = com.sapotlokal.data.repository.XpRepository.standingOf(snapshot)
                binding.tvRankRow.text = getString(
                    com.sapotlokal.R.string.hu_rank_row, standing.rank.level, standing.rank.title,
                    java.text.NumberFormat.getIntegerInstance().format(standing.xp)
                )
                RankCard.maybeShowRankUp(this@CustomerProfileActivity, userId, standing)
            }

            override fun onCancelled(error: DatabaseError) {
                // Network error fallback
            }
        }
        userRef.addValueEventListener(userListener!!)

    }

    private fun bindMemberSince() {
        auth.currentUser?.metadata?.creationTimestamp?.takeIf { it > 0L }?.let { created ->
            val month = java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(java.util.Date(created))
            binding.tvMemberSince.text = getString(com.sapotlokal.R.string.k_member_since, month)
        }
    }

    private fun setupActions() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnHowItWorks.setOnClickListener { HowPoints.show(this) }
        binding.tvRankRow.setOnClickListener { startActivity(android.content.Intent(this, ChallengesActivity::class.java)) }

        binding.tvUserName.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(com.sapotlokal.R.string.k_edit_profile))
                .setItems(arrayOf(getString(com.sapotlokal.R.string.k_change_name), getString(com.sapotlokal.R.string.k_choose_avatar))) { _, which ->
                    if (which == 0) showEditNameDialog() else pickAvatar()
                }
                .show()
        }
        // Tapping the picture opens the avatar picker too.
        binding.ivAvatar.setOnClickListener { pickAvatar() }
        binding.tvAvatarInitials.setOnClickListener { pickAvatar() }

        binding.btnSignOut.setOnClickListener { com.sapotlokal.util.AccountActions.confirmLogout(this) }
        binding.rowChangePassword.setOnClickListener { com.sapotlokal.util.AccountActions.showChangePassword(this) }
        binding.rowDeleteAccount.setOnClickListener { com.sapotlokal.util.AccountActions.showDeleteAccount(this) }
    }

    private var currentAvatarId: String = ""

    private fun pickAvatar() {
        val uid = auth.currentUser?.uid ?: return
        com.sapotlokal.util.AvatarUi.showPicker(this, currentAvatarId) { chosen ->
            com.sapotlokal.util.AvatarUi.save(uid, chosen) { ok ->
                if (!ok) android.widget.Toast.makeText(this, getString(com.sapotlokal.R.string.k_avatar_save_failed), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditNameDialog() {
        val input = EditText(this).apply {
            setText(currentUserName)
            setSelection(text.length)
            setHint(getString(com.sapotlokal.R.string.k_enter_your_name))
            setSingleLine(true)
            setPadding(48, 36, 48, 36)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(com.sapotlokal.R.string.k_edit_name_title))
            .setView(input)
            .setPositiveButton(getString(com.sapotlokal.R.string.k_save)) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank()) {
                    FirebaseDatabase.getInstance(AppConfig.DATABASE_URL)
                        .reference.child("users").child(currentUserId).child("userName")
                        .setValue(newName)
                        .addOnSuccessListener {
                            Toast.makeText(this, getString(com.sapotlokal.R.string.k_profile_updated), Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, getString(com.sapotlokal.R.string.k_profile_update_failed), Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, getString(com.sapotlokal.R.string.k_name_empty), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(com.sapotlokal.R.string.k_cancel), null)
            .show()
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun onStop() {
        super.onStop()
        if (currentUserId.isNotBlank()) {
            userListener?.let {
                FirebaseDatabase.getInstance(AppConfig.DATABASE_URL)
                    .reference.child("users").child(currentUserId)
                    .removeEventListener(it)
            }
            userListener = null
            com.sapotlokal.data.repository.BadgeRepository.stopListening(currentUserId, badgeListener)
            badgeListener = null
        }
    }

    override fun onStart() {
        super.onStart()
        if (currentUserId.isNotBlank()) {
            if (userListener == null) {
                setupListeners(currentUserId)
            }
            if (badgeListener == null) listenToBadges(currentUserId)
        }
    }
}
