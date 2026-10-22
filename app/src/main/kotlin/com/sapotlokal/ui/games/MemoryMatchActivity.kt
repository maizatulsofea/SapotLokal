package com.sapotlokal.ui.games

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.sapotlokal.AppConfig
import com.sapotlokal.R
import com.sapotlokal.data.repository.GamesRepository
import com.sapotlokal.databinding.ActivityMemoryMatchBinding

class MemoryMatchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMemoryMatchBinding
    private lateinit var userID: String
    private val handler = Handler(Looper.getMainLooper())

    private val cards = mutableListOf<Card>()
    private val cardViews = mutableListOf<TextView>()

    private var firstPick: Int? = null
    private var busy = false
    private var gameOver = false
    private var moves = 0
    private var matchedPairs = 0
    private var pendingSaveMoves = 0

    data class Card(
        val id: Int,
        val emoji: String,
        var faceUp: Boolean = false,
        var matched: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemoryMatchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val currentUID = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(this, R.string.games_error_not_logged_in, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        userID = currentUID

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.backButton.setOnClickListener { finish() }
        binding.newGameButton.setOnClickListener { startGame() }

        startGame()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun startGame() {
        handler.removeCallbacksAndMessages(null)
        cards.clear()
        firstPick = null
        busy = false
        gameOver = false
        moves = 0
        matchedPairs = 0

        binding.movesText.text = getString(R.string.games_memory_moves, moves)
        binding.statusText.text = ""
        binding.statusText.setOnClickListener(null)
        binding.newGameButton.isEnabled = true

        val deck = (GameConfig.MEMORY_EMOJIS + GameConfig.MEMORY_EMOJIS).shuffled()
        cards.addAll(deck.mapIndexed { idx, emoji -> Card(idx, emoji) })

        buildGrid()
        refreshInfo()
    }

    private fun refreshInfo() {
        GamesRepository.fetchStatus(userID) { status ->
            if (isFinishing || isDestroyed) return@fetchStatus
            if (status != null) {
                binding.infoText.text = getString(
                    R.string.games_memory_info,
                    status.memoryPointsToday,
                    AppConfig.MEMORY_MATCH_DAILY_CAP_POINTS
                )
            } else {
                binding.infoText.text = ""
            }
        }
    }

    private fun buildGrid() {
        binding.cardGrid.removeAllViews()
        cardViews.clear()
        binding.cardGrid.columnCount = GameConfig.MEMORY_COLUMNS

        val margin = dp(4)
        for (i in cards.indices) {
            val view = TextView(this).apply {
                gravity = Gravity.CENTER
                textSize = 32f
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply {
                    width = 0
                    height = dp(88)
                    setMargins(margin, margin, margin, margin)
                }
                setOnClickListener { onCardTapped(i) }
            }
            cardViews.add(view)
            binding.cardGrid.addView(view)
            showState(i)
        }
    }

    private fun onCardTapped(index: Int) {
        val card = cards[index]
        if (busy || gameOver || card.faceUp || card.matched) return

        card.faceUp = true
        flipTo(index)

        val first = firstPick
        if (first == null) {
            firstPick = index
            return
        }

        // Second card of a move
        firstPick = null
        moves++
        binding.movesText.text = getString(R.string.games_memory_moves, moves)

        if (cards[first].emoji == card.emoji) {
            cards[first].matched = true
            card.matched = true
            matchedPairs++
            handler.postDelayed({
                if (isFinishing || isDestroyed) return@postDelayed
                showState(first)
                showState(index)
                if (matchedPairs == GameConfig.MEMORY_EMOJIS.size) onWin()
            }, GameConfig.MEMORY_MATCH_REVEAL_DELAY_MS)
        } else {
            busy = true
            handler.postDelayed({
                if (isFinishing || isDestroyed) return@postDelayed
                cards[first].faceUp = false
                card.faceUp = false
                flipTo(first)
                flipTo(index)
                busy = false
            }, GameConfig.MEMORY_MISMATCH_DELAY_MS)
        }
    }

    private fun onWin() {
        if (gameOver) return
        gameOver = true
        pendingSaveMoves = moves
        saveWin()
    }

    private fun saveWin() {
        binding.newGameButton.isEnabled = false
        binding.statusText.text = getString(R.string.games_memory_saving)
        binding.statusText.setOnClickListener(null)

        GamesRepository.fetchStatus(userID) { status ->
            if (isFinishing || isDestroyed) return@fetchStatus
            if (status == null) return@fetchStatus showSaveFailed()

            val award = minOf(AppConfig.MEMORY_MATCH_REWARD_POINTS, status.memoryRoomToday)
            GamesRepository.recordMemoryWin(userID, award, status.memoryAttemptsTotal + 1) { ok ->
                if (isFinishing || isDestroyed) return@recordMemoryWin
                if (!ok) return@recordMemoryWin showSaveFailed()

                binding.statusText.text = when {
                    award <= 0 -> getString(R.string.games_memory_win_none, pendingSaveMoves)
                    award < AppConfig.MEMORY_MATCH_REWARD_POINTS ->
                        getString(R.string.games_memory_win_partial, pendingSaveMoves, award)
                    else -> getString(R.string.games_memory_win_full, pendingSaveMoves, award)
                }
                binding.newGameButton.isEnabled = true
                refreshInfo()
            }
        }
    }

    private fun showSaveFailed() {
        binding.statusText.text = getString(R.string.games_memory_save_failed)
        binding.statusText.setOnClickListener { saveWin() }
        binding.newGameButton.isEnabled = true
    }

    private fun flipTo(index: Int) {
        val view = cardViews[index]
        view.animate().scaleX(0f).setDuration(GameConfig.MEMORY_FLIP_HALF_MS).withEndAction {
            if (isFinishing || isDestroyed) return@withEndAction
            showState(index)
            view.animate().scaleX(1f).setDuration(GameConfig.MEMORY_FLIP_HALF_MS).start()
        }.start()
    }

    private fun showState(index: Int) {
        val card = cards[index]
        val view = cardViews[index]
        val background = GradientDrawable().apply { cornerRadius = dp(16).toFloat() }

        when {
            card.matched -> {
                view.text = card.emoji
                view.setTextColor(ContextCompat.getColor(this, R.color.game_card_front_text))
                background.setColor(ContextCompat.getColor(this, R.color.game_card_matched))
            }
            card.faceUp -> {
                view.text = card.emoji
                view.setTextColor(ContextCompat.getColor(this, R.color.game_card_front_text))
                background.setColor(ContextCompat.getColor(this, R.color.game_card_front))
                background.setStroke(dp(2), ContextCompat.getColor(this, R.color.game_card_front_stroke))
            }
            else -> {
                view.text = "?"
                view.setTextColor(ContextCompat.getColor(this, R.color.game_card_back_text))
                background.setColor(ContextCompat.getColor(this, R.color.game_card_back))
            }
        }
        view.background = background
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

