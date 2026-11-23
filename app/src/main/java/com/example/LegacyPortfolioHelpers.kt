package com.example

import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.pow

data class PortfolioSummaryData(
    val totalInvested: Double = 0.0,
    val totalCurrentValue: Double = 0.0,
    val overallTotalPlAmount: Double = 0.0,
    val overallTotalPlPercent: Double = 0.0,
    val overallDailyPlAmount: Double = 0.0,
    val overallDailyPlPercent: Double = 0.0,
    val xirrVal: Double = 0.0
)

fun formatIndianCommas(amount: Double, decimals: Int = 2): String {
    if (amount.isNaN() || amount.isInfinite()) return "0"
    val formatter = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = decimals.coerceAtLeast(0)
        maximumFractionDigits = decimals.coerceAtLeast(0)
    }
    return formatter.format(amount)
}

fun calculatePortfolioXirr(
    investedAmount: Double,
    currentVal: Double,
    navDateStr: String,
    baseCal: Calendar
): Double {
    if (investedAmount <= 0.0 || currentVal <= 0.0) return 0.0
    val endMs = IndexBenchmarkSimulator.parseAnyDateToMillis(navDateStr) ?: System.currentTimeMillis()
    val days = ((endMs - baseCal.timeInMillis) / 86_400_000.0).coerceAtLeast(1.0)
    val years = days / 365.25
    val ratio = currentVal / investedAmount
    val xirr = (ratio.pow(1.0 / years) - 1.0) * 100.0
    return if (xirr.isNaN() || xirr.isInfinite()) 0.0 else xirr.coerceIn(-99.9, 999.9)
}

fun calculateAggregatePortfolioXirr(
    investedItems: List<FundUiItem>,
    xirrBaseCal: Calendar,
    allTxns: List<CasTransactionEntity> = emptyList()
): Double {
    if (investedItems.isEmpty()) return 0.0
    val totalInvested = investedItems.sumOf { it.investedAmount }
    val totalCurrent = investedItems.sumOf { it.currentValue }
    if (allTxns.isNotEmpty()) {
        val navDateMs = investedItems.mapNotNull { IndexBenchmarkSimulator.parseAnyDateToMillis(it.fund.navDate) }
            .maxOrNull() ?: System.currentTimeMillis()
        val cashflows = ArrayList<Pair<Long, Double>>(allTxns.size + 1)
        for (txn in allTxns) {
            val tMs = IndexBenchmarkSimulator.parseAnyDateToMillis(txn.date) ?: continue
            if (txn.type.equals("Buy", ignoreCase = true)) {
                cashflows.add(tMs to -kotlin.math.abs(txn.amount))
            } else if (txn.type.equals("Sell", ignoreCase = true)) {
                cashflows.add(tMs to kotlin.math.abs(txn.amount))
            }
        }
        if (cashflows.isNotEmpty() && totalCurrent > 0.0) {
            cashflows.add(navDateMs to totalCurrent)
            val result = ReturnsCalculations.trueXirr(cashflows, guess = 0.2)
            if (!result.isNaN() && !result.isInfinite() && result != 0.0) {
                return result
            }
        }
    }
    val latestDate = investedItems.firstOrNull()?.fund?.navDate ?: ""
    return calculatePortfolioXirr(totalInvested, totalCurrent, latestDate, xirrBaseCal)
}
