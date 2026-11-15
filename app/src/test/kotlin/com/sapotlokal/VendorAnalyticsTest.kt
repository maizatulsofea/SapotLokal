package com.sapotlokal

import com.sapotlokal.data.model.Point
import com.sapotlokal.data.model.PointSource
import com.sapotlokal.util.VendorAnalyticsCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

/** The shop owner's Home numbers (REFRACTOR.md Phase 10). */
class VendorAnalyticsTest {

    private fun kl(s: String) = ZonedDateTime.parse("$s+08:00[Asia/Kuala_Lumpur]").toInstant().toEpochMilli()

    private fun visit(user: String, points: Int, at: Long) = Point(
        userID = user, vendorID = "v1", pointEarnedAmount = points, pointSource = PointSource.SHOP_VISIT, timestamp = at
    )

    // Sunday 15 Nov 2026, 20:00: the week started Monday 9 Nov.
    private val now = kl("2026-11-15T20:00:00")

    @Test
    fun countsOnlyThisWeeksVisitsAndPoints() {
        val points = listOf(
            visit("a", 10, kl("2026-11-09T00:00:00")), // Monday 00:00, this week
            visit("a", 13, kl("2026-11-14T09:00:00")),
            visit("b", 20, kl("2026-11-15T19:00:00")),
            visit("c", 10, kl("2026-11-08T23:59:00"))  // last Sunday, not this week
        )
        val used = listOf(kl("2026-11-10T12:00:00"), kl("2026-11-15T18:00:00"), kl("2026-11-02T12:00:00"))
        val r = VendorAnalyticsCalculator.compute(points, voucherUsedTimes = used, nowMs = now)
        assertEquals(3, r.weekVisits)
        assertEquals(2, r.weekCustomers)
        assertEquals(43, r.weekPointsGiven)
        assertEquals(2, r.weekVouchersUsed) // the one from 2 Nov is last week's
    }

    @Test
    fun redemptionsAreNotVisits() {
        val spend = Point(userID = "a", vendorID = "v1", pointRedeemedAmount = 10, pointSource = PointSource.REDEMPTION, timestamp = now - 1000)
        val r = VendorAnalyticsCalculator.compute(listOf(spend), emptyList(), now)
        assertEquals(0, r.weekVisits)
        assertEquals(0, r.recentVisits.size)
    }

    @Test
    fun recentVisitsAreNewestFirstAndCapped() {
        val points = (1..40).map { visit("u$it", 10, now - it * 60_000L) }
        val r = VendorAnalyticsCalculator.compute(points, emptyList(), now)
        assertEquals(VendorAnalyticsCalculator.RECENT_LIMIT, r.recentVisits.size)
        assertEquals("u1", r.recentVisits.first().userID)
    }
}
