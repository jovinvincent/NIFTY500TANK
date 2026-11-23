package com.example

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import kotlin.math.pow
import kotlin.math.min
import kotlin.math.abs

object ReturnsCalculations {
    data class Lot(val units: Double, val costPerUnit: Double, val date: Date)
    
    private val dateCache = java.util.concurrent.ConcurrentHashMap<String, Date>()
    private val xirrCache = java.util.concurrent.ConcurrentHashMap<String, Double>()
    private val dateFormatThreadLocal = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
    }

    fun parseDateFast(dateStr: String): Date? {
        if (dateStr.isEmpty()) return null
        return dateCache.getOrPut(dateStr) {
            val ms = IndexBenchmarkSimulator.parseAnyDateToMillis(dateStr)
            if (ms != null) Date(ms) else {
                try {
                    dateFormatThreadLocal.get()!!.parse(dateStr)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    fun getValuationDate(): Date {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.time
    }

    fun isSameScheme(s1: String, s2: String): Boolean {
        val l1 = s1.lowercase(Locale.ENGLISH).trim()
        val l2 = s2.lowercase(Locale.ENGLISH).trim()
        if (l1 == l2) return true

        // Income Plus Arbitrage Active FoF exact check
        val isIncomePlus1 = l1.contains("income plus") || l1.contains("arbitrage active")
        val isIncomePlus2 = l2.contains("income plus") || l2.contains("arbitrage active")
        if (isIncomePlus1 || isIncomePlus2) {
            return isIncomePlus1 && isIncomePlus2
        }

        // Strict AMC separation: Quant Mutual Funds vs QSIF Long-Short Portfolio Funds
        val isQuant1 = (l1.startsWith("quant ") || l1.contains("quant ")) && !l1.contains("qsif") && !l1.contains("quant sif")
        val isQuant2 = (l2.startsWith("quant ") || l2.contains("quant ")) && !l2.contains("qsif") && !l2.contains("quant sif")
        if (isQuant1 != isQuant2) return false
        if (isQuant1 && isQuant2) {
            return l1 == l2 || l1.contains(l2) || l2.contains(l1)
        }

        // Benchmark Indices check (Nifty 50 TRI, Nifty 500 TRI, etc.)
        val isIndex1 = l1.contains("nifty") || l1.contains("tri") || l1.contains("index")
        val isIndex2 = l2.contains("nifty") || l2.contains("tri") || l2.contains("index")
        if (isIndex1 || isIndex2) {
            return l1 == l2
        }

        // QSIF Portfolio Long-Short Funds matching
        if (l1.contains("ex-top") || l1.contains("ex top")) {
            return l2.contains("ex-top") || l2.contains("ex top")
        }
        if (l1.contains("active asset") || l1.contains("allocator")) {
            return l2.contains("active asset") || l2.contains("allocator")
        }
        if (l1.contains("sector rotation")) {
            return l2.contains("sector rotation")
        }
        if (l1.contains("hybrid") || l2.contains("hybrid")) {
            val isQsifHybrid1 = l1.contains("hybrid") && (l1.contains("long-short") || l1.contains("longshort") || l1.contains("qsif") || !l1.contains("quant"))
            val isQsifHybrid2 = l2.contains("hybrid") && (l2.contains("long-short") || l2.contains("longshort") || l2.contains("qsif") || !l2.contains("quant"))
            return isQsifHybrid1 && isQsifHybrid2
        }
        val isEquityLs1 = (l1.contains("equity") && (l1.contains("long-short") || l1.contains("long short") || l1.contains("longshort"))) && !l1.contains("ex-top") && !l1.contains("ex top")
        val isEquityLs2 = (l2.contains("equity") && (l2.contains("long-short") || l2.contains("long short") || l2.contains("longshort"))) && !l2.contains("ex-top") && !l2.contains("ex top")
        if (isEquityLs1 || isEquityLs2) {
            return isEquityLs1 && isEquityLs2
        }

        return l1.contains(l2) || l2.contains(l1)
    }

    private val filteredTxnsCache = java.util.concurrent.ConcurrentHashMap<String, List<CasTransactionEntity>>()
    private val fifoCache = java.util.concurrent.ConcurrentHashMap<String, List<Lot>>()

    fun filterTxnsForScheme(allTxns: List<CasTransactionEntity>, targetSchemeName: String): List<CasTransactionEntity> {
        if (allTxns.isEmpty()) return emptyList()
        val cacheKey = "${allTxns.size}_${allTxns.firstOrNull()?.id ?: 0}_${allTxns.lastOrNull()?.id ?: 0}_$targetSchemeName"
        val cached = filteredTxnsCache[cacheKey]
        if (cached != null) return cached
        val result = allTxns.filter { txn ->
            isSameScheme(txn.schemeName, targetSchemeName)
        }
        filteredTxnsCache[cacheKey] = result
        return result
    }
    
    fun runFifo(txns: List<CasTransactionEntity>): List<Lot> {
        if (txns.isEmpty()) return emptyList()
        val cacheKey = "${txns.size}_${txns.firstOrNull()?.id ?: 0}_${txns.lastOrNull()?.id ?: 0}_${txns.sumOf { it.units }}"
        val cachedLots = fifoCache[cacheKey]
        if (cachedLots != null) return cachedLots

        val sorted = txns.sortedBy {
            parseDateFast(it.date)?.time ?: 0L
        }
        val lots = mutableListOf<Lot>()
        for (t in sorted) {
            val d = parseDateFast(t.date) ?: continue
            val isBuy = t.type.equals("Buy", ignoreCase = true) || (t.units > 0 && t.amount > 0 && !t.type.equals("Sell", ignoreCase = true))
            val isSell = t.type.equals("Sell", ignoreCase = true) || t.type.equals("Redemption", ignoreCase = true) || t.type.equals("Switch-Out", ignoreCase = true) || t.units < 0 || t.amount < 0
            if (isBuy && t.units > 0) {
                val grossAmount = kotlin.math.abs(t.amount)
                val cpu = grossAmount / t.units
                lots.add(Lot(t.units, cpu, d))
            } else if (isSell) {
                var toSell = kotlin.math.abs(t.units)
                val newLots = mutableListOf<Lot>()
                for (lot in lots) {
                    if (toSell <= 0) { newLots.add(lot); continue }
                    val take = kotlin.math.min(lot.units, toSell)
                    if (lot.units - take > 1e-9) {
                        newLots.add(Lot(lot.units - take, lot.costPerUnit, lot.date))
                    }
                    toSell -= take
                }
                lots.clear()
                lots.addAll(newLots)
            }
        }
        fifoCache[cacheKey] = lots
        return lots
    }

    fun calculateXirr(
        txns: List<CasTransactionEntity>,
        latestNav: Double,
        balanceUnits: Double,
        totalCost: Double,
        valuationDate: Date = getValuationDate()
    ): Double {
        if (txns.isEmpty() || balanceUnits <= 0 || totalCost <= 0.0) return 0.0

        val cacheKey = "${txns.size}_${balanceUnits}_${latestNav}_${totalCost}_${valuationDate.time}"
        val cached = xirrCache[cacheKey]
        if (cached != null) return cached

        val lots = runFifo(txns)
        if (lots.isEmpty()) return 0.0

        val cashflows = mutableListOf<Pair<Long, Double>>()
        for (lot in lots) {
            val amount = -lot.units * lot.costPerUnit
            cashflows.add(lot.date.time to amount)
        }
        cashflows.add(valuationDate.time to (balanceUnits * latestNav))
        
        if (cashflows.isNotEmpty()) {
            val resolvedXirr = trueXirr(cashflows)
            if (resolvedXirr != 0.0 && !resolvedXirr.isNaN() && !resolvedXirr.isInfinite()) {
                xirrCache[cacheKey] = resolvedXirr
                return resolvedXirr
            }
        }

        // Fallback to the holding period CAGR approximation if trueXirr fails
        val totalLotUnits = lots.sumOf { it.units }
        if (totalLotUnits <= 0) return 0.0
        val weightedDays = lots.sumOf {
            val days = (valuationDate.time - it.date.time) / 86_400_000.0
            it.units * days
        } / totalLotUnits
        if (weightedDays <= 0) return 0.0

        val ratio = (latestNav * balanceUnits) / totalCost
        val exponent = 365.0 / weightedDays
        val res = (Math.pow(ratio, exponent) - 1.0)
        val safeRes = if (res.isNaN() || res.isInfinite()) 0.0 else res
        val clampedRes = minOf(maxOf(safeRes, -0.999), 5.0)
        val finalResult = clampedRes * 100.0
        xirrCache[cacheKey] = finalResult
        return finalResult
    }

    data class LifetimeFundPnl(
        val schemeName: String,
        val isQsif: Boolean,
        val totalPurchases: Double,          // Total cash invested from beginning (Buys/SIPs/Switch-Ins)
        val totalRedemptions: Double,        // Total cash redeemed from beginning (Sells/Switch-Outs)
        val currentUnits: Double,            // Remaining balance units held
        val latestNav: Double,
        val navDate: String,
        val currentValue: Double,            // currentUnits * latestNav
        val costOfCurrentHolding: Double,     // FIFO cost basis of remaining unsold lots
        val costOfSoldUnits: Double,         // FIFO cost basis of sold units
        val realizedPnL: Double,             // totalRedemptions - costOfSoldUnits
        val unrealizedPnL: Double,           // currentValue - costOfCurrentHolding
        val unrealizedPnLPct: Double,        // (unrealizedPnL / costOfCurrentHolding) * 100.0
        val holdingXirr: Double,             // XIRR of active holding cashflows + current valuation
        val totalAbsolutePnL: Double,        // realizedPnL + unrealizedPnL = totalRedemptions + currentValue - totalPurchases
        val absolutePnLPct: Double,          // (totalAbsolutePnL / totalPurchases) * 100.0
        val lifetimeXirr: Double,            // Money-weighted XIRR over all buy/sell cashflows + current value
        val activeLots: List<Lot>,
        val decoratedTxns: List<DecoratedTransaction>,
        val buyCount: Int,
        val sellCount: Int,
        val isFullyExited: Boolean,
        val rawCashflows: List<Pair<Long, Double>>
    )

    fun calculateLifetimePnl(
        schemeName: String,
        txns: List<CasTransactionEntity>,
        latestNav: Double,
        navDate: String,
        valuationDate: Date = getValuationDate(),
        overrideCostOfCurrentHolding: Double? = null
    ): LifetimeFundPnl {
        val dateFmt = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
        val sorted = txns.sortedBy {
            try { dateFmt.parse(it.date) } catch (e: Exception) { Date(0) }
        }

        val activeLots = mutableListOf<Lot>()
        val decoratedTxns = mutableListOf<DecoratedTransaction>()
        val cashflows = mutableListOf<Pair<Long, Double>>()

        var totalPurchases = 0.0
        var totalRedemptions = 0.0
        var costOfSoldUnits = 0.0
        var buyCount = 0
        var sellCount = 0

        for (t in sorted) {
            val d = try { dateFmt.parse(t.date) } catch (e: Exception) { null }
            val isBuy = t.type == "Buy" || (t.units > 0 && t.amount > 0 && t.type != "Sell")
            val isSell = t.type == "Sell" || t.units < 0 || t.amount < 0

            if (isBuy && t.units > 0) {
                buyCount++
                val grossAmount = kotlin.math.abs(t.amount)
                val cpu = grossAmount / t.units
                activeLots.add(Lot(t.units, cpu, d ?: Date(0)))
                totalPurchases += grossAmount

                if (d != null) {
                    cashflows.add(d.time to -grossAmount)
                }

                decoratedTxns.add(DecoratedTransaction(t))
            } else if (isSell) {
                sellCount++
                val unitsToSell = kotlin.math.abs(t.units)
                val redemptionProceeds = if (kotlin.math.abs(t.amount) > 0) kotlin.math.abs(t.amount) else unitsToSell * t.nav
                totalRedemptions += redemptionProceeds

                if (d != null) {
                    cashflows.add(d.time to +redemptionProceeds)
                }

                // FIFO matching against activeLots
                var remainingToConsume = unitsToSell
                var sellCostBasis = 0.0
                var weightedHoldingDays = 0.0
                var totalConsumedUnits = 0.0

                val remainingLots = mutableListOf<Lot>()
                for (lot in activeLots) {
                    if (remainingToConsume <= 0) {
                        remainingLots.add(lot)
                        continue
                    }
                    val take = kotlin.math.min(lot.units, remainingToConsume)
                    val days = if (d != null && lot.date.time > 0) ((d.time - lot.date.time) / 86_400_000.0).toInt() else 0
                    weightedHoldingDays += take * days
                    sellCostBasis += take * lot.costPerUnit
                    totalConsumedUnits += take
                    remainingToConsume -= take

                    if (lot.units - take > 1e-9) {
                        remainingLots.add(Lot(lot.units - take, lot.costPerUnit, lot.date))
                    }
                }
                activeLots.clear()
                activeLots.addAll(remainingLots)

                costOfSoldUnits += sellCostBasis

                val avgHoldingDays = if (totalConsumedUnits > 0) (weightedHoldingDays / totalConsumedUnits).toInt() else 0
                val exitLoad = if (avgHoldingDays <= 15) kotlin.math.round(redemptionProceeds * 0.01 * 100.0) / 100.0 else 0.0
                val taxPeriod = when {
                    avgHoldingDays <= 15 -> "Exit-Load (≤15d)"
                    avgHoldingDays <= 365 -> "STCG (16–365d)"
                    else -> "LTCG (>1y)"
                }
                val capitalGain = kotlin.math.round((redemptionProceeds - sellCostBasis) * 100.0) / 100.0

                decoratedTxns.add(
                    DecoratedTransaction(
                        txn = t,
                        holdingDays = avgHoldingDays,
                        exitLoad = exitLoad,
                        taxPeriod = taxPeriod,
                        capitalGain = capitalGain
                    )
                )
            } else {
                decoratedTxns.add(DecoratedTransaction(t))
            }
        }

        val currentUnits = activeLots.sumOf { it.units }
        val costOfCurrentHolding = if (overrideCostOfCurrentHolding != null && overrideCostOfCurrentHolding > 0.0) {
            overrideCostOfCurrentHolding
        } else {
            activeLots.sumOf { it.units * it.costPerUnit }
        }
        val currentValue = if (currentUnits > 0.0001) currentUnits * latestNav else 0.0

        val realizedPnL = totalRedemptions - costOfSoldUnits
        val unrealizedPnL = if (currentUnits > 0.0001) currentValue - costOfCurrentHolding else 0.0
        val unrealizedPnLPct = if (costOfCurrentHolding > 0) (unrealizedPnL / costOfCurrentHolding) * 100.0 else 0.0
        val totalAbsolutePnL = realizedPnL + unrealizedPnL
        val absolutePnLPct = if (totalPurchases > 0) (totalAbsolutePnL / totalPurchases) * 100.0 else 0.0

        // Effective Valuation Date based on navDate
        val navDateParsed = try { SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).parse(navDate) } catch(e: Exception) { null }
        val effectiveValuationDate = navDateParsed ?: valuationDate

        // Build cashflows for Lifetime XIRR
        val xirrCashflows = mutableListOf<Pair<Long, Double>>()
        xirrCashflows.addAll(cashflows)

        if (currentUnits > 0.0001 && currentValue > 0) {
            xirrCashflows.add(effectiveValuationDate.time to +currentValue)
        }

        val lifetimeXirr = if (xirrCashflows.isNotEmpty() && totalPurchases > 0) {
            val resolved = trueXirr(xirrCashflows)
            if (resolved.isNaN() || resolved.isInfinite()) 0.0 else resolved
        } else 0.0

        // Build cashflows for Current Holding XIRR
        val holdingCashflows = mutableListOf<Pair<Long, Double>>()
        for (lot in activeLots) {
            holdingCashflows.add(lot.date.time to -(lot.units * lot.costPerUnit))
        }
        if (currentUnits > 0.0001 && currentValue > 0) {
            holdingCashflows.add(effectiveValuationDate.time to +currentValue)
        }
        val holdingXirr = if (holdingCashflows.isNotEmpty() && costOfCurrentHolding > 0) {
            val resolved = trueXirr(holdingCashflows)
            if (resolved.isNaN() || resolved.isInfinite()) 0.0 else resolved
        } else 0.0

        val isQsif = isQsifScheme(schemeName)
        val isFullyExited = currentUnits <= 0.0001

        return LifetimeFundPnl(
            schemeName = schemeName,
            isQsif = isQsif,
            totalPurchases = totalPurchases,
            totalRedemptions = totalRedemptions,
            currentUnits = currentUnits,
            latestNav = latestNav,
            navDate = navDate,
            currentValue = currentValue,
            costOfCurrentHolding = costOfCurrentHolding,
            costOfSoldUnits = costOfSoldUnits,
            realizedPnL = realizedPnL,
            unrealizedPnL = unrealizedPnL,
            unrealizedPnLPct = unrealizedPnLPct,
            holdingXirr = holdingXirr,
            totalAbsolutePnL = totalAbsolutePnL,
            absolutePnLPct = absolutePnLPct,
            lifetimeXirr = lifetimeXirr,
            activeLots = activeLots,
            decoratedTxns = decoratedTxns,
            buyCount = buyCount,
            sellCount = sellCount,
            isFullyExited = isFullyExited,
            rawCashflows = cashflows
        )
    }

    fun perTransactionIrr(lot: Lot, latestNav: Double, valuationDate: Date = getValuationDate()): Double {
        if (lot.costPerUnit <= 0 || lot.units <= 0) return 0.0
        val days = ((valuationDate.time - lot.date.time) / 86_400_000.0)
        if (days <= 0) return 0.0
        val ratio = latestNav / lot.costPerUnit
        return (Math.pow(ratio, 365.0 / days) - 1.0) * 100.0
    }

    fun trueXirr(cashflows: List<Pair<Long, Double>>, guess: Double = 0.1, clampMax: Double? = null): Double {
        if (cashflows.isEmpty()) return 0.0
        val t0 = cashflows.minOf { it.first }
        
        val npv = npvLabel@{ rate: Double ->
            var sum = 0.0
            val base = 1.0 + rate
            if (base <= 0.0) return@npvLabel Double.NaN
            for ((t, cf) in cashflows) {
                val years = (t - t0) / (365.0 * 86_400_000.0)
                val term = Math.pow(base, years)
                if (term == 0.0 || term.isNaN()) return@npvLabel Double.NaN
                sum += cf / term
            }
            sum
        }
        
        val dnpv = dnpvLabel@{ rate: Double ->
            var sum = 0.0
            val base = 1.0 + rate
            if (base <= 0.0) return@dnpvLabel Double.NaN
            for ((t, cf) in cashflows) {
                val years = (t - t0) / (365.0 * 86_400_000.0)
                val term = Math.pow(base, years + 1.0)
                if (term == 0.0 || term.isNaN()) return@dnpvLabel Double.NaN
                sum += -years * cf / term
            }
            sum
        }
        
        fun formatResult(rate: Double): Double {
            val safeR = if (rate.isNaN() || rate.isInfinite()) 0.0 else rate
            val lowerBounded = maxOf(safeR, -0.999)
            val finalR = if (clampMax != null) minOf(lowerBounded, clampMax) else lowerBounded
            return finalR * 100.0
        }

        // 1. Try Newton-Raphson
        var r = guess
        var success = false
        for (i in 0 until 100) {
            val v = npv(r)
            val d = dnpv(r)
            if (Math.abs(d) < 1e-12) break
            val step = v / d
            val newR = r - step
            if (newR.isNaN() || newR.isInfinite() || newR <= -0.999) {
                break
            }
            if (Math.abs(newR - r) < 1e-9) {
                r = newR
                success = true
                break
            }
            r = newR
        }
        
        if (success) {
            return formatResult(r)
        }
        
        // 2. Fallback to Bisection Method with wide dynamic search range
        var low = -0.99
        var high = 100.0 // up to 10000%
        var vLow = npv(low)
        var vHigh = npv(high)
        
        if (vLow.isNaN() || vHigh.isNaN()) {
            return 0.0
        }
        
        // If signs are the same, try to expand high up to 100,000%
        if (vLow * vHigh > 0) {
            for (h in listOf(1000.0, 10000.0, 100000.0)) {
                high = h
                vHigh = npv(high)
                if (vLow * vHigh <= 0) break
            }
            if (vLow * vHigh > 0) {
                low = -0.999
                vLow = npv(low)
            }
        }
        
        if (vLow * vHigh <= 0) {
            for (i in 0 until 100) {
                val mid = (low + high) / 2.0
                val vMid = npv(mid)
                if (vMid.isNaN() || Math.abs(high - low) < 1e-9) {
                    return formatResult(mid)
                }
                if (Math.abs(vMid) < 1e-12) {
                    return formatResult(mid)
                }
                if (vLow * vMid < 0) {
                    high = mid
                    vHigh = vMid
                } else {
                    low = mid
                    vLow = vMid
                }
            }
            val midFinal = (low + high) / 2.0
            return formatResult(midFinal)
        }
        
        return formatResult(r)
    }

    data class DecoratedTransaction(
        val txn: CasTransactionEntity,
        val holdingDays: Int = 0,
        val exitLoad: Double = 0.0,
        val taxPeriod: String = "—",
        val capitalGain: Double = 0.0
    )

    fun decorateTransactions(txns: List<CasTransactionEntity>, valuationDate: Date = getValuationDate()): List<DecoratedTransaction> {
        val format = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
        val sortedTxns = txns.sortedBy { 
            try { format.parse(it.date) } catch(e: Exception) { Date(0) }
        }
        var lots = mutableListOf<Lot>()
        val results = mutableListOf<DecoratedTransaction>()
        
        for (t in sortedTxns) {
            val isP = t.type == "Buy"
            val isR = t.type == "Sell"
            val d = try { format.parse(t.date) } catch(e: Exception) { null }
            
            if (d == null) {
                results.add(DecoratedTransaction(t))
                continue
            }
            
            if (isP && t.units > 0) {
                val grossAmount = kotlin.math.abs(t.amount)
                val cpu = grossAmount / t.units
                lots.add(Lot(t.units, cpu, d))
                results.add(DecoratedTransaction(t))
            } else if (isR) {
                val toSell = Math.abs(t.units)
                if (toSell <= 0 || lots.isEmpty()) {
                    results.add(DecoratedTransaction(t))
                    continue
                }
                var totalSellUnits = 0.0
                var weightedDays = 0.0
                var totalCost = 0.0
                var toConsume = toSell
                for (lot in lots) {
                    if (toConsume <= 0) break
                    val take = kotlin.math.min(lot.units, toConsume)
                    val lotDays = ((d.time - lot.date.time) / 86_400_000.0).toInt()
                    weightedDays += take * lotDays
                    totalSellUnits += take
                    totalCost += take * lot.costPerUnit
                    toConsume -= take
                }
                if (totalSellUnits <= 0) {
                    results.add(DecoratedTransaction(t))
                    continue
                }
                val avgHoldingDays = (weightedDays / totalSellUnits).toInt()
                val exitLoad = if (avgHoldingDays <= 15) {
                    kotlin.math.round(Math.abs(t.amount) * 0.01 * 100.0) / 100.0
                } else 0.0
                val taxPeriod = when {
                    avgHoldingDays <= 15 -> "Exit-Load (≤15d)"
                    avgHoldingDays <= 365 -> "STCG (16–365d)"
                    else -> "LTCG (>1y)"
                }
                val avgBuyNav = totalCost / totalSellUnits
                val capitalGain = kotlin.math.round((t.nav - avgBuyNav) * totalSellUnits * 100.0) / 100.0
                
                results.add(DecoratedTransaction(
                    txn = t,
                    holdingDays = avgHoldingDays,
                    exitLoad = exitLoad,
                    taxPeriod = taxPeriod,
                    capitalGain = capitalGain
                ))
                
                // Consume lots
                var remaining = toSell
                val newLots = mutableListOf<Lot>()
                for (lot in lots) {
                    if (remaining <= 0) {
                        newLots.add(lot)
                        continue
                    }
                    val take = kotlin.math.min(lot.units, remaining)
                    if (lot.units - take > 1e-9) {
                        newLots.add(Lot(lot.units - take, lot.costPerUnit, lot.date))
                    }
                    remaining -= take
                }
                lots.clear()
                lots.addAll(newLots)
            } else {
                results.add(DecoratedTransaction(t))
            }
        }
        return results
    }
}
