// CasParser.kt
package com.example

import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToLong

const val STAMP_DUTY_RATE = 0.00005
const val STT_RATE = 0.00001
const val PDF_PASSWORD = "jovi31490"
const val EXIT_LOAD_RATE = 0.01
const val EXIT_LOAD_DAYS = 15
const val STCG_DAYS = 365
const val STCG_TAX_RATE = 0.30
const val LTCG_TAX_RATE = 0.125
const val CESS_RATE = 0.04

val EXIT_LOAD_LABEL = "Exit-Load (<=15d)"
val STCG_LABEL = "STCG (16-365d)"
val LTCG_LABEL = "LTCG (>1y)"

private const val TAG = "QsifCamsParser"
private val PDF_DATE_FMT = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)

fun logInfo(tag: String, msg: String) {
    try {
        android.util.Log.i(tag, msg)
    } catch (e: Throwable) {
        println("[$tag] INFO: $msg")
    }
}

fun logWarn(tag: String, msg: String) {
    try {
        android.util.Log.w(tag, msg)
    } catch (e: Throwable) {
        println("[$tag] WARN: $msg")
    }
}

val QUANT_SIF_ISINS = setOf(
    "INF966L30019", "INF966L30159", "INF966L30274",
    "INF966L30241", "INF966L30076", "INF966L01EJ3",
)

val QUANT_SIF_ISINS_ORDERED = listOf(
    "INF966L30019", "INF966L30159", "INF966L30274", "INF966L30241", "INF966L30076", "INF966L01EJ3",
)

fun fundNameForIsin(isin: String): String = when (isin) {
    "INF966L30019" -> "Equity Long-Short Fund"
    "INF966L30159" -> "Equity Ex-Top 100 Long-Short Fund"
    "INF966L30274" -> "Sector Rotation Long-Short Fund"
    "INF966L30241" -> "Active Asset Allocator Long-Short Fund"
    "INF966L30076" -> "Hybrid Long-Short Fund"
    "INF966L01EJ3" -> "Quant Income Plus Arbitrage Active FoF - Direct (G)"
    else -> isin
}

data class ParsedTransaction(
    val id: Int,
    val schemeName: String,
    val date: String,
    val description: String,
    val amount: Double,
    val units: Double,
    val nav: Double,
    val balance: Double,
    val type: String,
    val stampDuty: Double = 0.0,
    val stt: Double = 0.0,
    val tds: Double = 0.0,
    val txnSubType: String = type,
    val isLateralShift: Boolean = false,
    val holdingDays: Int = 0,
    val exitLoad: Double = 0.0,
    val taxPeriod: String = "—",
    val capitalGain: Double = 0.0,
) {
    val isPurchase: Boolean get() = type == "Buy"
    val isRedemption: Boolean get() = type == "Sell"
}

data class Lot(
    val units: Double,
    val costPerUnit: Double,
    val buyDate: Date,
)

data class FundDetails(
    val name: String,
    val isin: String,
    val totalInvestorCost: Double,
    val balanceUnits: Double,
    val transactions: List<ParsedTransaction>,
    val remainingLots: List<Lot>,
    val weightedDays: Double,
)

data class FifoResult(
    val camsCost: Double,
    val balanceUnits: Double,
    val lots: List<Lot>,
    val weightedDays: Double,
)

data class TaxSummary(
    val stcg: Double = 0.0,
    val ltcg: Double = 0.0,
    val exitLoad: Double = 0.0,
    val totalTax: Double = 0.0,
)

fun parseDateRobust(dateStr: String): Date? {
    val trimmed = dateStr.trim()
    if (trimmed.isEmpty()) return null
    val patterns = arrayOf(
        "dd-MMM-yyyy",
        "dd-MMM-yy",
        "dd-MM-yyyy",
        "dd/MM/yyyy",
        "dd/MM/yy",
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "d-MMM-yyyy",
        "d-MMM-yy",
        "d/M/yyyy",
        "d-M-yyyy",
        "dd MMM yyyy",
        "dd MMM yy"
    )
    for (pat in patterns) {
        try {
            val sdf = SimpleDateFormat(pat, Locale.ENGLISH)
            sdf.isLenient = false
            val parsed = sdf.parse(trimmed)
            if (parsed != null) return parsed
        } catch (_: Exception) {}
    }
    return null
}

