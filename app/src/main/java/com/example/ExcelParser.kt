package com.example

import android.content.Context
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight pure-Kotlin text/CSV CAS parser replacing heavy Apache POI Excel dependencies.
 */
object ExcelParser {
    fun parseDateRobust(dateStr: String): Date? = com.example.parseDateRobust(dateStr)

    fun parseFunds(context: Context, uri: Uri): Map<String, FundDetails> {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot open file")

        if (rawBytes.size >= 4) {
            val isPdf = rawBytes[0] == '%'.code.toByte() && rawBytes[1] == 'P'.code.toByte() &&
                rawBytes[2] == 'D'.code.toByte() && rawBytes[3] == 'F'.code.toByte()
            val isZipXlsx = rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte()
            val isOleXls = rawBytes[0] == 0xD0.toByte() && rawBytes[1] == 0xCF.toByte()
            if (isPdf || isZipXlsx || isOleXls) {
                throw IllegalArgumentException(
                    "Binary PDF/Excel parsing has been removed to keep the app lightweight. Please export/import JSON backup or plain CSV/text."
                )
            }
        }

        val text = rawBytes.toString(Charsets.UTF_8)
        val textFunds = com.example.parseFunds(text)
        if (textFunds.isNotEmpty()) return textFunds

        return parseCsvFunds(text)
    }

    private fun parseCsvFunds(csvText: String): Map<String, FundDetails> {
        val funds = mutableMapOf<String, FundDetails>()
        val dateFmtOutput = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
        var globalTxnId = 1
        var currentIsin = ""
        var currentFundName = ""
        val txnsByIsin = linkedMapOf<String, MutableList<ParsedTransaction>>()

        for (rawLine in csvText.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val isinMatch = Regex("ISIN:\\s*(\\S+)").find(line)
            if (isinMatch != null) {
                currentIsin = isinMatch.groupValues[1].replace(Regex("[^\\w]"), "")
                currentFundName = fundNameForIsin(currentIsin)
                txnsByIsin.getOrPut(currentIsin) { mutableListOf() }
                continue
            }
            val cols = line.split(',').map { it.trim().trim('"') }
            if (cols.size < 14 || cols[0].toIntOrNull() == null) continue
            val isinKey = currentIsin.ifEmpty { "CSV_FUND" }
            val dateParsed = parseDateRobust(cols[1])
            val dateStr = if (dateParsed != null) dateFmtOutput.format(dateParsed) else cols[1]
            val typeStr = cols[4]
            val amount = cols[5].replace(",", "").toDoubleOrNull() ?: 0.0
            val stt = cols[6].replace(",", "").toDoubleOrNull() ?: 0.0
            val stamp = cols[7].replace(",", "").toDoubleOrNull() ?: 0.0
            val tds = cols[8].replace(",", "").toDoubleOrNull() ?: 0.0
            val units = cols[10].replace(",", "").toDoubleOrNull() ?: 0.0
            val nav = cols[11].replace(",", "").toDoubleOrNull() ?: 0.0
            val balance = cols[13].replace(",", "").toDoubleOrNull() ?: 0.0
            val type = when {
                typeStr.contains("Purchase", true) || typeStr.contains("Shift In", true) ||
                    typeStr.contains("Switch In", true) || typeStr.contains("SIP", true) ||
                    typeStr.contains("Allotment", true) -> "Buy"
                typeStr.contains("Redemption", true) || typeStr.contains("Shift Out", true) ||
                    typeStr.contains("Switch Out", true) || typeStr.contains("Withdrawal", true) -> "Sell"
                else -> "Other"
            }
            val list = txnsByIsin.getOrPut(isinKey) { mutableListOf() }
            list.add(
                ParsedTransaction(
                    id = globalTxnId++,
                    schemeName = currentFundName.ifEmpty { isinKey },
                    date = dateStr,
                    description = typeStr,
                    amount = amount,
                    units = units,
                    nav = nav,
                    balance = balance,
                    type = type,
                    stampDuty = stamp,
                    stt = stt,
                    tds = tds,
                    isLateralShift = typeStr.contains("Lateral", true)
                )
            )
        }

        val valuationDate = makeValuationDate()
        for ((isin, txns) in txnsByIsin) {
            if (txns.isEmpty()) continue
            val name = fundNameForIsin(isin)
            val fifo = computeFifo(txns, isin)
            classifyRedemptionsForTax(txns, valuationDate)
            funds[isin] = FundDetails(
                name = name,
                isin = isin,
                totalInvestorCost = fifo.camsCost,
                balanceUnits = fifo.balanceUnits,
                transactions = txns,
                remainingLots = fifo.lots,
                weightedDays = fifo.weightedDays
            )
        }
        return funds
    }
}
