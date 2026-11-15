package com.sapotlokal.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * MissionDateUtils — Date and calendar utilities for SapotLokal Weekly Missions.
 *
 * Configured specifically for Asia/Kuala_Lumpur timezone (UTC+8).
 * Missions reset automatically every Sunday at 23:59:59 Asia/Kuala_Lumpur.
 */
object MissionDateUtils {

    val KL_ZONE: ZoneId = ZoneId.of("Asia/Kuala_Lumpur")

    /**
     * Returns the ISO-8601 week key in KL timezone, e.g. "2026-W44".
     */
    fun getWeekYearKey(timestampMillis: Long = ServerClock.now()): String {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestampMillis), KL_ZONE)
        val year = zdt.get(IsoFields.WEEK_BASED_YEAR)
        val week = zdt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return String.format(Locale.US, "%04d-W%02d", year, week)
    }

    /**
     * Returns the epoch milliseconds of Monday 00:00:00.000 of the current ISO week in KL timezone.
     */
    fun getWeekStartMillis(timestampMillis: Long = ServerClock.now()): Long {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestampMillis), KL_ZONE)
        val monday = zdt.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
        return monday.toInstant().toEpochMilli()
    }

    /**
     * Decodes the creation timestamp in milliseconds from a Firebase Realtime Database push key.
     * The first 8 characters encode the 48-bit timestamp.
     */
    fun extractTimestampFromPushKey(pushKey: String): Long {
        val pushChars = "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz"
        if (pushKey.length < 8) return 0L
        var time = 0L
        for (i in 0 until 8) {
            val c = pushKey[i]
            val index = pushChars.indexOf(c)
            if (index == -1) return 0L
            time = time * 64 + index
        }
        return time
    }

    /**
     * Returns the epoch milliseconds of Sunday 23:59:59 of the current ISO week in KL timezone.
     */
    fun getWeekExpiresAtMillis(timestampMillis: Long = ServerClock.now()): Long {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestampMillis), KL_ZONE)
        val sunday = zdt.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            .withHour(23)
            .withMinute(59)
            .withSecond(59)
            .withNano(999_000_000)
        return sunday.toInstant().toEpochMilli()
    }


    /** Same as [formatTimeRemaining] below, but worded in the language the customer chose. */
    fun formatTimeRemaining(
        context: android.content.Context,
        expiresAt: Long,
        nowMillis: Long = ServerClock.now()
    ): String {
        val diff = expiresAt - nowMillis
        return when {
            diff <= 0L -> context.getString(com.sapotlokal.R.string.time_expired)
            diff >= 2 * 86_400_000L -> context.getString(com.sapotlokal.R.string.time_days_left, (diff / 86_400_000L).toInt())
            diff >= 86_400_000L -> context.getString(com.sapotlokal.R.string.time_one_day_left)
            diff >= 2 * 3_600_000L -> context.getString(com.sapotlokal.R.string.time_hours_left, (diff / 3_600_000L).toInt())
            diff >= 3_600_000L -> context.getString(com.sapotlokal.R.string.time_one_hour_left)
            diff >= 2 * 60_000L -> context.getString(com.sapotlokal.R.string.time_mins_left, (diff / 60_000L).toInt())
            else -> context.getString(com.sapotlokal.R.string.time_ending_soon)
        }
    }

    /**
     * Formats the remaining time until expiration into a human-readable string.
     * e.g. "3 days left", "1 day left", "5 hours left", "Ending soon".
     */
    fun formatTimeRemaining(expiresAt: Long, nowMillis: Long = ServerClock.now()): String {
        val diff = expiresAt - nowMillis
        return when {
            diff <= 0L -> "Expired"
            diff >= 2 * 86_400_000L -> "${diff / 86_400_000L} days left"
            diff >= 86_400_000L -> "1 day left"
            diff >= 2 * 3_600_000L -> "${diff / 3_600_000L} hours left"
            diff >= 3_600_000L -> "1 hour left"
            diff >= 2 * 60_000L -> "${diff / 60_000L} mins left"
            else -> "Ending soon"
        }
    }

    /**
     * Returns "yyyy-MM-dd" date string in KL timezone.
     */
    fun getDayString(timestampMillis: Long = ServerClock.now()): String {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestampMillis), KL_ZONE)
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            zdt.year,
            zdt.monthValue,
            zdt.dayOfMonth
        )
    }

    /**
     * Returns true if the timestamp falls after 8:00 PM (20:00) in KL timezone.
     */
    fun isNightScan(timestampMillis: Long = ServerClock.now()): Boolean {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestampMillis), KL_ZONE)
        return zdt.hour >= 20
    }

    /**
     * Returns true if the timestamp falls on Saturday or Sunday in KL timezone.
     */
    fun isSaturdayOrSunday(timestampMillis: Long = ServerClock.now()): Boolean {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestampMillis), KL_ZONE)
        val dow = zdt.dayOfWeek
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
    }

    /**
     * Returns the DayOfWeek in KL timezone.
     */
    fun getDayOfWeek(timestampMillis: Long = ServerClock.now()): DayOfWeek {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestampMillis), KL_ZONE)
        return zdt.dayOfWeek
    }
}

