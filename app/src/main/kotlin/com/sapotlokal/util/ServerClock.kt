package com.sapotlokal.util

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.AppConfig

/**
 * The database's idea of "now" (CLAUDE.md rule 3: checks never trust the phone's clock).
 *
 * Firebase reports how far the phone's clock is from the server's at /.info/serverTimeOffset.
 * Adding it back means changing the phone's date or time moves nothing: day and week keys, game
 * limits, the scan cooldown and QR expiry all read [now]. The offset arrives when the app
 * connects, before any data read finishes, so a check that runs inside a read callback always
 * sees it. The database rules also reject written timestamps far from the server's time.
 */
object ServerClock {

    @Volatile private var offsetMs = 0L

    /** Starts listening for the offset; called once from SapotApp. */
    fun start() {
        FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).getReference(".info/serverTimeOffset")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    offsetMs = snapshot.getValue(Double::class.java)?.toLong() ?: 0L
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /** Server time in epoch milliseconds. */
    fun now(): Long = System.currentTimeMillis() + offsetMs
}