fun parseFunds(fullText: String): Map<String, FundDetails> {
    val funds = LinkedHashMap<String, FundDetails>()
    val txnsMap = LinkedHashMap<String, MutableList<ParsedTransaction>>()
    for (isin in QUANT_SIF_ISINS_ORDERED) {
        txnsMap[isin] = mutableListOf()
    }
    var currentIsin: String? = null
    var totalParsed = 0
    var totalSkipped = 0

    for (raw in fullText.split("\n")) {
        val line = raw.trim()
            .replace(Regex("""(\d+),\s+(\d+)"""), "$1,$2")
            .replace(Regex("""\(\s+"""), "(")
            .replace(Regex("""\s+\)"""), ")")
            .replace(Regex("""-\s+"""), "-")
        if (line.isEmpty()) continue
        val isinMatch = Regex("ISIN:\\s*(\\S+)").find(line)
        if (isinMatch != null) {
            currentIsin = isinMatch.groupValues[1].replace(Regex("[^\\w]"), "")
            continue
        }
        val isin = currentIsin ?: continue
        if (isin !in QUANT_SIF_ISINS) continue
        val fundName = fundNameForIsin(isin)
        val txns = txnsMap[isin] ?: continue
        if (shouldSkipLine(line)) continue
        if (isChargeLine(line)) {
            foldChargeIntoLastTxn(line, txns)
            continue
        }
        val dateMatch = Regex("""^(\d{1,2}[-/][A-Za-z]{3}[-/]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\s+(.+)""").find(line)
        if (dateMatch == null) continue
        val rawDateStr = dateMatch.groupValues[1]
        val parsedD = parseDateRobust(rawDateStr)
        val dateStr = if (parsedD != null) PDF_DATE_FMT.format(parsedD) else rawDateStr
        val rest = dateMatch.groupValues[2]
        val noNavDt = rest.replace(Regex("\\s*\\(NAV Dt\\s*:\\s*\\d{2}[/-]\\w{2,3}[/-]\\d{2,4}\\)"), "")
        val firstNumMatch = Regex("\\(?-?[\\d,]+\\.\\d+\\)?").find(noNavDt) ?: continue
        val typeRaw = noNavDt.substring(0, firstNumMatch.range.first).trim()
        val numbersPart = noNavDt.substring(firstNumMatch.range.first).trim()
        val typeClean = typeRaw
            .replace(Regex("\\*+"), "")
            .replace(Regex("\\s+$"), "")
            .replace(Regex("\\s+(Trxn\\.Ref\\.No\\..*|less.*|Inter Bank.*)$"), "")
            .trim()
        val normalizedType = normalizeTransactionType(typeClean)
        val numbers = Regex("\\(?-?[\\d,]+\\.\\d+\\)?")
            .findAll(numbersPart)
            .map { parseNum(it.value) }
            .toList()
        if (numbers.size < 4) {
            logWarn(TAG, "REJECTED ($fundName, n=${numbers.size}): $line")
            totalSkipped++
            continue
        }
        val isTableFormat = numbers.size >= 10
        val amount = if (isTableFormat) numbers[0] else numbers[numbers.size - 4]
        val rawUnits = if (isTableFormat) numbers[5] else numbers[numbers.size - 3]
        val rawNav = if (isTableFormat) numbers[6] else numbers[numbers.size - 2]
        val balance = if (isTableFormat) numbers[8] else numbers[numbers.size - 1]
        val isSell = normalizedType == "Sell" || amount < 0
        val units = if (isSell) -abs(rawUnits) else abs(rawUnits)
        val nav = abs(rawNav)
        val signedAmount = if (isSell) -abs(amount) else abs(amount)
        
        val stampDuty = if (isTableFormat) numbers[2] else (if (!isSell) round2(signedAmount * STAMP_DUTY_RATE) else 0.0)
        val stt = if (isTableFormat) numbers[1] else 0.0
        val tds = if (isTableFormat) numbers[3] else 0.0

        txns.add(ParsedTransaction(
            id = txns.size,
            schemeName = fundName,
            date = dateStr,
            description = typeClean,
            amount = signedAmount,
            units = units,
            nav = nav,
            balance = balance,
            type = if (isSell) "Sell" else "Buy",
            stampDuty = stampDuty,
            stt = stt,
            tds = tds,
            txnSubType = typeClean,
            isLateralShift = typeClean.contains("Lateral Shift", ignoreCase = true),
        ))
        totalParsed++
    }

    for (isin in QUANT_SIF_ISINS_ORDERED) {
        val txns = txnsMap[isin] ?: continue
        if (txns.isEmpty()) continue
        val fundName = fundNameForIsin(isin)
        val fifo = computeFifo(txns, isin)
        val valuationDate = makeValuationDate()
        classifyRedemptionsForTax(txns, valuationDate)
        funds[isin] = FundDetails(
            name = fundName, isin = isin,
            totalInvestorCost = fifo.camsCost,
            balanceUnits = fifo.balanceUnits,
            transactions = txns,
            remainingLots = fifo.lots,
            weightedDays = fifo.weightedDays,
        )
    }
    logInfo(TAG, "parseFunds: parsed=$totalParsed, skipped=$totalSkipped (expected: 42/0)")
    return funds
}

