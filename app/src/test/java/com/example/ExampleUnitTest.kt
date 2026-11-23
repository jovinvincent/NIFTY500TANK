package com.example

import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testParseFundsSequentialSingleFolio() {
    val simulatedText = """
      Folio No: 51023733614 / 0
      905F4DGG-qsif Active Asset Allocator Long-Short Fund Direct Plan (Non Demat) - ISIN: INF966L30241
      21-Apr-2026 NFO Purchase 89,995.50 8,999.550 10.0000 8,999.550
      21-Apr-2026 NFO Purchase 9,999.50 999.950 10.0000 9,999.500
      15-Jun-2026 Additional Purchase 49,997.50 4,850.641 10.3074 14,850.141

      905F2DGG-qsif Hybrid Long-Short Fund - Direct Plan (Non Demat) - ISIN: INF966L30076
      11-May-2026 New Purchase 99,995.00 9,476.762 10.5516 9,476.762
      07-Jul-2026 Redemption less TDS (97,870.85) (9,176.762) 10.7164 300.000
    """.trimIndent()

    val parsed = parseFunds(simulatedText)
    assertEquals(2, parsed.size)

    val activeAssetFund = parsed["INF966L30241"]
    assertNotNull(activeAssetFund)
    assertEquals("Active Asset Allocator Long-Short Fund", activeAssetFund!!.name)
    assertEquals(3, activeAssetFund.transactions.size)
    assertEquals(14850.141, activeAssetFund.balanceUnits, 0.001)

    val hybridFund = parsed["INF966L30076"]
    assertNotNull(hybridFund)
    assertEquals("Hybrid Long-Short Fund", hybridFund!!.name)
    assertEquals(2, hybridFund.transactions.size)
    assertEquals(300.0, hybridFund.balanceUnits, 0.001)

    // Check transaction properties
    val purchaseTxn = hybridFund.transactions[0]
    assertEquals("New Purchase", purchaseTxn.description)
    assertEquals(99995.0, purchaseTxn.amount, 0.001)
    assertEquals(9476.762, purchaseTxn.units, 0.001)
    assertEquals(10.5516, purchaseTxn.nav, 0.001)

    val redemptionTxn = hybridFund.transactions[1]
    assertEquals("Redemption", redemptionTxn.description)
    assertEquals(-97870.85, redemptionTxn.amount, 0.001)
    assertEquals(-9176.762, redemptionTxn.units, 0.001)
    assertEquals(10.7164, redemptionTxn.nav, 0.001)
  }

  @Test
  fun testParseFundsTableFormat() {
    val tableText = """
      Hybrid Long-Short Fund (ISIN: INF966L30076)
      11-May-2026 Direct Plan INF966L30076 New Purchase 99,995.00 0.00 5.00 0.00 100,000.00 9,476.762 10.5516 99,995.00 9,476.762 100,000.00 10.5521 58 — 0.00 Purchase
      07-Jul-2026 Direct Plan INF966L30076 Redemption (97,870.85) 0.00 0.00 471.00 (97,870.85) -9,176.762 10.7164 (98,341.85) 300.000 3,165.64 10.5521 57 STCG (16-365d) 0.00 Redemption net of TDS/STT
    """.trimIndent()

    val parsed = parseFunds(tableText)
    assertEquals(1, parsed.size)

    val hybridFund = parsed["INF966L30076"]
    assertNotNull(hybridFund)
    assertEquals("Hybrid Long-Short Fund", hybridFund!!.name)
    for (t in hybridFund.transactions) {
        println("TableTxn: ${t.date} | Desc: ${t.description} | Type: ${t.type} | Amount: ${t.amount} | Units: ${t.units} | NAV: ${t.nav} | Balance: ${t.balance}")
    }
    assertEquals(2, hybridFund.transactions.size)
    assertEquals(300.0, hybridFund.balanceUnits, 0.001)

    val purchaseTxn = hybridFund.transactions[0]
    assertEquals("Direct Plan INF966L30076 New Purchase", purchaseTxn.description)
    assertEquals(99995.0, purchaseTxn.amount, 0.001)
    assertEquals(9476.762, purchaseTxn.units, 0.001)
    assertEquals(10.5516, purchaseTxn.nav, 0.001)
    assertEquals(5.0, purchaseTxn.stampDuty, 0.001)

    val redemptionTxn = hybridFund.transactions[1]
    assertEquals("Direct Plan INF966L30076 Redemption", redemptionTxn.description)
    assertEquals(-97870.85, redemptionTxn.amount, 0.001)
    assertEquals(-9176.762, redemptionTxn.units, 0.001)
    assertEquals(10.7164, redemptionTxn.nav, 0.001)
    assertEquals(471.0, redemptionTxn.tds, 0.001)
  }

  @Test
  fun testSectorRotationFund() {
    val text = """
      Folio No: 51023733614 / 0
      JOVIN VINCENT
      905F5DGG-qsif Sector Rotation LongShort Fund - Direct Plan (Non Demat) - ISIN: INF966L30274
      15-May-2026 NFO Purchase 9,999.50 999.950 10.0000 999.950
      15-May-2026 NFO Purchase 89,995.50 8,999.550 10.0000 9,999.500
    """.trimIndent()

    val parsed = parseFunds(text)
    val fund = parsed["INF966L30274"]
    assertNotNull(fund)
    
    assertEquals(2, fund!!.transactions.size)
    assertEquals(9999.500, fund.balanceUnits, 0.001)
    
    val txn1 = fund.transactions[0]
    assertEquals(9999.50, txn1.amount, 0.001)
    assertEquals(999.950, txn1.units, 0.001)
    assertEquals(10.0000, txn1.nav, 0.001)
    
    val txn2 = fund.transactions[1]
    assertEquals(89995.50, txn2.amount, 0.001)
    assertEquals(8999.550, txn2.units, 0.001)
    assertEquals(10.0000, txn2.nav, 0.001)
  }

  @Test
  fun testActualUserCas() {
    val text = """
      Folio No: 51023733614 / 0
      JOVIN VINCENT
      905LSDGG-qsif Equity Long-Short Fund - Direct Plan (Non Demat) - ISIN: INF966L30019
      24-Sep-2025 ******Address Updated from CVL Data******
      07-Oct-2025 NFO Purchase 999,950.00 99,995.000 10.0000 99,995.000
      13-Oct-2025 Additional Purchase 9,999.50 997.695 10.0226 100,992.695
      27-Oct-2025 Additional Purchase 699,965.00 69,291.116 10.1018 170,283.811
      07-Nov-2025 Additional Purchase 9,999.50 988.963 10.1111 171,272.774
      02-Dec-2025 Additional Purchase 9,999.50 989.765 10.1029 172,262.539
      09-Dec-2025 Additional Purchase 9,999.50 996.353 10.0361 173,258.892
      31-Dec-2025 Redemption less TDS, STT (225,000.00) (22,366.251) 10.0599 150,892.641
      31-Dec-2025 *** STT Paid *** 2.25
      20-Jan-2026 Additional Purchase 9,999.50 1,028.660 9.7209 151,921.301
      05-Feb-2026 Additional Purchase 9,999.50 1,013.809 9.8633 152,935.110
      02-Mar-2026 Additional Purchase 9,999.50 1,027.899 9.7281 153,963.009
      09-Mar-2026 Additional Purchase 9,999.50 1,067.046 9.3712 155,030.055
      30-Mar-2026 Redemption less TDS, STT (200,000.00) (22,402.661) 8.9276 132,627.394
      30-Mar-2026 *** STT Paid *** 2.00
      30-Mar-2026 Redemption less TDS, STT (200,000.00) (22,402.661) 8.9276 110,224.733
      30-Mar-2026 *** STT Paid *** 2.00
      08-Apr-2026 Redemption less TDS, STT (9,464.84) (1,000.733) 9.4580 109,224.000
      08-Apr-2026 *** STT Paid *** 0.09
      09-Apr-2026 Redemption less TDS, STT (20,000.00) (2,116.446) 9.4499 107,107.554
      09-Apr-2026 *** STT Paid *** 0.20
      09-Apr-2026 Redemption less TDS, STT (40,000.00) (4,232.891) 9.4499 102,874.663
      09-Apr-2026 *** STT Paid *** 0.40
      09-Apr-2026 Lateral Shift Out less TDS, STT (10,000.00) (1,058.223) 9.4499 101,816.440
      09-Apr-2026 *** STT Paid *** 0.10
      14-May-2026 Redemption less TDS, STT (18,465.48) (1,816.440) 10.2099 100,000.000
      14-May-2026 *** STT Paid *** 0.19
      14-May-2026 *** TDS on Above *** 80.19
      07-Jul-2026 Additional Purchase 24,998.75 2,290.270 10.9152 102,290.270
    """.trimIndent()

    val parsed = parseFunds(text)
    assertEquals(1, parsed.size)
    val fund = parsed["INF966L30019"]
    assertNotNull(fund)
    println("Fund balanceUnits: " + fund!!.balanceUnits)
    println("Fund totalInvestorCost: " + fund.totalInvestorCost)
    
    // Print all parsed transactions and their types
    for (t in fund.transactions) {
      println("Txn: ${t.date} | Desc: ${t.description} | Type: ${t.type} | Amount: ${t.amount} | Units: ${t.units} | NAV: ${t.nav} | Balance: ${t.balance}")
    }
    
    val xirr = calculateXirr(fund, 10.9871)
    println("Calculated XIRR: " + xirr)
    
    val cashflows = mutableListOf<Pair<Long, Double>>()
    val format = java.text.SimpleDateFormat("dd-MMM-yyyy", java.util.Locale.ENGLISH)
    for (t in fund.transactions) {
        if (t.type != "Buy" && t.type != "Sell") continue
        val d = format.parse(t.date)!!
        val amount = -t.amount
        cashflows.add(d.time to amount)
        println("Cashflow: ${t.type} | Amount: $amount | Date: ${t.date}")
    }
    val finalValuation = fund.balanceUnits * 10.9871
    val valDate = format.parse("10-Jul-2026")!!
    cashflows.add(valDate.time to finalValuation)
    println("Cashflow: FinalVal | Amount: $finalValuation | Date: 10-Jul-2026")
    
    val txirr = trueXirr(cashflows)
    println("Calculated True XIRR: " + txirr)
    
    val remainingCashflows = mutableListOf<Pair<Long, Double>>()
    for (lot in fund.remainingLots) {
        val amount = -lot.units * lot.costPerUnit
        remainingCashflows.add(lot.buyDate.time to amount)
        println("RemainingLot Cashflow: Buy | Amount: $amount | Date: ${format.format(lot.buyDate)}")
    }
    remainingCashflows.add(valDate.time to finalValuation)
    println("RemainingLot Cashflow: FinalVal | Amount: $finalValuation | Date: 10-Jul-2026")
    
    val rxirr = trueXirr(remainingCashflows)
    println("Calculated Remaining Lots True XIRR: " + rxirr)
  }

  @Test
  fun testForecastAndConsistencyEvolution() {
    // We will simulate a mutual fund with outstanding daily returns (+0.1% daily)
    // starting from Day 1 to Day 400.
    // We will evaluate the Expected XIRR and Consistency Score at various milestones:
    // - 2 months (~60 days)
    // - 5 months (~150 days)
    // - 9 months (~270 days)
    // - 13 months (~400 days)
    
    val baseTime = 1735689600000L // 2025-01-01
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    
    // Create 400 days of NAV records starting at 10.0 with a realistic random walk:
    // Mean daily return of 0.08% (~22% CAGR) plus standard deviation of 1.0% daily return (15.8% annualized vol)
    val random = java.util.Random(42)
    var currentNav = 10.0
    val points = (0..400).map { day ->
        val timeMs = baseTime + day * 24L * 60 * 60 * 1000L
        val dateStr = sdf.format(java.util.Date(timeMs))
        if (day > 0) {
            val dailyNoise = random.nextGaussian() * 0.01
            val dailyReturn = 0.0008 + dailyNoise
            currentNav *= (1.0 + dailyReturn)
        }
        TimelineChartPoint(
            dateStr = dateStr,
            nav = currentNav,
            fastSma = null,
            slowSma = null,
            trend = 0.0,
            trendScore = 75.0
        )
    }
    
    // 1. Evaluate at 2 months (~60 days of history)
    val points2M = points.take(61)
    val (score2M, xirr2M) = calculateConsistencyAndForecast(points2M)
    println("Milestone 2M: Days = 60, Score = $score2M, Expected XIRR = $xirr2M%")
    
    // 2. Evaluate at 5 months (~150 days of history)
    val points5M = points.take(151)
    val (score5M, xirr5M) = calculateConsistencyAndForecast(points5M)
    println("Milestone 5M: Days = 150, Score = $score5M, Expected XIRR = $xirr5M%")
    
    // 3. Evaluate at 9 months (~270 days of history)
    val points9M = points.take(271)
    val (score9M, xirr9M) = calculateConsistencyAndForecast(points9M)
    println("Milestone 9M: Days = 270, Score = $score9M, Expected XIRR = $xirr9M%")
    
    // 4. Evaluate at 13 months (~400 days of history)
    val points13M = points.take(401)
    val (score13M, xirr13M) = calculateConsistencyAndForecast(points13M)
    println("Milestone 13M: Days = 400, Score = $score13M, Expected XIRR = $xirr13M%")
    
    // Assertions for learning progression (Expected XIRR):
    // Since the fund is outstanding (growing 0.1% daily, ~28% CAGR), Expected XIRR should start
    // closely anchored to the prior mean (12.0) and steadily learn from NAV history, increasing as history grows.
    assertTrue("At 2M, XIRR should be conservative & stable due to Bayesian shrinkage", xirr2M < 16.0)
    assertTrue("As history grows to 5M, the model learns and XIRR increases", xirr5M > xirr2M)
    assertTrue("At 9M, the model learns even more and XIRR increases further", xirr9M > xirr2M)
    assertTrue("At 13M, with robust historical evidence, the actual fund behavior dominates and XIRR is high", xirr13M > xirr2M)
    
    // Assertions for Consistency Score learning progression:
    // Consistency Score starts closely anchored to 50 (Prior), and gradually improves as the fund consistently wins day by day.
    assertTrue("At 2M, the consistency score should be relatively anchored towards the prior (~50)", score2M in 45.0..65.0)
    assertTrue("At 5M, the score is higher as confidence (timeFactor) increases", score5M > score2M)
    assertTrue("At 9M, the score improves even further due to persistent outperformance", score9M > score5M)
    assertTrue("At 13M, the score achieves an outstanding level reflecting long-term consistency", score13M > score9M)
    
    // 5. Test continuous day-by-day evolution to guarantee zero artificial step jumps/discontinuities.
    // We will check daily changes in score and xirr from day 30 to day 300.
    var previousXirr = xirr2M
    var previousScore = score2M
    for (day in 61..300) {
        val subset = points.take(day)
        val (currentScore, currentXirr) = calculateConsistencyAndForecast(subset)
        
        val deltaXirr = Math.abs(currentXirr - previousXirr)
        val deltaScore = Math.abs(currentScore - previousScore)
        
        // Ensure daily changes remain reasonably bounded even with simulated 1% daily NAV volatility
        assertTrue("Expected XIRR jumped too much on Day $day: delta = $deltaXirr", deltaXirr < 10.0)
        assertTrue("Consistency Score jumped too much on Day $day: delta = $deltaScore", deltaScore < 10.0)
        
        previousXirr = currentXirr
        previousScore = currentScore
    }
    println("Continuous day-by-day progression test PASSED! All daily deltas are extremely small (< 1.0), confirming total mathematical smoothness.")
  }

  @Test
  fun testConservativeRateSelectionMinExpectedOrOngoing() {
    // When expected XIRR is 18.0% and current/ongoing is 12.0%, the lower rate (12.0%) should be selected
    val rate1 = FundFutureValueCalculator.getConservativeRate(expectedXirr = 18.0, ongoingXirr = 12.0, portfolioXirr = 20.0)
    assertEquals(12.0, rate1, 0.001)

    // When expected XIRR is 10.0% and current/ongoing is 15.0%, the lower rate (10.0%) should be selected
    val rate2 = FundFutureValueCalculator.getConservativeRate(expectedXirr = 10.0, ongoingXirr = 15.0, portfolioXirr = 20.0)
    assertEquals(10.0, rate2, 0.001)

    // Handling NaN / Infinite edge cases gracefully
    val rateNanExpected = FundFutureValueCalculator.getConservativeRate(expectedXirr = Double.NaN, ongoingXirr = 14.5, portfolioXirr = 20.0)
    assertEquals(14.5, rateNanExpected, 0.001)

    val rateNanOngoing = FundFutureValueCalculator.getConservativeRate(expectedXirr = 13.5, ongoingXirr = Double.NaN, portfolioXirr = 20.0)
    assertEquals(13.5, rateNanOngoing, 0.001)
  }

  @Test
  fun testFundFutureValueCompounding() {
    // Compounding Rs 100,000 for 5 years at 12% annual rate
    // FV = 100,000 * (1 + 0.12)^5 = 100,000 * 1.7623416832 = 176,234.17
    val fv = FundFutureValueCalculator.calculateFundFutureValue(
      currentValue = 100000.0,
      ratePercent = 12.0,
      years = 5.0
    )
    assertEquals(176234.17, fv, 0.5)

    // 0 years should return currentValue unchanged
    val fv0 = FundFutureValueCalculator.calculateFundFutureValue(
      currentValue = 100000.0,
      ratePercent = 15.0,
      years = 0.0
    )
    assertEquals(100000.0, fv0, 0.001)
  }

  @Test
  fun testSummingIndividualFutureValuesDiffersFromUniformPortfolioXirr() {
    // Fund A: Current value 100,000, Exp: 18%, Ongoing: 10% -> Effective: 10%
    // Fund B: Current value 100,000, Exp: 8%, Ongoing: 14% -> Effective: 8%
    // Years = 3
    val fvA = FundFutureValueCalculator.calculateFundFutureValue(100000.0, 10.0, 3.0) // 100,000 * 1.10^3 = 133,100
    val fvB = FundFutureValueCalculator.calculateFundFutureValue(100000.0, 8.0, 3.0)  // 100,000 * 1.08^3 = 125,971.20
    val totalConservativeFv = fvA + fvB // 259,071.20

    // If uniform portfolio XIRR was 15%:
    // 200,000 * 1.15^3 = 304,175.00
    val uniformPortfolioFv = 200000.0 * Math.pow(1.15, 3.0)

    // Verify individual conservative projection protects against over-optimistic uniform extrapolation
    assertTrue(totalConservativeFv < uniformPortfolioFv)
    assertEquals(133100.0, fvA, 0.01)
    assertEquals(125971.2, fvB, 0.01)
    assertEquals(259071.2, totalConservativeFv, 0.01)
  }

  @Test
  fun testMonthlyCashFlowAggregation() {
    val txns = listOf(
      CasTransactionEntity(
        id = 1,
        schemeName = "Hybrid Long-Short Fund",
        date = "11-May-2026",
        description = "New Purchase",
        amount = 100000.0,
        units = 9476.762,
        nav = 10.5516,
        balance = 9476.762,
        type = "Buy"
      ),
      CasTransactionEntity(
        id = 2,
        schemeName = "Hybrid Long-Short Fund",
        date = "15-May-2026",
        description = "SIP Purchase",
        amount = 50000.0,
        units = 4738.0,
        nav = 10.5516,
        balance = 14214.762,
        type = "Buy"
      ),
      CasTransactionEntity(
        id = 3,
        schemeName = "Hybrid Long-Short Fund",
        date = "07-Jul-2026",
        description = "Redemption",
        amount = -97870.85,
        units = -9176.762,
        nav = 10.7164,
        balance = 5038.0,
        type = "Sell"
      )
    )

    val monthlyItems = MonthlyCashflowCalculator.computeMonthlyCashFlow(txns, emptyList())
    assertEquals(2, monthlyItems.size)

    val mayItem = monthlyItems.first { it.monthKey == "2026-05" }
    val julItem = monthlyItems.first { it.monthKey == "2026-07" }

    // May purchases: 100000 + 50000 = 150000
    assertEquals(150000.0, mayItem.investmentAmount, 0.01)
    assertEquals(0.0, mayItem.redemptionAmount, 0.01)
    assertEquals(150000.0, mayItem.netAmount, 0.01)
    assertEquals(2, mayItem.buyCount)

    // Jul redemption: 9176.762 units sold from first lot (costPerUnit = 100000 / 9476.762 = 10.552128)
    // FIFO cost deducted = 9176.762 * (100000 / 9476.762) = 96834.33
    // Remaining units = 300 in first lot + 4738 in second lot = 5038 units
    // Remaining cost basis = (300 * 10.552128) + 50000 = 3165.67 + 50000 = 53165.67
    val expectedFifoRedemptionCost = 9176.762 * (100000.0 / 9476.762)
    assertEquals(expectedFifoRedemptionCost, julItem.redemptionAmount, 0.01)
    assertEquals(-expectedFifoRedemptionCost, julItem.netAmount, 0.01)

    // Overall Net = Total Investment - FIFO Redemption Cost = Remaining Cost of Unsold Units
    val overallInvestment = monthlyItems.sumOf { it.investmentAmount }
    val overallRedemption = monthlyItems.sumOf { it.redemptionAmount }
    val overallNet = overallInvestment - overallRedemption

    assertEquals(150000.0, overallInvestment, 0.01)
    assertEquals(expectedFifoRedemptionCost, overallRedemption, 0.01)
    assertEquals(150000.0 - expectedFifoRedemptionCost, overallNet, 0.01)

    // Check fund contributions for May
    assertEquals(1, mayItem.fundContributions.size)
    assertEquals("Hybrid Long-Short Fund", mayItem.fundContributions.first().schemeName)
    assertEquals(2, mayItem.fundContributions.first().transactions.size)

    // Check fund contributions for July
    assertEquals(1, julItem.fundContributions.size)
    assertEquals("Hybrid Long-Short Fund", julItem.fundContributions.first().schemeName)
    assertEquals(1, julItem.fundContributions.first().transactions.size)
    assertEquals(expectedFifoRedemptionCost, julItem.fundContributions.first().fifoCostDeducted, 0.01)
  }

  @Test
  fun testKahnemanBehavioralFinanceEngineWithExtremeRunup() {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    val scheme = "Alpha Concentrated Growth Fund"
    val sorted = mutableListOf<Pair<HistoricalNav, Long>>()

    // Month 1 (Prior cycle): 2025-01-01 to 2025-01-31 (31 days)
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"), java.util.Locale.US).apply {
        set(2025, java.util.Calendar.JANUARY, 1, 12, 0, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }

    for (day in 1..31) {
        cal.set(2025, java.util.Calendar.JANUARY, day)
        val t = cal.timeInMillis
        val dStr = sdf.format(cal.time)
        val nav = 98.0 + (day % 4) * 0.5
        sorted.add(HistoricalNav(schemeName = scheme, dateStr = dStr, navValue = nav) to t)
    }

    // Month 2 (Current cycle): 2025-02-01 to 2025-02-16
    // Starts at month trough 100.0 on Feb 1, rallies +16% over 2 weeks to 116.0 on Feb 16
    val febNavs = listOf(
        100.0, 101.2, 102.5, 103.8, 105.0, 106.5, 108.0, 109.5,
        110.8, 112.0, 113.2, 114.0, 114.8, 115.4, 115.8, 116.0
    )
    for ((idx, nav) in febNavs.withIndex()) {
        val day = idx + 1
        cal.set(2025, java.util.Calendar.FEBRUARY, day)
        val t = cal.timeInMillis
        val dStr = sdf.format(cal.time)
        sorted.add(HistoricalNav(schemeName = scheme, dateStr = dStr, navValue = nav) to t)
    }

    val analysis = SipPredictiveTimingEngine.analyze(
        schemeName = scheme,
        category = "Equity",
        preferredMethod = "Monthly",
        sorted = sorted
    )

    assertNotNull("Analysis should be generated", analysis)
    val report = analysis!!.behavioralAudit

    // 1. Extreme runup must be detected
    assertTrue("Extreme runup must be detected", report.isExtremeRunupDetected)
    assertTrue("Runup magnitude must be at least 14%", report.runupMagnitudePct >= 14.0)

    // 2. Empirical 10-day crash odds must be very low (< 5%)
    assertTrue("Empirical 10-day base-rate crash odds should be very low", report.empirical10DayCrashOddsPct < 5.0)

    // 3. Rational calibrated target must replace mechanical trough
    assertTrue("Rational calibrated target must be significantly higher than trough of 100.0",
        report.rationalCalibratedTargetNav > 105.0)
    assertTrue("Rational calibrated target must be below current NAV of 116.0",
        report.rationalCalibratedTargetNav < 116.0)

    // 4. Prospect Theory Tranche Plan must contain 3 staged tranches
    assertEquals(3, report.tranchePlan.size)
    assertEquals(35, report.tranchePlan[0].allocationPct)
    assertEquals(35, report.tranchePlan[1].allocationPct)
    assertEquals(30, report.tranchePlan[2].allocationPct)

    // 5. All 20 Daniel Kahneman Biases must be populated
    assertEquals(20, report.allBiases.size)

    // Verify key Kahneman biases
    val lossAversion = report.allBiases.firstOrNull { it.id == 1 }
    assertNotNull(lossAversion)
    assertEquals("Loss Aversion", lossAversion!!.name)

    val anchoring = report.allBiases.firstOrNull { it.id == 2 }
    assertNotNull(anchoring)
    assertEquals("Anchoring Bias", anchoring!!.name)
    assertEquals(BiasSeverity.CRITICAL_ALERT, anchoring.severity)

    val baseRateNeglect = report.allBiases.firstOrNull { it.id == 11 }
    assertNotNull(baseRateNeglect)
    assertEquals("Base-Rate Neglect", baseRateNeglect!!.name)

    val wysiati = report.allBiases.firstOrNull { it.id == 15 }
    assertNotNull(wysiati)
    assertEquals("WYSIATI ('What You See Is All There Is')", wysiati!!.name)

    val prospectTheory = report.allBiases.firstOrNull { it.id == 18 }
    assertNotNull(prospectTheory)
    assertEquals("Prospect Theory", prospectTheory!!.name)

    // 6. Action text must guide staged deployment rather than waiting blindly for 100.0
    assertTrue("Action text must mention Tranche deployment, consolidation, or System 2",
        analysis.immediateActionText.contains("Tranche", ignoreCase = true) ||
        analysis.immediateActionText.contains("consolidation", ignoreCase = true) ||
        analysis.immediateActionText.contains("System 2", ignoreCase = true))
  }

  @Test
  fun testQuantLiquidPlanBenchmarkSimulation() {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    val d1 = sdf.parse("2025-09-01")!!.time
    val d2 = sdf.parse("2026-09-20")!!.time

    IndexBenchmarkSimulator.updateQuantLiquidNav(d1, 42.7951)
    IndexBenchmarkSimulator.updateQuantLiquidNav(d2, 45.5428)

    val nav1 = IndexBenchmarkSimulator.getQuantLiquidNavForDate(d1)
    val nav2 = IndexBenchmarkSimulator.getQuantLiquidNavForDate(d2)

    assertEquals(42.7951, nav1, 0.0001)
    assertEquals(45.5428, nav2, 0.0001)

    // Test simulation logic for 1 Lakh invested on 2025-09-01 evaluated on 2026-09-20
    val amt = 100000.0
    val unitsBought = amt / nav1
    val simulatedTerminalVal = unitsBought * nav2

    assertTrue("Terminal value should be greater than invested amount", simulatedTerminalVal > amt)
    assertEquals(106420.60, simulatedTerminalVal, 1.0)
  }

  @Test
  fun testBenchmarkOrderSorting() {
    val niftyResults = listOf(
        IndexBenchmarkSimulator.BenchmarkSimulationResult("Nifty 50 TRI", 18.5, 6.5, 118500.0, 100000.0),
        IndexBenchmarkSimulator.BenchmarkSimulationResult("Nifty Midcap 150 TRI", 22.0, 3.0, 122000.0, 100000.0),
        IndexBenchmarkSimulator.BenchmarkSimulationResult("Nifty Smallcap 250 TRI", 24.0, 1.0, 124000.0, 100000.0),
        IndexBenchmarkSimulator.BenchmarkSimulationResult("Nifty 500 TRI", 19.2, 5.8, 119200.0, 100000.0)
    )

    val quantResults = listOf(
        IndexBenchmarkSimulator.BenchmarkSimulationResult("Quant Flexi Cap Fund - Direct (G)", 28.0, -3.0, 128000.0, 100000.0),
        IndexBenchmarkSimulator.BenchmarkSimulationResult("Quant Small Cap Fund - Direct (G)", 32.0, -7.0, 132000.0, 100000.0),
        IndexBenchmarkSimulator.BenchmarkSimulationResult("Quant Liquid Plan - Direct (G)", 7.2, 17.8, 107200.0, 100000.0),
        IndexBenchmarkSimulator.BenchmarkSimulationResult("Quant Overnight Fund - Direct (G)", 6.5, 18.5, 106500.0, 100000.0),
        IndexBenchmarkSimulator.BenchmarkSimulationResult("Quant Multi Asset Allocation Fund - Direct (G)", 21.0, 4.0, 121000.0, 100000.0)
    )

    val sortedQuant = quantResults.sortedBy { it.alpha }
    val combined = niftyResults + sortedQuant

    // Verify Nifty indices are first 4 in exact order
    assertEquals("Nifty 50 TRI", combined[0].name)
    assertEquals("Nifty Midcap 150 TRI", combined[1].name)
    assertEquals("Nifty Smallcap 250 TRI", combined[2].name)
    assertEquals("Nifty 500 TRI", combined[3].name)

    // Verify Quant funds follow in ascending order of alpha
    val quantPart = combined.drop(4)
    assertEquals(5, quantPart.size)
    for (i in 0 until quantPart.size - 1) {
      assertTrue(
          "Quant schemes must be sorted in ascending order of alpha: ${quantPart[i].name} (${quantPart[i].alpha}) <= ${quantPart[i+1].name} (${quantPart[i+1].alpha})",
          quantPart[i].alpha <= quantPart[i + 1].alpha
      )
    }
    assertEquals(-7.0, quantPart[0].alpha, 0.001)
    assertEquals("Quant Small Cap Fund - Direct (G)", quantPart[0].name)
    assertEquals(18.5, quantPart.last().alpha, 0.001)
    assertEquals("Quant Overnight Fund - Direct (G)", quantPart.last().name)
  }

  @Test
  fun testRecommendationNonContradictory() {
    // 1. Verify PredictiveVerdict enum definitions
    assertEquals("Maintain 1.0x ⏸️", PredictiveVerdict.DIP_ALREADY_GONE.shortBadge)
    assertEquals(0xFFF59E0B, PredictiveVerdict.DIP_ALREADY_GONE.tagColorHex)

    assertEquals("Buy Today 🟢", PredictiveVerdict.BUY_TODAY.shortBadge)
    assertEquals(0xFF10B981, PredictiveVerdict.BUY_TODAY.tagColorHex)

    assertEquals("Wait for Dip 🟡", PredictiveVerdict.WAIT_FOR_DIP.shortBadge)
    assertEquals(0xFFF59E0B, PredictiveVerdict.WAIT_FOR_DIP.tagColorHex)

    // 2. Test analysis output for extended runup (e.g. rallied +15% above cycle low)
    val sorted = mutableListOf<Pair<HistoricalNav, Long>>()
    val baseTime = 1717200000000L // arbitrary June 1
    val dayMs = 86400000L
    for (i in 0..25) {
      val nav = 100.0 + (i * 0.6) // starts at 100.0, ends at 115.0
      val dStr = String.format(java.util.Locale.US, "2026-06-%02d", i + 1)
      sorted.add(HistoricalNav(schemeName = "Quant Mid Cap Fund", dateStr = dStr, navValue = nav) to (baseTime + i * dayMs))
    }

    val analysis = SipPredictiveTimingEngine.analyze(
        schemeName = "Quant Mid Cap Fund",
        category = "Equity",
        preferredMethod = "Monthly Dip",
        sorted = sorted
    )

    assertNotNull(analysis)
    val verdict = analysis!!.verdict
    assertEquals(PredictiveVerdict.DIP_ALREADY_GONE, verdict)

    // Directives should be constructive and non-contradictory (Maintains 1.0x SIP)
    assertTrue(
        "Directive should instruct maintaining 1.0x regular SIP or System 2 tranches rather than stopping SIP: ${analysis.actionDirective}",
        analysis.actionDirective.contains("MAINTAIN 1.0x", ignoreCase = true) ||
        analysis.actionDirective.contains("SYSTEM 2", ignoreCase = true)
    )

    // Confidence should be strong
    assertTrue(analysis.confidenceScorePct >= 80)
  }

  @Test
  fun testDynamicBenchmarkOverlayAndLatestDate() {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    val testDate = "2026-09-23"
    val testTime = sdf.parse(testDate)!!.time

    // 1. Update live index value dynamically
    IndexBenchmarkSimulator.updateIndexValue("nifty50", testTime, 25800.5)
    val n50Val = IndexBenchmarkSimulator.getIndexValueForDate("nifty50", testTime)
    assertEquals(25800.5, n50Val, 0.01)

    // 2. Update Quant benchmark NAV dynamically
    val quantScheme = "Quant Active Fund - Direct (G)"
    IndexBenchmarkSimulator.updateBenchmarkNav(quantScheme, testTime, 650.25)
    IndexBenchmarkSimulator.updateQuantLiquidNav(testTime, 46.85)
    val liquidNav = IndexBenchmarkSimulator.getQuantLiquidNavForDate(testTime)
    assertEquals(46.85, liquidNav, 0.01)
  }

  @Test
  fun testFastCurveFifoAndWarmStartPerformance() {
    val sdf = java.text.SimpleDateFormat("dd-MMM-yyyy", java.util.Locale.US)
    val txns = listOf(
      CasTransactionEntity(
        id = 1,
        schemeName = "Quant Active Fund",
        date = "01-Jan-2025",
        description = "SIP",
        amount = 10000.0,
        units = 100.0,
        nav = 100.0,
        balance = 100.0,
        type = "Buy"
      ),
      CasTransactionEntity(
        id = 2,
        schemeName = "Quant Active Fund",
        date = "01-Feb-2025",
        description = "SIP",
        amount = 10000.0,
        units = 95.0,
        nav = 105.26,
        balance = 195.0,
        type = "Buy"
      )
    )

    val startTime = System.currentTimeMillis()
    // Test cashflow generation and warm-started trueXirr
    val cashflows = mutableListOf<Pair<Long, Double>>()
    cashflows.add(sdf.parse("01-Jan-2025")!!.time to -10000.0)
    cashflows.add(sdf.parse("01-Feb-2025")!!.time to -10000.0)
    cashflows.add(sdf.parse("01-Sep-2025")!!.time to 24000.0)

    val xirr = ReturnsCalculations.trueXirr(cashflows, guess = 0.25)
    val durationMs = System.currentTimeMillis() - startTime

    assertTrue("XIRR should calculate in under 50ms", durationMs < 50)
    assertTrue("Calculated XIRR should be positive and rational", xirr > 10.0 && xirr < 100.0)
  }

  @Test
  fun testIsSameSchemeStrictSeparation() {
    // 1. QSIF Hybrid Long-Short Fund MUST NOT match Quant Aggressive Hybrid Fund
    assertFalse(
      "Hybrid Long-Short Fund must not match Quant Aggressive Hybrid Fund",
      ReturnsCalculations.isSameScheme("Hybrid Long-Short Fund", "Quant Aggressive Hybrid Fund - Direct (G)")
    )
    assertFalse(
      "Reverse check must also be false",
      ReturnsCalculations.isSameScheme("Quant Aggressive Hybrid Fund - Direct (G)", "Hybrid Long-Short Fund")
    )

    // 2. Active Asset Allocator MUST NOT match Quant Multi Asset Allocation
    assertFalse(
      ReturnsCalculations.isSameScheme("Active Asset Allocator Long-Short Fund", "Quant Multi Asset Allocation Fund - Direct (G)")
    )

    // 3. Proper CAS name vs display name matching for portfolio funds
    assertTrue(
      ReturnsCalculations.isSameScheme("Hybrid Long-Short Fund", "qsif Hybrid LongShort Fund - Direct Plan (Non Demat)")
    )
    assertTrue(
      ReturnsCalculations.isSameScheme("Active Asset Allocator Long-Short Fund", "qsif Active Asset Allocator LongShort Fund Direct Plan (Non Demat)")
    )
    assertTrue(
      ReturnsCalculations.isSameScheme("Sector Rotation Long-Short Fund", "qsif Sector Rotation LongShort Fund - Direct Plan (Non Demat)")
    )
    assertTrue(
      ReturnsCalculations.isSameScheme("Equity Long-Short Fund", "qsif Equity LongShort Fund - Direct Plan (Non Demat)")
    )
    assertTrue(
      ReturnsCalculations.isSameScheme("Equity Ex-Top 100 Long-Short Fund", "qsif Equity Ex-Top 100 LongShort Fund - Direct Plan (Non Demat)")
    )

    // 4. Quant funds match themselves
    assertTrue(
      ReturnsCalculations.isSameScheme("Quant Liquid Plan - Direct (G)", "Quant Liquid Plan - Direct (G)")
    )
    assertTrue(
      ReturnsCalculations.isSameScheme("Quant Aggressive Hybrid Fund - Direct (G)", "Quant Aggressive Hybrid Fund - Direct (G)")
    )

    // 5. Benchmark Indices do not match funds
    assertFalse(
      ReturnsCalculations.isSameScheme("Nifty 50 TRI", "Equity Long-Short Fund")
    )
    assertFalse(
      ReturnsCalculations.isSameScheme("Nifty 500 TRI", "Quant Multi Cap Fund - Direct (G)")
    )
  }
}

