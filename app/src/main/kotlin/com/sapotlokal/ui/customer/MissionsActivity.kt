package com.sapotlokal.ui.customer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.sapotlokal.R
import com.sapotlokal.data.model.MissionDef
import com.sapotlokal.data.model.MissionState
import com.sapotlokal.data.repository.MissionRepository
import com.sapotlokal.databinding.ActivityMissionsBinding
import com.sapotlokal.databinding.ItemMissionRowBinding
import com.sapotlokal.util.MissionDateUtils

/**
 * Missions (REFRACTOR.md Phase 5): three weekly missions that reset every Monday 00:00 Malaysia
 * time, and three lifetime missions. A finished mission shows "Claim N XP"; claiming pays the XP
 * through MissionRepository.claim (one write, payable once). An unfinished one opens the place to
 * make progress.
 */
class MissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMissionsBinding
    private var listening: MissionRepository.Listening? = null
    private var current: List<MissionState> = emptyList()
    private val claiming = mutableSetOf<MissionDef>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }
        binding.tvResets.text = MissionDateUtils.formatTimeRemaining(this, MissionDateUtils.getWeekExpiresAtMillis())

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }
        listening = MissionRepository.listen(uid) { missions ->
            current = missions
            binding.progressBar.visibility = View.GONE
            render(uid)
        }
    }

    private fun render(uid: String) {
        fill(binding.weeklyRows, current.filter { it.def.weekly }, uid)
        fill(binding.lifetimeRows, current.filter { !it.def.weekly }, uid)
    }

    private fun fill(container: LinearLayout, missions: List<MissionState>, uid: String) {
        container.removeAllViews()
        missions.forEach { m -> container.addView(row(container, m, uid).root) }
    }

    private fun row(parent: LinearLayout, m: MissionState, uid: String): ItemMissionRowBinding {
        val b = ItemMissionRowBinding.inflate(LayoutInflater.from(this), parent, false)
        val name = getString(m.def.nameRes, m.def.target)
        b.ivIcon.setImageResource(m.def.iconRes)
        b.tvName.text = name
        b.tvProgress.text = getString(R.string.mission_progress, m.progressShown, m.def.target)
        b.bar.max = m.def.target
        b.bar.setProgressCompat(m.progressShown, false)

        b.tvXp.visibility = View.GONE
        b.btnClaim.visibility = View.GONE
        b.tvDone.visibility = View.GONE
        when {
            m.claimed -> {
                b.tvDone.visibility = View.VISIBLE
                b.root.contentDescription = getString(R.string.mission_row_done_cd, name)
            }
            m.claimable -> {
                b.btnClaim.visibility = View.VISIBLE
                b.btnClaim.text = getString(R.string.mission_claim, m.def.xp)
                b.btnClaim.isEnabled = m.def !in claiming
                b.btnClaim.setOnClickListener { claim(uid, m) }
            }
            else -> {
                b.tvXp.visibility = View.VISIBLE
                b.tvXp.text = getString(R.string.mission_xp, m.def.xp)
                b.root.contentDescription = getString(R.string.mission_row_cd, name, m.progressShown, m.def.target, m.def.xp)
                b.root.setOnClickListener { start(m.def.startsAt) }
            }
        }
        return b
    }

    private fun claim(uid: String, m: MissionState) {
        if (!claiming.add(m.def)) return
        render(uid)
        MissionRepository.claim(uid, m, current) { award ->
            claiming.remove(m.def)
            if (isFinishing || isDestroyed) return@claim
            if (award == null) {
                Toast.makeText(this, R.string.k_could_not_claim, Toast.LENGTH_SHORT).show()
                render(uid)
            } else {
                Toast.makeText(this, getString(R.string.k_plus_level_points, award.amount), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun start(where: MissionDef.Start) {
        val target = when (where) {
            MissionDef.Start.SCAN -> ScanActivity::class.java
            MissionDef.Start.SHOPS -> FindShopsActivity::class.java
            MissionDef.Start.GAMES -> com.sapotlokal.ui.games.GamesMenuActivity::class.java
            MissionDef.Start.MISSIONS -> return
        }
        startActivity(Intent(this, target))
    }

    override fun onDestroy() {
        super.onDestroy()
        listening?.stop()
    }
}
