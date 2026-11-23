package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdvanceDeclineManagerTest {

    @Test
    fun parseNseAllIndicesJson_doesNotConfuseNifty50WithNifty500OrThematicIndices() {
        val samplePayload = """
            {
              "timestamp": "13-Nov-2026 15:30:00",
              "data": [
                {"index": "NIFTY 50", "advances": "11", "declines": "38", "unchanged": "1"},
                {"index": "NIFTY NEXT 50", "advances": "9", "declines": "41", "unchanged": "0"},
                {"index": "NIFTY 50 VALUE 20", "advances": "4", "declines": "16", "unchanged": "0"},
                {"index": "NIFTY MIDCAP 150", "advances": "47", "declines": "103", "unchanged": "0"},
                {"index": "NIFTY SMALLCAP 250", "advances": "92", "declines": "158", "unchanged": "0"},
                {"index": "NIFTY 500", "advances": "158", "declines": "340", "unchanged": "3"},
                {"index": "NIFTY 500 MULTICAP 50:25:25", "advances": "160", "declines": "340", "unchanged": "1"}
              ]
            }
        """.trimIndent()

        val parsed = AdvanceDeclineManager.parseNseAllIndicesJson(samplePayload)
        assertNotNull(parsed)
        parsed!!

        assertEquals("2026-11-13", parsed.tradeDateStr)
        assertEquals(Triple(11, 38, 1), parsed.nifty50)
        assertEquals(Triple(9, 41, 0), parsed.niftyNext50)
        assertEquals(Triple(47, 103, 0), parsed.niftyMidcap150)
        assertEquals(Triple(92, 158, 0), parsed.niftySmallcap250)
        assertEquals(Triple(158, 340, 3), parsed.nifty500)
    }

    @Test
    fun parseNseAllIndicesJson_rejectsOutOfBoundsNifty50Count() {
        val corruptedPayload = """
            {
              "timestamp": "13-Nov-2026 15:30:00",
              "data": [
                {"index": "NIFTY 50", "advances": "158", "declines": "340", "unchanged": "3"}
              ]
            }
        """.trimIndent()

        val parsed = AdvanceDeclineManager.parseNseAllIndicesJson(corruptedPayload)
        assertNull(parsed)
    }

    @Test
    fun tradingDates_neverReturnWeekendsAndSnapSaturdayToFriday() {
        assertTrue(AdvanceDeclineManager.isWeekendIso("2026-11-14"))
        assertTrue(AdvanceDeclineManager.isWeekendIso("2026-11-15"))
        assertFalse(AdvanceDeclineManager.isWeekendIso("2026-11-13"))

        assertEquals("2026-11-13", AdvanceDeclineManager.parseNseTradeDateIso("14-Nov-2026 10:00:00"))
        assertEquals("2026-11-13", AdvanceDeclineManager.parseNseTradeDateIso("13-Nov-2026 15:30:00"))

        val istFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        val saturdayMorningMs = istFormat.parse("2026-11-14 06:55")!!.time
        assertEquals("2026-11-13", AdvanceDeclineManager.mostRecentWeekdayIsoIst(saturdayMorningMs))
    }

    @Test
    fun isValidRecord_rejectsLegacyPollutedNifty50Record() {
        val poisonedRecord = DailyAdvanceDeclineRecord(
            dateStr = "2026-11-13",
            timestamp = 0L,
            bseSensex = AdvanceDeclineManager.buildSegment("bseSensex", "BSE Sensex", 9, 21, 0),
            nifty50 = IndexAdvanceDecline("nifty50", "Nifty 50", 158, 340, 3, 50, 0.46, 31.7),
            niftyNext50 = AdvanceDeclineManager.buildSegment("niftyNext50", "Nifty Next 50", 9, 41, 0),
            niftyMidcap150 = AdvanceDeclineManager.buildSegment("niftyMidcap150", "Nifty Midcap 150", 47, 103, 0),
            niftySmallcap250 = AdvanceDeclineManager.buildSegment("niftySmallcap250", "Nifty Smallcap 250", 92, 158, 0),
            nifty500 = AdvanceDeclineManager.buildSegment("nifty500", "Nifty 500", 160, 339, 1),
            nifty1000 = AdvanceDeclineManager.buildSegment("nifty1000", "Nifty 1000", 359, 639, 2),
            allNseEquity = AdvanceDeclineManager.buildSegment("allNseEquity", "All NSE Equity", 1107, 1778, 42)
        )
        assertFalse(AdvanceDeclineManager.isValidRecord(poisonedRecord))
    }

    @Test
    fun historicalAssets_areAuthenticAccurateWithValidTotalsAndNoWeekends() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val records = AdvanceDeclineManager.getAllHistoricalRecords(context)

        assertTrue("Expected >2550 trading days since 2016-04-01", records.size >= 2550)
        assertEquals("2016-04-01", records.first().dateStr)
        assertEquals("2026-11-13", records.last().dateStr)

        for (rec in records) {
            assertFalse("Weekend date found in timeline: ${rec.dateStr}", AdvanceDeclineManager.isWeekendIso(rec.dateStr))
            assertTrue("Invalid record integrity on ${rec.dateStr}", AdvanceDeclineManager.isValidRecord(rec))
            for (seg in rec.getAllSegments()) {
                assertEquals(
                    "adv+dec+unc != total for ${seg.indexKey} on ${rec.dateStr}",
                    seg.totalConstituents,
                    seg.advances + seg.declines + seg.unchanged
                )
            }
        }

        // Verify ALL historical subpages/charts begin on or by early April 2016
        val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val vixAll = IndiaVixManager.getHistoricalSeries("ALL", context)
        val tmmiAll = MarketMoodIndexEngine.getHistoricalTickertapeSeries("ALL", context)
        val gmmiAll = MarketMoodIndexEngine.getHistoricalGoodreturnsSeries("ALL", context)
        val adAll = AdvanceDeclineManager.getHistoricalSeries("nifty50", "ALL", false, context)

        assertTrue("VIX ALL start (${isoFmt.format(vixAll.first().first)}) should be in Apr 2016", isoFmt.format(vixAll.first().first).startsWith("2016-04"))
        assertTrue("TMMI ALL start (${isoFmt.format(tmmiAll.first().first)}) should be in Apr 2016", isoFmt.format(tmmiAll.first().first).startsWith("2016-04"))
        assertTrue("GMMI ALL start (${isoFmt.format(gmmiAll.first().first)}) should be in Apr 2016", isoFmt.format(gmmiAll.first().first).startsWith("2016-04"))
        assertTrue("A/D ALL start (${isoFmt.format(adAll.first().first)}) should be in Apr 2016", isoFmt.format(adAll.first().first).startsWith("2016-04"))
        assertTrue("Expected >= 2550 VIX ALL points", vixAll.size >= 2550)
        assertTrue("Expected >= 2550 GMMI ALL points", gmmiAll.size >= 2550)
        assertTrue("Expected >= 2550 A/D ALL points", adAll.size >= 2550)

        // Verify authentic ground-truth dates
        val nov12 = records.first { it.dateStr == "2026-11-12" }
        assertEquals(20, nov12.nifty50.advances)
        assertEquals(30, nov12.nifty50.declines)
        assertEquals(0, nov12.nifty50.unchanged)
        assertEquals(50, nov12.nifty50.totalConstituents)

        val nov13 = records.last()
        assertEquals(11, nov13.nifty50.advances)
        assertEquals(38, nov13.nifty50.declines)
        assertEquals(1, nov13.nifty50.unchanged)
        assertEquals(50, nov13.nifty50.totalConstituents)
        assertEquals(30, nov13.bseSensex.totalConstituents)
        assertEquals(500, nov13.nifty500.totalConstituents)
        assertEquals(1000, nov13.nifty1000.totalConstituents)
    }
}