private fun isChargeLine(line: String): Boolean {
    if (!line.contains("***")) return false
    return Regex("\\*+\\s*([^*]+?)\\s*\\*+\\s*\\(?(-?[\\d,]+\\.?\\d*)\\)?\\s*$").containsMatchIn(line)
}

private fun foldChargeIntoLastTxn(line: String, txns: MutableList<ParsedTransaction>) {
    if (txns.isEmpty()) return
    val match = Regex("\\*+\\s*([^*]+?)\\s*\\*+\\s*\\(?(-?[\\d,]+\\.?\\d*)\\)?\\s*$").find(line) ?: return
    val chargeType = match.groupValues[1].trim().lowercase()
    val amount = parseNum(match.groupValues[2])
    val lastIdx = txns.size - 1
    val current = txns[lastIdx]
    txns[lastIdx] = when {
        "stt" in chargeType -> current.copy(stt = amount)
        "tds" in chargeType -> current.copy(tds = amount)
        "stamp" in chargeType -> current.copy(stampDuty = amount)
        else -> current
    }
}

private fun normalizeTransactionType(typeStr: String): String {
    val s = typeStr.lowercase()
    return when {
        s.contains("purchase") -> "Buy"
        s.contains("shift in") -> "Buy"
        s.contains("switch in") -> "Buy"
        s.contains("sip") && !s.contains("redemption") -> "Buy"
        s.contains("allotment") -> "Buy"
        s.contains("redemption") -> "Sell"
        s.contains("shift out") -> "Sell"
        s.contains("switch out") -> "Sell"
        s.contains("withdrawal") -> "Sell"
        else -> "Other"
    }
}

fun computeFifo(txns: List<ParsedTransaction>, isin: String): FifoResult {
    val lots = mutableListOf<Lot>()
    for (tx in txns) {
        if (!tx.isPurchase && !tx.isRedemption) continue
        val d = parseDateRobust(tx.date) ?: try { PDF_DATE_FMT.parse(tx.date) } catch (e: Exception) { null }
        if (d == null) { logWarn(TAG, "Bad date: ${tx.date}"); continue }
        when {
            tx.isPurchase && tx.units > 0 -> {
                val stamp = if (tx.stampDuty > 0) tx.stampDuty
                            else round2(tx.amount * STAMP_DUTY_RATE)
                lots.add(Lot(tx.units, (tx.amount + stamp + tx.stt) / tx.units, d))
            }
            tx.isRedemption -> {
                var toSell = abs(tx.units)
                val newLots = mutableListOf<Lot>()
                for (lot in lots) {
                    if (toSell <= 0) { newLots.add(lot); continue }
                    val take = min(lot.units, toSell).toDouble()
                    if (lot.units - take > 1e-9) {
                        newLots.add(Lot(lot.units - take, lot.costPerUnit, lot.buyDate))
                    }
                    toSell -= take
                }
                lots.clear(); lots.addAll(newLots)
            }
        }
    }
    val balanceUnits = round3(lots.sumOf { it.units })
    val totalCost = round2(lots.sumOf { it.units * it.costPerUnit })
    
    val valuationDate = makeValuationDate()
    val totalLotUnits = lots.sumOf { it.units }
    val weightedDays = if (totalLotUnits > 0) {
        lots.sumOf {
            val days = ((valuationDate.time - it.buyDate.time) / 86_400_000.0)
            it.units * days
        } / totalLotUnits
    } else {
        0.0
    }
    return FifoResult(totalCost, balanceUnits, lots.toList(), weightedDays)
}

