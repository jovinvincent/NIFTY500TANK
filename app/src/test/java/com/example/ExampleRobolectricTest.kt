package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import org.jsoup.Jsoup

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun testScraperDirectly() {
    val client = OkHttpClient.Builder()
        .connectTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    val url = "https://www.qsif.com/NAV/latestnav"
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .build()
    
    try {
        val response = client.newCall(request).execute()
        val responseString = response.body?.string() ?: ""
        println("P1_RESPONSE_LENGTH: ${responseString.length}")
        
        var currentDoc = Jsoup.parse(responseString)
        val table = currentDoc.getElementById("notice")
        if (table == null) {
            println("ERROR: ctl00_ContentPlaceHolder1_nav_sch or ID notice table not found!")
            return
        }
        val p1Rows = table.getElementsByTag("tr").size
        println("P1_ROWS_FOUND: $p1Rows")
        
        var currentPage = 1
        while (currentPage < 5) {
            val links = currentDoc.select("#ctl00_ContentPlaceHolder1_nav_sch a")
                .takeIf { it.isNotEmpty() }
                ?: currentDoc.select("a[href*=__doPostBack]")
            
            println("PAGE $currentPage - LINKS FOUND: ${links.size}")
            for (link in links) {
                println("  LINK TEXT: '${link.text()}', HREF: '${link.attr("href")}'")
            }
            
            var nextPageTarget: String? = null
            val targetPageStr = (currentPage + 1).toString()
            for (link in links) {
                val text = link.text().trim()
                val href = link.attr("href")
                if (text == targetPageStr) {
                    val match = Regex("""__doPostBack\(\s*['"]([^'"]+)['"]""").find(href)
                    if (match != null) {
                        nextPageTarget = match.groupValues[1]
                        println("FOUND NEXT PAGE TARGET FOR PAGE $targetPageStr: $nextPageTarget, HREF: $href")
                        break
                    }
                }
            }
            
            if (nextPageTarget == null) {
                println("TERMINATED: nextPageTarget is null for target page $targetPageStr")
                break
            }
            
            val form = currentDoc.getElementById("aspnetForm") ?: currentDoc.select("form").first()
            val params = mutableMapOf<String, String>()
            if (form != null) {
                val inputs = form.select("input")
                for (input in inputs) {
                    val name = input.attr("name")
                    val valStr = input.attr("value")
                    if (name.isNotEmpty() && input.attr("type") != "submit") {
                        params[name] = valStr
                    }
                }
            }
            
            params["__EVENTTARGET"] = nextPageTarget
            params["__EVENTARGUMENT"] = ""
            
            val formBodyBuilder = FormBody.Builder()
            for ((key, value) in params) {
                formBodyBuilder.add(key, value)
            }
            
            val postRequest = Request.Builder()
                .url(url)
                .post(formBodyBuilder.build())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", url)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Cache-Control", "no-cache")
                .build()
            
            println("POSTING FOR PAGE $targetPageStr...")
            val nextResponse = client.newCall(postRequest).execute()
            val nextPageHtml = nextResponse.body?.string() ?: ""
            println("PAGE $targetPageStr RESPONSE LENGTH: ${nextPageHtml.length}")
            
            if (nextPageHtml.isEmpty()) {
                println("TERMINATED: empty response for page $targetPageStr")
                break
            }
            
            currentDoc = Jsoup.parse(nextPageHtml)
            val nextTable = currentDoc.getElementById("notice")
            if (nextTable == null) {
                println("TERMINATED: notice table not found on page $targetPageStr")
                break
            }
            
            val nextTableRows = nextTable.getElementsByTag("tr")
            println("PAGE $targetPageStr ROWS: ${nextTableRows.size}")
            for (row in nextTableRows) {
                val cols = row.getElementsByTag("td")
                if (cols.size >= 5) {
                    val cells = cols.map { it.text().trim() }
                    if (!cells[0].equals("Date", ignoreCase = true)) {
                        println("  ROW - Date: '${cells[0]}', Scheme: '${cells[1]}', Option: '${cells[2]}', NAV: '${cells[3]}', Change: '${cells[4]}'")
                    }
                }
            }
            currentPage++
        }
    } catch(e: Exception) {
        println("SCRAPE_EXCEPTION: ${e.message}")
        e.printStackTrace()
    }
  }

  @Test
  fun testViewModelRefresh() {
    val context = ApplicationProvider.getApplicationContext<Context>() as android.app.Application
    val vm = NavViewModel(context)
    vm.refreshNavData()
    
    var attempts = 0
    while (vm.refreshing.value && attempts < 150) {
        Thread.sleep(100)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        attempts++
    }
    
    val state = vm.uiState.value
    println("VM_STATE_AFTER_REFRESH: $state")
    if (state is UiState.Error) {
        println("VM_ERROR_MESSAGE: ${state.message}")
    } else if (state is UiState.Success) {
        println("VM_SUCCESS_FUNDS_SIZE: ${state.items.size}")
        state.items.forEach {
            println("  FUND: ${it.schemeName} | NAV: ${it.fund.navValue}")
        }
    }
  }
}
