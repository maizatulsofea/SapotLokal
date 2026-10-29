package com.sapotlokal.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.AppConfig
import com.sapotlokal.data.model.Challenge
import com.sapotlokal.data.model.ChallengeWithProgress
import com.sapotlokal.data.model.Game
import com.sapotlokal.data.model.Point
import com.sapotlokal.data.model.UserChallenge
import com.sapotlokal.data.model.UserChallengeStatus

/**
 * ChallengeRepository — reads and updates challenges and customer progress.
 *
 * Implements Phase 6 Task 6.1:
 * - /challenges: Global definitions of available challenges.
 * - /userChallenges: Per-user tracking and completion status.
 * - Automatic progress synchronization against verified ledger rows in /points and /games.
 * - Atomic one-time reward claim writing to /points.
 */
object ChallengeRepository {

    private val db = FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).reference

    private val DEFAULT_CHALLENGES = listOf(
        Challenge(
            challengeID = "ch_visit_3",
            challengeTitle = "Local Explorer",
            challengeDescription = "Visit 3 different local shops and scan their QR code",
            challengeTargetValue = 3,
            challengeRewardPoint = 30,
            isActive = true
        ),
        Challenge(
            challengeID = "ch_games_3",
            challengeTitle = "Game Master",
            challengeDescription = "Play mini-games (Spin Wheel or Memory Match) 3 times",
            challengeTargetValue = 3,
            challengeRewardPoint = 25,
            isActive = true
        ),
        Challenge(
            challengeID = "ch_points_100",
            challengeTitle = "Loyal Supporter",
            challengeDescription = "Earn a total of 100 lifetime points across local shops",
            challengeTargetValue = 100,
            challengeRewardPoint = 50,
            isActive = true
        )
    )

    /**
     * Seeds the default challenges in /challenges if none exist yet.
     */
    fun seedDefaultChallenges(onComplete: () -> Unit = {}) {
        db.child("challenges").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                    val updates = hashMapOf<String, Any>()
                    for (ch in DEFAULT_CHALLENGES) {
                        updates["challenges/${ch.challengeID}"] = ch
                    }
                    db.updateChildren(updates).addOnCompleteListener { onComplete() }
                } else {
                    onComplete()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onComplete()
            }
        })
    }

    /**
     * Evaluates live customer progress against all active challenges and updates /userChallenges.
     * Progress is derived directly from the verified database ledger:
     * - Distinct shops visited (/points with non-blank vendorID and pointEarnedAmount > 0)
     * - Games played count (/games)
     * - Lifetime earned points (/points)
     */
    fun syncUserProgress(userID: String, onComplete: () -> Unit = {}) {
        if (userID.isBlank()) return

        seedDefaultChallenges {
            db.child("challenges").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(challengesSnap: DataSnapshot) {
                    val challenges = challengesSnap.children
                        .mapNotNull { it.getValue(Challenge::class.java) }
                        .filter { it.isActive }

                    if (challenges.isEmpty()) {
                        onComplete()
                        return
                    }

                    // Read user's existing userChallenges
                    db.child("userChallenges")
                        .orderByChild("userID")
                        .equalTo(userID)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(userChallengesSnap: DataSnapshot) {
                                val existingMap = mutableMapOf<String, UserChallenge>()
                                for (c in userChallengesSnap.children) {
                                    val uc = c.getValue(UserChallenge::class.java) ?: continue
                                    existingMap[uc.challengeID] = uc
                                }

                                // Gather live stats from /points and /games
                                db.child("points").orderByChild("userID").equalTo(userID)
                                    .addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(pointsSnap: DataSnapshot) {
                                            val visitedShops = mutableSetOf<String>()
                                            var lifetimePoints = 0

                                            for (pChild in pointsSnap.children) {
                                                val p = pChild.getValue(Point::class.java) ?: continue
                                                if (p.vendorID.isNotBlank() && p.pointEarnedAmount > 0) {
                                                    visitedShops.add(p.vendorID)
                                                }
                                                lifetimePoints += p.pointEarnedAmount
                                            }

                                            db.child("games").orderByChild("userID").equalTo(userID)
                                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                                    override fun onDataChange(gamesSnap: DataSnapshot) {
                                                        var gamesCount = 0
                                                        for (gChild in gamesSnap.children) {
                                                            val g = gChild.getValue(Game::class.java) ?: continue
                                                            if (g.gameAttempt > 0 || g.gamePointsRewarded >= 0) {
                                                                gamesCount++
                                                            }
                                                        }

                                                        // Now compute and update userChallenges
                                                        val updates = hashMapOf<String, Any?>()

                                                        for (ch in challenges) {
                                                            val existing = existingMap[ch.challengeID]
                                                            val target = ch.challengeTargetValue

                                                            val rawValue = when (ch.challengeID) {
                                                                "ch_visit_3" -> visitedShops.size
                                                                "ch_games_3" -> gamesCount
                                                                "ch_points_100" -> lifetimePoints
                                                                else -> 0
                                                            }

                                                            val currentValue = rawValue.coerceAtMost(target)
                                                            val currentStatus = existing?.userChallengeStatus ?: UserChallengeStatus.IN_PROGRESS

                                                            val newStatus = when {
                                                                currentStatus == UserChallengeStatus.CLAIMED -> UserChallengeStatus.CLAIMED
                                                                currentValue >= target -> UserChallengeStatus.COMPLETED
                                                                else -> UserChallengeStatus.IN_PROGRESS
                                                            }

                                                            val key = existing?.userChallengeID?.ifBlank { null }
                                                                ?: "${userID}_${ch.challengeID}"

                                                            val record = UserChallenge(
                                                                userChallengeID = key,
                                                                userID = userID,
                                                                challengeID = ch.challengeID,
                                                                currentValue = currentValue,
                                                                userChallengeStatus = newStatus
                                                            )

                                                            updates["userChallenges/$key"] = record
                                                        }

                                                        if (updates.isNotEmpty()) {
                                                            db.updateChildren(updates).addOnCompleteListener {
                                                                onComplete()
                                                            }
                                                        } else {
                                                            onComplete()
                                                        }
                                                    }

                                                    override fun onCancelled(error: DatabaseError) {
                                                        onComplete()
                                                    }
                                                })
                                        }

                                        override fun onCancelled(error: DatabaseError) {
                                            onComplete()
                                        }
                                    })
                            }

                            override fun onCancelled(error: DatabaseError) {
                                onComplete()
                            }
                        })
                }

                override fun onCancelled(error: DatabaseError) {
                    onComplete()
                }
            })
        }
    }

    /**
     * Listens in real time to challenges and user's progress.
     */
    fun listenToChallengesWithProgress(
        userID: String,
        onUpdate: (List<ChallengeWithProgress>) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(userChallengesSnap: DataSnapshot) {
                val progressMap = mutableMapOf<String, UserChallenge>()
                for (child in userChallengesSnap.children) {
                    val uc = child.getValue(UserChallenge::class.java) ?: continue
                    progressMap[uc.challengeID] = uc
                }

                db.child("challenges").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(challengesSnap: DataSnapshot) {
                        val list = challengesSnap.children
                            .mapNotNull { it.getValue(Challenge::class.java) }
                            .filter { it.isActive }
                            .map { challenge ->
                                ChallengeWithProgress(
                                    challenge = challenge,
                                    userChallenge = progressMap[challenge.challengeID]
                                )
                            }
                        onUpdate(list)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        onError(error.message)
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }

        db.child("userChallenges")
            .orderByChild("userID")
            .equalTo(userID)
            .addValueEventListener(listener)

        return listener
    }

    fun removeListener(listener: ValueEventListener) {
        db.child("userChallenges").removeEventListener(listener)
    }

    /**
     * Atomically claims a completed challenge:
     * 1. Marks userChallengeStatus = "claimed"
     * 2. Writes an earned ledger entry into /points with pointSource = "challenge"
     */
    fun claimReward(
        userID: String,
        challenge: Challenge,
        userChallenge: UserChallenge,
        onResult: (success: Boolean, errorMsg: String?) -> Unit
    ) {
        if (userChallenge.userChallengeStatus == UserChallengeStatus.CLAIMED) {
            onResult(false, "Challenge reward already claimed")
            return
        }

        if (userChallenge.currentValue < challenge.challengeTargetValue &&
            userChallenge.userChallengeStatus != UserChallengeStatus.COMPLETED
        ) {
            onResult(false, "Challenge is not completed yet")
            return
        }

        val pointRow = PointRepository.newChallengePointEntry(userID, challenge.challengeRewardPoint)
        if (pointRow == null) {
            onResult(false, "Could not generate point transaction")
            return
        }

        val recordKey = userChallenge.userChallengeID.ifBlank { "${userID}_${challenge.challengeID}" }
        val updatedUserChallenge = userChallenge.copy(
            userChallengeID = recordKey,
            userID = userID,
            challengeID = challenge.challengeID,
            currentValue = challenge.challengeTargetValue,
            userChallengeStatus = UserChallengeStatus.CLAIMED
        )

        val updates = hashMapOf<String, Any?>(
            "userChallenges/$recordKey" to updatedUserChallenge,
            "points/${pointRow.pointID}" to pointRow
        )

        db.updateChildren(updates)
            .addOnSuccessListener {
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                onResult(false, e.message)
            }
    }
}