fun makeValuationDate(): Date {
    val cal = Calendar.getInstance()
    cal.set(Calendar.MILLISECOND, 0)
    return cal.time
}

fun classifyRedemptionsForTax(txns: List<ParsedTransaction>, valuationDate: Date) {
    val lots = mutableListOf<Lot>()
    for (idx in txns.indices) {
        val tx = txns[idx]
        if (!tx.isPurchase && !tx.isRedemption) continue
        val d = parseDateRobust(tx.date) ?: try { PDF_DATE_FMT.parse(tx.date) } catch (e: Exception) { null } ?: continue
        when {
            tx.isPurchase && tx.units > 0 -> {
                val stamp = if (tx.stampDuty > 0) tx.stampDuty
                            else round2(tx.amount * STAMP_DUTY_RATE)
                lots.add(Lot(tx.units, (tx.amount + stamp + tx.stt) / tx.units, d))
            }
            tx.isRedemption -> {
                val toSell = abs(tx.units)
                if (toSell <= 0 || lots.isEmpty()) continue
                var totalSellUnits = 0.0
                var weightedDays = 0.0
                var totalCost = 0.0
                var toConsume = toSell
                for (lot in lots) {
                    if (toConsume <= 0) break
                    val take = min(lot.units, toConsume).toDouble()
                    val lotDays = ((d.time - lot.buyDate.time) / 86_400_000.0).toInt()
                    weightedDays += take * lotDays
                    totalSellUnits += take
                    totalCost += take * lot.costPerUnit
                    toConsume -= take
                }
                if (totalSellUnits <= 0) continue
                val avgHoldingDays = (weightedDays / totalSellUnits).toInt()
                val exitLoad = if (avgHoldingDays <= EXIT_LOAD_DAYS) {
                    round2(abs(tx.amount) * EXIT_LOAD_RATE)
                } else 0.0
                val taxPeriod = when {
                    avgHoldingDays <= EXIT_LOAD_DAYS -> EXIT_LOAD_LABEL
                    avgHoldingDays <= STCG_DAYS -> STCG_LABEL
                    else -> LTCG_LABEL
                }
                val avgBuyNav = totalCost / totalSellUnits
                val capitalGain = round2((tx.nav - avgBuyNav) * totalSellUnits)
                @Suppress("UNCHECKED_CAST")
                (txns as MutableList<ParsedTransaction>)[idx] = tx.copy(
                    holdingDays = avgHoldingDays,
                    exitLoad = exitLoad,
                    taxPeriod = taxPeriod,
                    capitalGain = capitalGain,
                )
                var remaining = toSell
                val newLots = mutableListOf<Lot>()
                for (lot in lots) {
                    if (remaining <= 0) { newLots.add(lot); continue }
                    val take = min(lot.units, remaining).toDouble()
                    if (lot.units - take > 1e-9) {
                        newLots.add(Lot(lot.units - take, lot.costPerUnit, lot.buyDate))
                    }
                    remaining -= take
                }
                lots.clear(); lots.addAll(newLots)
            }
        }
    }
}

fun calculateXirr(fund: FundDetails, latestNav: Double, valuationDate: Date = makeValuationDate()): Double {
    val totalUnits = fund.balanceUnits
    val totalCost = fund.totalInvestorCost
    if (totalUnits <= 0 || totalCost <= 0) return 0.0
    val weightedDays = fund.weightedDays
    if (weightedDays <= 0) return 0.0
    val ratio = (latestNav * totalUnits) / totalCost
    val exponent = 365.0 / weightedDays
    return (Math.pow(ratio, exponent) - 1.0) * 100.0
}

