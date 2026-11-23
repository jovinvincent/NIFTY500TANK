package com.example

import org.junit.Test

class RegexTest {
    @Test
    fun testRegex() {
        val line = "15-Jun-2026 Additional Purchase 49,997.50 4,850.641 10.3074 14,850.141"
        val dateMatch = Regex("""(\d{2}-[A-Z][a-z]{2}-\d{4})\s+(.+)""").find(line)
        val rest = dateMatch!!.groupValues[2]
        val noNavDt = rest.replace(Regex("""\s*\(NAV Dt\s*:\s*\d{2}[/-]\w{2,3}[/-]\d{2,4}\)"""), "")
        val firstNumMatch = Regex("""\(?-?[\d,]+\.\d+\)?""").find(noNavDt)
        val numbersPart = noNavDt.substring(firstNumMatch!!.range.first).trim()
        val numbers = Regex("""\(?-?[\d,]+\.\d+\)?""")
            .findAll(numbersPart)
            .map { it.value }
            .toList()
        println("Numbers: \$numbers")
    }
}