fun perTransactionIrr(lot: Lot, latestNav: Double, valuationDate: Date = makeValuationDate()): Double {
    if (lot.costPerUnit <= 0 || lot.units <= 0) return 0.0
    val days = ((valuationDate.time - lot.buyDate.time) / 86_400_000.0)
    if (days <= 0) return 0.0
    val ratio = latestNav / lot.costPerUnit
    return (Math.pow(ratio, 365.0 / days) - 1.0) * 100.0
}

fun trueXirr(cashflows: List<Pair<Long, Double>>, guess: Double = 0.1): Double {
    if (cashflows.isEmpty()) return 0.0
    val t0 = cashflows.minOf { it.first }
    val npv = { rate: Double ->
        cashflows.sumOf { (t, cf) ->
            val years = (t - t0) / (365.0 * 86_400_000.0)
            cf / Math.pow(1.0 + rate, years)
        }
    }
    val dnpv = { rate: Double ->
        cashflows.sumOf { (t, cf) ->
            val years = (t - t0) / (365.0 * 86_400_000.0)
            -years * cf / Math.pow(1.0 + rate, years + 1.0)
        }
    }
    var r = guess
    for (i in 0 until 100) {
        val v = npv(r)
        val d = dnpv(r)
        if (Math.abs(d) < 1e-12) break
        val newR = r - v / d
        if (Math.abs(newR - r) < 1e-9) return newR * 100.0
        r = newR
    }
    return r * 100.0
}

fun parseNum(s: String): Double =
    s.replace(",", "").replace("(", "-").replace(")", "").trim().toDoubleOrNull() ?: 0.0

fun round2(x: Double): Double = (x * 100).roundToLong() / 100.0
fun round3(x: Double): Double = (x * 1000).roundToLong() / 1000.0

private fun shouldSkipLine(line: String): Boolean {
    val patterns = listOf(
        "Address Updated from CVL Data", "Nominee", "Opening Unit Balance",
        "Closing Unit Balance", "Total Cost Value", "Market Value", "NAV on",
        "Entry Load", "Exit Load", "Graded Exit", "Folio No", "PAN:", "KYC:",
        "Registrar", "Statement Date", "This Consolidated", "This statement",
        "This CAS", "Email Id", "Mobile",
        "investor friendly", "registered", "common to", "missing from",
        "consolidate all", "check with", "reverse", "brought to",
        "of your family", "list the", "Period:",
        "CAMS", "CAMSCASWS", "Page ", "*** STT", "*** Stamp", "*** TDS",
        "KERALA", "KOTTAYAM", "INDIA", "PLASSANAL", "MEENACHIL",
        "THALAPPALAM", "OLAYATHIL", "PAN Number", "S.O.", "S/O VINCENT",
        "BIJI JOSE", "JOVIN VINCENT", "Jovin Vincent", " qsif ", "quant ",
    )
    if (patterns.any { it in line }) return true
    if (Regex("^\\d{2}-[A-Z][a-z]{2}-\\d{4}\\s+To\\s+\\d{2}-[A-Z][a-z]{2}-\\d{4}").containsMatchIn(line)) return true
    if (Regex("^\\s*Date\\s+Transaction", RegexOption.IGNORE_CASE).containsMatchIn(line)) return true
    return false
}

fun computeCapitalGainsTax(txns: List<ParsedTransaction>): TaxSummary {
    var stcg = 0.0
    var ltcg = 0.0
    var exitLoad = 0.0
    for (tx in txns) {
        if (!tx.isRedemption) continue
        if (tx.isLateralShift) continue
        when (tx.taxPeriod) {
            EXIT_LOAD_LABEL -> exitLoad += tx.exitLoad
            STCG_LABEL -> stcg += maxOf(0.0, tx.capitalGain)
            LTCG_LABEL -> ltcg += maxOf(0.0, tx.capitalGain)
        }
    }
    val stcgTax = stcg * (STCG_TAX_RATE + CESS_RATE)
    val ltcgTax = ltcg * (LTCG_TAX_RATE + CESS_RATE)
    return TaxSummary(stcg = stcg, ltcg = ltcg, exitLoad = exitLoad, totalTax = round2(stcgTax + ltcgTax))
}
