package com.example.depthtank.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

class TankRepository(
    private val context: Context,
    private val remoteDataSource: RemoteTankDataSource = LiveMarketTankDataSource(context)
) {

    companion object {
        private const val TAG = "TankRepository"
        private const val SEED_STOCKS_ASSET = "nifty500_stocks_seed.json"
        private const val SEED_HISTORY_ASSET = "nifty500_tank_history.json"
        private const val SUBINDEX_HISTORY_ASSET = "subindex_tank_history.json"
        private const val INDEX_CONSTITUENTS_ASSET = "index_constituents.json"
        private const val INDIA_VIX_ASSET = "india_vix_historical.json"
        private const val MMI_HISTORY_ASSET = "goodreturns_mmi_history.json"
        private const val ADVANCE_DECLINE_ASSET = "advance_decline_historical.json"
        private const val VALUATION_PE_ASSET = "valuation_pe_history.json.gz"
        private const val LEGACY_LIVE_CACHE_FILE = "nifty500_live_snapshot.json"
        private const val LEGACY_LIVE_CACHE_V2_FILE = "nifty500_live_snapshot_v2.json"
        private const val LIVE_CACHE_FILE = "nifty500_live_snapshot_v3.json"
        private const val LIVE_HISTORY_VAULT_FILE = "live_history_vault_v1.json"
        private const val LIVE_MACRO_VAULT_FILE = "live_macro_vault_v1.json"
        private const val PRE2016_MANIFEST_ASSET = "pre2016_manifest.json"
        internal const val PRE2016_CACHE_DIR_NAME = "pre2016_archive_cache_v1"
        internal const val PRE2016_BOUNDARY_DATE = "2016-04-01"
        private const val FULL_INCEPTION_CYCLE_START = "1995-01-01"
        private const val PE_CAUSAL_MAX_STALE_DAYS = 14L
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    }

    private data class StockPeAnchor(
        val peTtm: Double,
        val peLow52W: Double,
        val peHigh52W: Double,
        val epsTtm: Double?
    )

    private data class HistoricalPeSample(
        val date: String,
        val epochDay: Long,
        val peTankLevel: Double,
        val medianPe: Double?,
        val peActiveCount: Int,
        val peScoreBins: List<Int>
    )

    private data class OfficialIndexPeSample(
        val date: String,
        val epochDay: Long,
        val indexPe: Double,
        val indexPb: Double?,
        val indexDivYield: Double?
    )

    private data class ValuationPeArchive(
        val stockAnchors: Map<String, StockPeAnchor>,
        val universeSamples: Map<TankIndexUniverse, List<HistoricalPeSample>>,
        val officialIndexPeSamples: Map<TankIndexUniverse, List<OfficialIndexPeSample>> = emptyMap()
    )

    private val loadMutex = Mutex()
    private val pre2016Mutex = Mutex()
    private var initialized = false
    private var pre2016Loaded = false
    private var seedLatestDate: String = "2026-11-13"
    @Volatile
    private var valuationPeArchive: ValuationPeArchive? = null

    private val _stocks = MutableStateFlow<List<StockTank>>(emptyList())
    val stocks: StateFlow<List<StockTank>> = _stocks.asStateFlow()

    private val _history = MutableStateFlow<List<TankHistoryPoint>>(emptyList())
    val history: StateFlow<List<TankHistoryPoint>> = _history.asStateFlow()

    private val _pipelineStatus = MutableStateFlow(SyncPipelineStatus())
    val pipelineStatus: StateFlow<SyncPipelineStatus> = _pipelineStatus.asStateFlow()

    private val _fourPillarSnapshot = MutableStateFlow(FourPillarSnapshot())
    val fourPillarSnapshot: StateFlow<FourPillarSnapshot> = _fourPillarSnapshot.asStateFlow()

    private val _detectedCycles = MutableStateFlow<List<MarketCycleTurningPoint>>(emptyList())
    val detectedCycles: StateFlow<List<MarketCycleTurningPoint>> = _detectedCycles.asStateFlow()

    private val _liveSignal = MutableStateFlow<CausalLiveSignal?>(null)
    val liveSignal: StateFlow<CausalLiveSignal?> = _liveSignal.asStateFlow()

    private val _datasetIntegrity = MutableStateFlow(DatasetIntegrityMetadata())
    val datasetIntegrity: StateFlow<DatasetIntegrityMetadata> = _datasetIntegrity.asStateFlow()

    private val _historyIntegrity = MutableStateFlow(DatasetIntegrityMetadata())
    val historyIntegrity: StateFlow<DatasetIntegrityMetadata> = _historyIntegrity.asStateFlow()

    private val _universeConstituents = MutableStateFlow<Map<TankIndexUniverse, Set<String>>>(emptyMap())
    val universeConstituents: StateFlow<Map<TankIndexUniverse, Set<String>>> = _universeConstituents.asStateFlow()

    private val _universeHistories = MutableStateFlow<Map<TankIndexUniverse, List<TankHistoryPoint>>>(emptyMap())
    val universeHistories: StateFlow<Map<TankIndexUniverse, List<TankHistoryPoint>>> = _universeHistories.asStateFlow()

    private val _macroOverlays = MutableStateFlow<Map<String, MacroOverlayPoint>>(emptyMap())
    val macroOverlays: StateFlow<Map<String, MacroOverlayPoint>> = _macroOverlays.asStateFlow()

    private val _archiveHydrationState = MutableStateFlow(ArchiveHydrationState.NOT_LOADED)
    val archiveHydrationState: StateFlow<ArchiveHydrationState> = _archiveHydrationState.asStateFlow()

    private val _archiveSummary = MutableStateFlow(ArchiveProvenanceSummary())
    val archiveSummary: StateFlow<ArchiveProvenanceSummary> = _archiveSummary.asStateFlow()

    suspend fun ensureInitialized() {
        if (initialized && _history.value.isNotEmpty() && _stocks.value.isNotEmpty()) return
        loadMutex.withLock {
            if (initialized && _history.value.isNotEmpty() && _stocks.value.isNotEmpty()) return@withLock
            withContext(Dispatchers.IO) {
                deleteLegacyCacheIfPresent()
                val seedHistoryPayload = loadSeedHistoryPayload()
                val baseHistory = seedHistoryPayload.points
                val baseHistoryIntegrity = seedHistoryPayload.integrity
                val constituentsMap = loadUniverseConstituents()
                _universeConstituents.value = constituentsMap
                val baseSubHistories = loadSubIndexHistories(baseHistory)
                seedLatestDate = baseHistory.lastOrNull()?.date ?: "2026-11-13"

                val seedMacros = loadMacroOverlays()
                val vaultedMacros = loadLiveMacroVault()
                var mergedMacros = mergeMacroOverlayMaps(seedMacros, vaultedMacros)

                val peArchive = getOrLoadValuationPeArchive()
                val seedAllHistories = (baseSubHistories + (TankIndexUniverse.NIFTY_500 to baseHistory))
                    .mapValues { (u, list) ->
                        attachValuationPeToHistory(
                            list,
                            peArchive?.universeSamples?.get(u),
                            peArchive?.officialIndexPeSamples?.get(u)
                        )
                    }
                val vaultedHistoryExtras = loadLiveHistoryVault()
                val restoredAllHistories = mergeExtraPointsIntoUniverses(seedAllHistories, vaultedHistoryExtras)
                    .mapValues { (u, list) ->
                        attachValuationPeToHistory(
                            list,
                            peArchive?.universeSamples?.get(u),
                            peArchive?.officialIndexPeSamples?.get(u)
                        )
                    }

                val rawSeedStocks = enrichStocksWithPeAnchors(loadSeedStocks(), peArchive?.stockAnchors)
                val baseStocks = TankCalculator.filterEligibleConstituents(rawSeedStocks)
                val excludedSeedCount = (rawSeedStocks.size - baseStocks.size).coerceAtLeast(0)

                val cached = loadLiveCache()
                val eligibleCached = cached?.let {
                    TankCalculator.filterEligibleConstituents(enrichStocksWithPeAnchors(it.stocks, peArchive?.stockAnchors))
                } ?: emptyList()
                val useCache = cached != null &&
                    eligibleCached.size >= 450 &&
                    NseMarketCalendar.isValidTradingIsoDate(cached.tradeDate) &&
                    cached.tradeDate >= seedLatestDate

                if (useCache) {
                    val mergedAllHistories = mergeAllUniverses(
                        baseHistories = restoredAllHistories,
                        stocks = eligibleCached,
                        constituents = constituentsMap,
                        tradeDate = cached.tradeDate
                    )
                    val mergedHistory = mergedAllHistories[TankIndexUniverse.NIFTY_500] ?: baseHistory

                    // Ensure cached live session A/D is reflected if vaulted macro lacks it
                    val cachedAd = computePerUniverseAdPct(eligibleCached, constituentsMap)
                    if (cachedAd.isNotEmpty() && mergedMacros[cached.tradeDate]?.adPctByUniverse.isNullOrEmpty()) {
                        mergedMacros = applyMacroSnapshot(
                            existing = mergedMacros,
                            snapshot = RemoteMacroSnapshot(
                                adPctByDateAndUniverse = mapOf(cached.tradeDate to cachedAd)
                            )
                        )
                    }
                    _macroOverlays.value = mergedMacros

                    val liveIntegrity = DatasetIntegrityMetadata(
                        dataQuality = DataQuality.VERIFIED_EOD,
                        constituentCoverage = roundFourDecimals(
                            eligibleCached.size.toDouble() / DatasetIntegrityMetadata.INDEX_TARGET_CONSTITUENTS
                        ).coerceIn(0.0, 1.0),
                        isIllustrative = false,
                        lastVerifiedTradingDate = cached.tradeDate,
                        excludedInsufficientHistoryCount = (cached.stocks.size - eligibleCached.size).coerceAtLeast(0)
                    )
                    publishState(
                        stocks = eligibleCached,
                        history = mergedHistory,
                        allUniverseHistories = mergedAllHistories,
                        liveIntegrity = liveIntegrity,
                        historyIntegrity = baseHistoryIntegrity,
                        sessionDate = cached.tradeDate
                    )
                    _pipelineStatus.value = _pipelineStatus.value.copy(
                        lastSyncedDisplay = cached.syncedDisplay,
                        lastTradeDate = cached.tradeDate,
                        sourceDisplay = cached.sourceName,
                        trackedStocksCount = eligibleCached.size,
                        isLiveHydrated = true,
                        integrity = liveIntegrity,
                        historyIntegrity = baseHistoryIntegrity
                    )
                } else {
                    _macroOverlays.value = mergedMacros
                    val vaultHistoryN500 = restoredAllHistories[TankIndexUniverse.NIFTY_500] ?: baseHistory
                    val latestVaultDate = vaultHistoryN500.lastOrNull()?.date ?: seedLatestDate
                    val vaultIntegrity = DatasetIntegrityMetadata(
                        dataQuality = DataQuality.VERIFIED_EOD,
                        constituentCoverage = roundFourDecimals(
                            baseStocks.size.toDouble() / DatasetIntegrityMetadata.INDEX_TARGET_CONSTITUENTS
                        ).coerceIn(0.0, 1.0),
                        isIllustrative = false,
                        lastVerifiedTradingDate = latestVaultDate,
                        excludedInsufficientHistoryCount = excludedSeedCount
                    )
                    publishState(
                        stocks = baseStocks,
                        history = vaultHistoryN500,
                        allUniverseHistories = restoredAllHistories,
                        liveIntegrity = vaultIntegrity,
                        historyIntegrity = baseHistoryIntegrity,
                        sessionDate = latestVaultDate
                    )
                    _pipelineStatus.value = _pipelineStatus.value.copy(
                        lastSyncedDisplay = "Auto-Synced: EOD Vault (${NseMarketCalendar.formatDdMmmYyyy(latestVaultDate)})",
                        lastTradeDate = latestVaultDate,
                        trackedStocksCount = baseStocks.size,
                        isLiveHydrated = false,
                        integrity = vaultIntegrity,
                        historyIntegrity = baseHistoryIntegrity
                    )
                }
                initialized = true
            }
        }
    }

    suspend fun ensurePre2016Loaded(forceRebuildCache: Boolean = false): Result<Unit> {
        ensureInitialized()
        if (pre2016Loaded && !forceRebuildCache && _archiveHydrationState.value == ArchiveHydrationState.READY) {
            return Result.success(Unit)
        }
        return pre2016Mutex.withLock {
            if (pre2016Loaded && !forceRebuildCache && _archiveHydrationState.value == ArchiveHydrationState.READY) {
                return@withLock Result.success(Unit)
            }
            _archiveHydrationState.value = ArchiveHydrationState.LOADING
            withContext(Dispatchers.IO) {
                try {
                    val manifestRaw = context.assets.open(PRE2016_MANIFEST_ASSET).bufferedReader().use { it.readText() }
                    val manifest = JSONObject(manifestRaw)
                    val startDate = manifest.optString("startDate", "1995-01-02")
                    val endDate = manifest.optString("endDate", "2016-03-31")
                    val firstActive200dDate = manifest.optString("firstActive200dDate", "1995-08-25")
                    val reconstructedPeriodEnd = manifest.optString("reconstructedPeriodEnd", "1998-07-31")
                    val vixInceptionDate = manifest.optString("vixInceptionDate", DeadlyComboEngine.INDIA_VIX_INCEPTION_DATE)
                    val mmiInceptionDate = manifest.optString("mmiInceptionDate", DeadlyComboEngine.MMI_VERIFIED_START_DATE)
                    val filesObj = manifest.getJSONObject("files")

                    val tankSpec = filesObj.getJSONObject("tankHistory")
                    val subSpec = filesObj.getJSONObject("subIndexHistory")
                    val macroSpec = filesObj.getJSONObject("macroOverlays")

                    val cacheDir = File(context.filesDir, PRE2016_CACHE_DIR_NAME).apply { mkdirs() }
                    val (tankJson, tankFromCache) = readOrDecompressVerifiedAsset(
                        cacheFile = File(cacheDir, "pre2016_tank_history.json"),
                        assetName = tankSpec.getString("asset"),
                        expectedRawSha256 = tankSpec.getString("rawSha256"),
                        forceRebuild = forceRebuildCache
                    )
                    val (subJson, subFromCache) = readOrDecompressVerifiedAsset(
                        cacheFile = File(cacheDir, "pre2016_subindex_history.json"),
                        assetName = subSpec.getString("asset"),
                        expectedRawSha256 = subSpec.getString("rawSha256"),
                        forceRebuild = forceRebuildCache
                    )
                    val (macroJson, macroFromCache) = readOrDecompressVerifiedAsset(
                        cacheFile = File(cacheDir, "pre2016_macro_overlays.json"),
                        assetName = macroSpec.getString("asset"),
                        expectedRawSha256 = macroSpec.getString("rawSha256"),
                        forceRebuild = forceRebuildCache
                    )

                    val parser = (remoteDataSource as? LiveMarketTankDataSource) ?: LiveMarketTankDataSource(context)
                    val preN500 = JSONObject(tankJson).optJSONArray("history")
                        ?.let { parser.parseHistoryArray(it, validateCalendar = false) }
                        ?.filter { it.date < PRE2016_BOUNDARY_DATE }
                        ?.sortedBy { it.date }
                        .orEmpty()
                    if (preN500.isEmpty()) {
                        throw IllegalStateException("Pre-2016 tank history archive parsed zero records")
                    }

                    val peArchive = getOrLoadValuationPeArchive()
                    val preByUniverse = LinkedHashMap<TankIndexUniverse, List<TankHistoryPoint>>(TankIndexUniverse.entries.size)
                    preByUniverse[TankIndexUniverse.NIFTY_500] = attachValuationPeToHistory(
                        preN500,
                        peArchive?.universeSamples?.get(TankIndexUniverse.NIFTY_500),
                        peArchive?.officialIndexPeSamples?.get(TankIndexUniverse.NIFTY_500)
                    )
                    val subRoot = JSONObject(subJson).optJSONObject("subIndices")
                    for (u in TankIndexUniverse.entries) {
                        if (u == TankIndexUniverse.NIFTY_500) continue
                        val arr = subRoot?.optJSONObject(u.subIndexHistoryKey)?.optJSONArray("history") ?: continue
                        val pts = parser.parseHistoryArray(arr, validateCalendar = false)
                            .filter { it.date < PRE2016_BOUNDARY_DATE }
                            .sortedBy { it.date }
                        if (pts.isNotEmpty()) {
                            preByUniverse[u] = attachValuationPeToHistory(
                                pts,
                                peArchive?.universeSamples?.get(u),
                                peArchive?.officialIndexPeSamples?.get(u)
                            )
                        }
                    }

                    val preMacros = parsePre2016MacroOverlays(macroJson, vixInceptionDate)

                    loadMutex.withLock {
                        val existingAll = _universeHistories.value.ifEmpty {
                            mapOf(TankIndexUniverse.NIFTY_500 to _history.value)
                        }
                        val joinedAll = LinkedHashMap<TankIndexUniverse, List<TankHistoryPoint>>(TankIndexUniverse.entries.size)
                        for (u in TankIndexUniverse.entries) {
                            val modern = existingAll[u].orEmpty().filter { it.date >= PRE2016_BOUNDARY_DATE }
                            val pre = preByUniverse[u].orEmpty()
                            val combined = ArrayList<TankHistoryPoint>(pre.size + modern.size)
                            combined.addAll(pre)
                            combined.addAll(modern)
                            joinedAll[u] = combined
                        }
                        val joinedN500 = joinedAll[TankIndexUniverse.NIFTY_500].orEmpty()

                        val existingMacros = _macroOverlays.value
                        val joinedMacros = LinkedHashMap<String, MacroOverlayPoint>(preMacros.size + existingMacros.size)
                        for ((dt, pt) in preMacros) {
                            if (dt < PRE2016_BOUNDARY_DATE) {
                                joinedMacros[dt] = pt
                            }
                        }
                        for ((dt, pt) in existingMacros) {
                            if (dt >= PRE2016_BOUNDARY_DATE) {
                                joinedMacros[dt] = pt
                            }
                        }

                        _macroOverlays.value = joinedMacros
                        _universeHistories.value = joinedAll
                        _history.value = joinedN500
                        _detectedCycles.value = CycleTurningPointEngine.detectCyclesRetrospective(
                            joinedN500,
                            CycleDetectionConfig(startIsoDate = FULL_INCEPTION_CYCLE_START)
                        )
                        _archiveSummary.value = ArchiveProvenanceSummary(
                            state = ArchiveHydrationState.READY,
                            startDate = startDate,
                            endDate = endDate,
                            firstActive200dDate = firstActive200dDate,
                            reconstructedPeriodEnd = reconstructedPeriodEnd,
                            vixInceptionDate = vixInceptionDate,
                            mmiInceptionDate = mmiInceptionDate,
                            pre2016SessionCount = preN500.size,
                            pre2016VixSessionCount = preMacros.values.count { it.indiaVix != null },
                            loadedFromPersistentCache = tankFromCache && subFromCache && macroFromCache,
                            sha256Verified = true,
                            errorMessage = null
                        )
                        pre2016Loaded = true
                        _archiveHydrationState.value = ArchiveHydrationState.READY
                    }
                    Result.success(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed hydrating pre-2016 archive", e)
                    _archiveSummary.value = _archiveSummary.value.copy(
                        state = ArchiveHydrationState.FAILED,
                        sha256Verified = false,
                        errorMessage = e.message ?: "Archive checksum or decompression failed"
                    )
                    _archiveHydrationState.value = ArchiveHydrationState.FAILED
                    Result.failure(e)
                }
            }
        }
    }

    private fun readOrDecompressVerifiedAsset(
        cacheFile: File,
        assetName: String,
        expectedRawSha256: String,
        forceRebuild: Boolean
    ): Pair<String, Boolean> {
        val expectedNorm = expectedRawSha256.trim().lowercase(Locale.US)
        if (!forceRebuild && cacheFile.exists()) {
            try {
                val cachedBytes = cacheFile.readBytes()
                if (sha256Hex(cachedBytes) == expectedNorm) {
                    return String(cachedBytes, Charsets.UTF_8) to true
                }
                Log.w(TAG, "Checksum mismatch on cached ${cacheFile.name}; rebuilding from gzip asset")
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading cached ${cacheFile.name}; rebuilding", e)
            }
            runCatching { cacheFile.delete() }
        }

        val assetStream = try {
            context.assets.open(assetName)
        } catch (notFound: FileNotFoundException) {
            if (assetName.endsWith(".gz", ignoreCase = true)) {
                context.assets.open(assetName.removeSuffix(".gz"))
            } else {
                throw notFound
            }
        }
        val rawBytes = assetStream.use { rawInput ->
            val buffered = BufferedInputStream(rawInput, 16_384)
            buffered.mark(2)
            val b0 = buffered.read()
            val b1 = buffered.read()
            buffered.reset()
            val isGzip = (b0 == 0x1f && b1 == 0x8b)
            val decodedStream = if (isGzip) GZIPInputStream(buffered) else buffered
            decodedStream.use { stream ->
                val out = ByteArrayOutputStream(65_536)
                val buf = ByteArray(16_384)
                while (true) {
                    val read = stream.read(buf)
                    if (read <= 0) break
                    out.write(buf, 0, read)
                }
                out.toByteArray()
            }
        }

        val actualSha = sha256Hex(rawBytes)
        if (actualSha != expectedNorm) {
            throw IllegalStateException("SHA-256 mismatch for $assetName: expected=$expectedNorm actual=$actualSha")
        }

        val tmpFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        try {
            tmpFile.writeBytes(rawBytes)
            if (cacheFile.exists()) cacheFile.delete()
            if (!tmpFile.renameTo(cacheFile)) {
                cacheFile.writeBytes(rawBytes)
                tmpFile.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not persist decompressed cache for ${cacheFile.name}", e)
            runCatching { tmpFile.delete() }
        }

        return String(rawBytes, Charsets.UTF_8) to false
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val hi = (b.toInt() ushr 4) and 0x0F
            val lo = b.toInt() and 0x0F
            sb.append("0123456789abcdef"[hi])
            sb.append("0123456789abcdef"[lo])
        }
        return sb.toString()
    }

    private fun parsePre2016MacroOverlays(
        rawJson: String,
        vixInceptionDate: String
    ): Map<String, MacroOverlayPoint> {
        val root = JSONObject(rawJson)
        val arr = root.optJSONArray("dates") ?: return emptyMap()
        val out = LinkedHashMap<String, MacroOverlayPoint>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val dt = obj.optString("date", "").trim()
            if (dt.length != 10 || dt >= PRE2016_BOUNDARY_DATE) continue
            val rawVix = if (obj.has("vix") && !obj.isNull("vix")) {
                obj.optDouble("vix", Double.NaN).takeIf { !it.isNaN() && it > 0.0 }
            } else {
                null
            }
            val safeVix = if (dt >= vixInceptionDate) rawVix else null
            val adObj = obj.optJSONObject("ad")
            val adMap = LinkedHashMap<TankIndexUniverse, Double>(TankIndexUniverse.entries.size)
            if (adObj != null) {
                for (u in TankIndexUniverse.entries) {
                    val v = adObj.optDouble(u.constituentKey, Double.NaN)
                    if (!v.isNaN() && v in 0.0..100.0) {
                        adMap[u] = v
                    }
                }
            }
            out[dt] = MacroOverlayPoint(
                date = dt,
                indiaVix = safeVix,
                mmiValue = null,
                adPctByUniverse = adMap
            )
        }
        return out
    }

    suspend fun refreshLiveMarketData(silent: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        ensureInitialized()
        val nowCheckIst = ZonedDateTime.now(NseMarketCalendar.IST_ZONE)
        _pipelineStatus.value = _pipelineStatus.value.copy(
            isSyncing = if (silent) _pipelineStatus.value.isSyncing else true,
            isLiveMarketActive = NseMarketCalendar.isMarketOpen(nowCheckIst),
            errorMessage = null
        )

        val priorStocksMap = _stocks.value.associateBy { it.symbol }
        val priorTradeDate = _pipelineStatus.value.lastTradeDate

        val (remoteResult, macroResult) = coroutineScope {
            val stocksDeferred = async { remoteDataSource.fetchLiveNifty500Stocks() }
            val macroDeferred = async { remoteDataSource.fetchLiveMacroSnapshot() }
            stocksDeferred.await() to macroDeferred.await()
        }

        val fetchedMacro = macroResult.getOrNull()

        return@withContext remoteResult.fold(
            onSuccess = { payload ->
                val nowIst = ZonedDateTime.now(NseMarketCalendar.IST_ZONE)
                val expectedLatestSession = NseMarketCalendar.latestMarketSessionDate(nowIst).toString()
                val tradeDate = payload.sessionDate
                    ?.takeIf { NseMarketCalendar.isValidTradingIsoDate(it) && it <= expectedLatestSession }
                    ?: expectedLatestSession
                val formattedDate = NseMarketCalendar.formatDdMmmYyyy(tradeDate)
                val isMarketOpenNow = NseMarketCalendar.isMarketOpen(nowIst)
                val isMarketOffToday = !NseMarketCalendar.isTradingDay(nowIst.toLocalDate()) ||
                    nowIst.toLocalTime().isBefore(LocalTime.of(9, 15))
                val syncedDisplay = when {
                    isMarketOpenNow -> "LIVE: $formattedDate • ${nowIst.format(TIME_FORMATTER)} IST"
                    isMarketOffToday && tradeDate != nowIst.toLocalDate().toString() ->
                        "Synced: $formattedDate (Market Off)"
                    else -> "Auto-Synced: $formattedDate • ${nowIst.format(TIME_FORMATTER)} IST"
                }

                // Backfill prior52WHigh from prior session snapshot (prefer TV [1] bar if available, else cached snapshot)
                val tvPriorDayMap = payload.historicalStocksByDate.entries
                    .filter { it.key < tradeDate }
                    .maxByOrNull { it.key }
                    ?.value
                    ?.associateBy { it.symbol }
                    .orEmpty()
                val peAnchors = getOrLoadValuationPeArchive()?.stockAnchors
                val enrichedStocks = enrichStocksWithPeAnchors(
                    payload.stocks.map { stock ->
                        if (stock.prior52WHigh != null) {
                            stock
                        } else {
                            val tvPrev = tvPriorDayMap[stock.symbol]
                            val cachedPrev = if (tradeDate > priorTradeDate) priorStocksMap[stock.symbol] else null
                            val prevHigh = tvPrev?.high52W ?: cachedPrev?.high52W
                            if (prevHigh != null && prevHigh > 0.0) {
                                stock.copy(prior52WHigh = prevHigh)
                            } else stock
                        }
                    },
                    peAnchors
                )
                val eligibleLiveStocks = TankCalculator
                    .filterEligibleConstituents(enrichedStocks)
                    .sortedByDescending { it.score }

                val currentBaseHistories = _universeHistories.value.ifEmpty {
                    mapOf(TankIndexUniverse.NIFTY_500 to _history.value)
                }
                val existingN500Dates = currentBaseHistories[TankIndexUniverse.NIFTY_500]
                    ?.mapTo(HashSet()) { it.date }
                    .orEmpty()

                // Identify missing trading sessions between seedLatestDate (or last 30 days) and tradeDate
                val recentTradingDates = computeTradingDatesInGap(afterIso = seedLatestDate, beforeIso = tradeDate)
                val missingGapDates = recentTradingDates.filterTo(LinkedHashSet()) { dt ->
                    dt !in existingN500Dates && dt !in payload.historicalStocksByDate
                }
                val adOnlyDates = payload.historicalStocksByDate.entries
                    .filter { (dt, list) ->
                        val hasValidAd = list.count { kotlin.math.abs(it.dailyChangePct) > 1e-6 } >= (list.size / 4)
                        !hasValidAd && _macroOverlays.value[dt]?.adPctByUniverse.isNullOrEmpty()
                    }
                    .mapTo(LinkedHashSet()) { it.key }

                val datesToQueryYahoo = (missingGapDates + adOnlyDates).toSet()
                val yahooGapStocks = if (datesToQueryYahoo.isNotEmpty() && eligibleLiveStocks.size >= 450) {
                    runCatching {
                        remoteDataSource.fetchGapDayStocks(
                            missingDates = datesToQueryYahoo,
                            referenceStocks = eligibleLiveStocks,
                            bseSymbols = payload.bseSymbols
                        )
                    }.getOrDefault(emptyMap())
                } else {
                    emptyMap()
                }

                // Assemble chronological snapshots to merge
                val sessionsToMerge = java.util.TreeMap<String, List<StockTank>>()
                for (dt in missingGapDates) {
                    yahooGapStocks[dt]?.let { list ->
                        if (list.size >= 450) sessionsToMerge[dt] = enrichStocksWithPeAnchors(list, peAnchors)
                    }
                }
                for ((dt, tvStocks) in payload.historicalStocksByDate) {
                    if (dt < tradeDate && dt > seedLatestDate) {
                        val yMap = yahooGapStocks[dt]?.associateBy { it.symbol }
                        val enrichedHist = if (yMap != null) {
                            tvStocks.map { st ->
                                if (kotlin.math.abs(st.dailyChangePct) < 1e-6) {
                                    val yChg = yMap[st.symbol]?.dailyChangePct
                                    if (yChg != null) st.copy(dailyChangePct = yChg) else st
                                } else st
                            }
                        } else tvStocks
                        sessionsToMerge[dt] = enrichStocksWithPeAnchors(enrichedHist, peAnchors)
                    }
                }
                if (eligibleLiveStocks.size >= 450) {
                    sessionsToMerge[tradeDate] = eligibleLiveStocks
                }

                var updatedAllHistories = currentBaseHistories
                val computedAdMap = LinkedHashMap<String, Map<TankIndexUniverse, Double>>()
                for ((dt, dayStocks) in sessionsToMerge) {
                    updatedAllHistories = mergeAllUniverses(
                        baseHistories = updatedAllHistories,
                        stocks = dayStocks,
                        constituents = _universeConstituents.value,
                        tradeDate = dt
                    )
                    val adForDay = computePerUniverseAdPct(dayStocks, _universeConstituents.value)
                    if (adForDay.isNotEmpty()) {
                        computedAdMap[dt] = adForDay
                    }
                }
                val updatedHistory = updatedAllHistories[TankIndexUniverse.NIFTY_500] ?: _history.value

                // Merge live macro snapshot (VIX + MMI + Constituent A/D + NSE fallback)
                val combinedAdMap = LinkedHashMap<String, Map<TankIndexUniverse, Double>>()
                fetchedMacro?.adPctByDateAndUniverse?.forEach { (dt, uMap) ->
                    if (uMap.isNotEmpty()) combinedAdMap[dt] = uMap
                }
                computedAdMap.forEach { (dt, uMap) ->
                    if (uMap.isNotEmpty()) combinedAdMap[dt] = uMap
                }
                val mergedMacroSnap = RemoteMacroSnapshot(
                    vixByDate = fetchedMacro?.vixByDate.orEmpty(),
                    mmiByDate = fetchedMacro?.mmiByDate.orEmpty(),
                    adPctByDateAndUniverse = combinedAdMap
                )
                val updatedMacroOverlays = applyMacroSnapshot(_macroOverlays.value, mergedMacroSnap)
                _macroOverlays.value = updatedMacroOverlays
                saveLiveMacroVault(updatedMacroOverlays)
                saveLiveHistoryVault(updatedAllHistories)

                val excludedCount = (payload.stocks.size - eligibleLiveStocks.size).coerceAtLeast(0) +
                    payload.integrity.excludedInsufficientHistoryCount

                val liveIntegrity = DatasetIntegrityMetadata(
                    dataQuality = if (eligibleLiveStocks.size >= 450) DataQuality.VERIFIED_EOD else DataQuality.ILLUSTRATIVE_FALLBACK,
                    constituentCoverage = roundFourDecimals(
                        eligibleLiveStocks.size.toDouble() / DatasetIntegrityMetadata.INDEX_TARGET_CONSTITUENTS
                    ).coerceIn(0.0, 1.0),
                    isIllustrative = eligibleLiveStocks.size < 450,
                    lastVerifiedTradingDate = tradeDate,
                    excludedInsufficientHistoryCount = excludedCount
                )

                publishState(
                    stocks = eligibleLiveStocks,
                    history = updatedHistory,
                    allUniverseHistories = updatedAllHistories,
                    liveIntegrity = liveIntegrity,
                    historyIntegrity = _historyIntegrity.value,
                    sessionDate = tradeDate
                )

                saveLiveCache(
                    LiveCacheSnapshot(
                        tradeDate = tradeDate,
                        syncedDisplay = syncedDisplay,
                        sourceName = payload.sourceName,
                        stocks = eligibleLiveStocks
                    )
                )

                _pipelineStatus.value = _pipelineStatus.value.copy(
                    isSyncing = false,
                    lastSyncedDisplay = syncedDisplay,
                    lastTradeDate = tradeDate,
                    sourceDisplay = payload.sourceName,
                    trackedStocksCount = eligibleLiveStocks.size,
                    isLiveHydrated = true,
                    isLiveMarketActive = isMarketOpenNow,
                    errorMessage = null,
                    integrity = liveIntegrity,
                    historyIntegrity = _historyIntegrity.value
                )
                Result.success(Unit)
            },
            onFailure = { error ->
                if (fetchedMacro != null) {
                    val updatedMacroOverlays = applyMacroSnapshot(_macroOverlays.value, fetchedMacro)
                    _macroOverlays.value = updatedMacroOverlays
                    saveLiveMacroVault(updatedMacroOverlays)
                }
                val latestDate = _history.value.lastOrNull()?.date ?: "2026-11-13"
                val currentCount = _stocks.value.size
                _pipelineStatus.value = _pipelineStatus.value.copy(
                    isSyncing = false,
                    lastSyncedDisplay = if (_pipelineStatus.value.isLiveHydrated) {
                        _pipelineStatus.value.lastSyncedDisplay
                    } else {
                        "Auto-Synced: EOD Vault (${NseMarketCalendar.formatDdMmmYyyy(latestDate)})"
                    },
                    trackedStocksCount = currentCount,
                    errorMessage = "Offline / market feed unreachable — displaying verified EOD vault."
                )
                Result.failure(error)
            }
        )
    }

    private fun publishState(
        stocks: List<StockTank>,
        history: List<TankHistoryPoint>,
        allUniverseHistories: Map<TankIndexUniverse, List<TankHistoryPoint>>,
        liveIntegrity: DatasetIntegrityMetadata,
        historyIntegrity: DatasetIntegrityMetadata,
        sessionDate: String
    ) {
        _stocks.value = stocks
        _history.value = history
        _universeHistories.value = allUniverseHistories
        _datasetIntegrity.value = liveIntegrity
        _historyIntegrity.value = historyIntegrity
        val causal = CycleTurningPointEngine.computeCausalSignalAt(
            historyPrefix = history,
            liveStocks = stocks
        )
        _liveSignal.value = causal
        _fourPillarSnapshot.value = causal?.fourPillars
            ?: TankCalculator.computeFourPillarSnapshot(stocks, history, sessionDate)
        val cycleConfig = if (history.firstOrNull()?.date?.let { it < PRE2016_BOUNDARY_DATE } == true) {
            CycleDetectionConfig(startIsoDate = FULL_INCEPTION_CYCLE_START)
        } else {
            CycleDetectionConfig()
        }
        _detectedCycles.value = CycleTurningPointEngine.detectCyclesRetrospective(history, cycleConfig)
    }

    private fun mergeAllUniverses(
        baseHistories: Map<TankIndexUniverse, List<TankHistoryPoint>>,
        stocks: List<StockTank>,
        constituents: Map<TankIndexUniverse, Set<String>>,
        tradeDate: String
    ): Map<TankIndexUniverse, List<TankHistoryPoint>> {
        val out = LinkedHashMap<TankIndexUniverse, List<TankHistoryPoint>>(TankIndexUniverse.entries.size)
        for (universe in TankIndexUniverse.entries) {
            val base = baseHistories[universe] ?: continue
            val slice = if (universe == TankIndexUniverse.NIFTY_500) {
                stocks
            } else {
                val allowed = constituents[universe].orEmpty()
                if (allowed.isNotEmpty()) stocks.filter { it.symbol in allowed } else emptyList()
            }
            out[universe] = mergeTodayIntoHistory(
                currentHistory = base,
                stocks = slice,
                tradeDate = tradeDate,
                targetConstituents = universe.targetCount
            )
        }
        return out
    }

    internal fun mergeTodayIntoHistory(
        currentHistory: List<TankHistoryPoint>,
        stocks: List<StockTank>,
        tradeDate: String,
        targetConstituents: Int = DatasetIntegrityMetadata.INDEX_TARGET_CONSTITUENTS
    ): List<TankHistoryPoint> {
        val eligible = TankCalculator.filterEligibleConstituents(stocks)
        val minRequired = max(1, (targetConstituents * 0.90).toInt())
        if (eligible.size < minRequired) return currentHistory
        if (!NseMarketCalendar.isValidTradingIsoDate(tradeDate)) return currentHistory

        val level = TankCalculator.computeMainTankLevel(eligible)
        val flowing = eligible.count { it.isFlowing }
        val highPressure = eligible.count { it.isHighPressure }
        val avgScore = eligible.map { it.score }.average()

        val eupPct = TankCalculator.computeEuphoriaPressurePct(eligible)
        val hasPriorHighs = eligible.any { it.prior52WHigh != null }
        val bpCount = if (hasPriorHighs) eligible.count { it.isMakingFresh52WHigh } else 0
        val bpPct = if (hasPriorHighs) TankCalculator.computeBreakoutPulsePct(eligible) else 0.0
        val capCount = eligible.count { it.isCapitulation }
        val capPct = TankCalculator.computeCapitulationDepthPct(eligible)

        val mutable = currentHistory.toMutableList()
        val existingIdx = mutable.indexOfLast { it.date == tradeDate }
        val insertIdx = if (existingIdx >= 0) {
            existingIdx
        } else {
            val afterPos = mutable.indexOfFirst { it.date > tradeDate }
            if (afterPos >= 0) afterPos else mutable.size
        }
        val prevIdx = insertIdx - 1
        val prevLevel = if (prevIdx >= 0) mutable[prevIdx].tankLevel else level
        val roundedLevel = roundTwoDecimals(level)
        val roundedDelta = roundTwoDecimals(roundedLevel - prevLevel)

        val prevPlateau = if (prevIdx >= 0) mutable[prevIdx].plateauDays else 0
        val plateauDays = if (roundedLevel > TankCalculator.OVERHEATED_TANK_THRESHOLD_PCT) prevPlateau + 1 else 0

        val lookbackIdx = insertIdx - TankCalculator.BREAKOUT_LOOKBACK_DAYS
        val deltaB20 = if (lookbackIdx >= 0 && lookbackIdx < mutable.size) {
            roundTwoDecimals(bpPct - mutable[lookbackIdx].breakoutPulsePct)
        } else {
            0.0
        }

        // Preserve existing historical breakout metrics if live prior highs weren't present for the same date
        val existingPoint = if (existingIdx >= 0) mutable[existingIdx] else null
        val finalBpCount = if (hasPriorHighs) bpCount else (existingPoint?.breakoutCount ?: bpCount)
        val finalBpPct = if (hasPriorHighs) bpPct else (existingPoint?.breakoutPulsePct ?: bpPct)
        val finalDeltaB20 = if (hasPriorHighs) deltaB20 else (existingPoint?.breakoutPulseDelta20 ?: deltaB20)

        val peEligible = TankCalculator.filterEligiblePeConstituents(eligible)
        val recentPeCounts = mutable.takeLast(20)
            .map { it.peActiveCount }
            .filter { it > 0 }
            .sorted()
        val rollingPeBaseline = if (recentPeCounts.isNotEmpty()) recentPeCounts[recentPeCounts.size / 2] else 0
        val minReliablePeCount = kotlin.math.max(
            (rollingPeBaseline * 0.75).toInt(),
            (eligible.size * 0.55).toInt()
        ).coerceAtLeast(10)
        val existingPeCount = existingPoint?.peActiveCount ?: 0
        val isLivePeComplete = peEligible.isNotEmpty() &&
            peEligible.size >= minReliablePeCount &&
            peEligible.size >= existingPeCount
        val livePeTankLevel = if (isLivePeComplete) TankCalculator.computePeTankLevel(eligible, 50.0) else null
        val liveMedianPe = if (isLivePeComplete) TankCalculator.computeMedianPe(eligible) else null
        val livePeBins = if (isLivePeComplete) TankCalculator.buildPeScoreBins(eligible) else emptyList()

        val prevOfficial = if (insertIdx > 0) mutable[insertIdx - 1] else null
        val point = TankHistoryPoint(
            date = tradeDate,
            tankLevel = roundedLevel,
            dailyDelta = roundedDelta,
            flowingCount = flowing,
            activeCount = eligible.size,
            highPressureCount = highPressure,
            avgScore = roundTwoDecimals(avgScore),
            euphoriaPressurePct = eupPct,
            breakoutCount = finalBpCount,
            breakoutPulsePct = finalBpPct,
            breakoutPulseDelta20 = finalDeltaB20,
            capitulationCount = capCount,
            capitulationDepthPct = capPct,
            plateauDays = plateauDays,
            scoreBins = TankCalculator.buildScoreBins(eligible),
            peTankLevel = livePeTankLevel ?: existingPoint?.peTankLevel,
            medianPe = liveMedianPe ?: existingPoint?.medianPe,
            peActiveCount = if (isLivePeComplete) peEligible.size else existingPeCount,
            peScoreBins = if (livePeBins.size == TankCalculator.SCORE_HISTOGRAM_BIN_COUNT) livePeBins else (existingPoint?.peScoreBins ?: emptyList()),
            indexPe = existingPoint?.indexPe ?: prevOfficial?.indexPe,
            indexPb = existingPoint?.indexPb ?: prevOfficial?.indexPb,
            indexDivYield = existingPoint?.indexDivYield ?: prevOfficial?.indexDivYield
        )

        if (existingIdx >= 0) {
            mutable[existingIdx] = point
        } else {
            mutable.add(insertIdx, point)
        }

        // Reconcile dailyDelta, plateauDays, and breakoutPulseDelta20 for points following an inserted/updated point
        for (i in (insertIdx + 1) until mutable.size) {
            val prevPt = mutable[i - 1]
            val cur = mutable[i]
            val newDelta = roundTwoDecimals(cur.tankLevel - prevPt.tankLevel)
            val newPlateau = if (cur.tankLevel > TankCalculator.OVERHEATED_TANK_THRESHOLD_PCT) {
                prevPt.plateauDays + 1
            } else {
                0
            }
            val lb = i - TankCalculator.BREAKOUT_LOOKBACK_DAYS
            val newDeltaB20 = if (lb >= 0) {
                roundTwoDecimals(cur.breakoutPulsePct - mutable[lb].breakoutPulsePct)
            } else {
                cur.breakoutPulseDelta20
            }
            if (cur.dailyDelta != newDelta || cur.plateauDays != newPlateau || cur.breakoutPulseDelta20 != newDeltaB20) {
                mutable[i] = cur.copy(
                    dailyDelta = newDelta,
                    plateauDays = newPlateau,
                    breakoutPulseDelta20 = newDeltaB20
                )
            }
        }
        return mutable
    }

    private fun computeTradingDatesInGap(afterIso: String, beforeIso: String): List<String> {
        return try {
            val end = LocalDate.parse(beforeIso)
            val floor = LocalDate.parse(afterIso).plusDays(1)
            val earliest = end.minusDays(30)
            var d = if (floor.isAfter(earliest)) floor else earliest
            val dates = ArrayList<String>()
            while (d.isBefore(end)) {
                if (NseMarketCalendar.isTradingDay(d)) {
                    dates.add(d.toString())
                }
                d = d.plusDays(1)
            }
            dates
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun computePerUniverseAdPct(
        stocks: List<StockTank>,
        constituents: Map<TankIndexUniverse, Set<String>>
    ): Map<TankIndexUniverse, Double> {
        if (stocks.isEmpty()) return emptyMap()
        val nonZeroCount = stocks.count { abs(it.dailyChangePct) > 1e-6 }
        if (nonZeroCount < (stocks.size * 0.25).toInt()) return emptyMap()

        val out = LinkedHashMap<TankIndexUniverse, Double>(TankIndexUniverse.entries.size)
        for (u in TankIndexUniverse.entries) {
            val slice = if (u == TankIndexUniverse.NIFTY_500) {
                stocks
            } else {
                val allowed = constituents[u].orEmpty()
                if (allowed.isNotEmpty()) stocks.filter { it.symbol in allowed } else emptyList()
            }
            val adv = slice.count { it.dailyChangePct > 0.0 }
            val dec = slice.count { it.dailyChangePct < 0.0 }
            val total = adv + dec
            if (total > 0) {
                out[u] = roundTwoDecimals((adv.toDouble() / total.toDouble()) * 100.0)
            }
        }
        return out
    }

    private fun mergeMacroOverlayMaps(
        base: Map<String, MacroOverlayPoint>,
        overrides: Map<String, MacroOverlayPoint>
    ): Map<String, MacroOverlayPoint> {
        if (overrides.isEmpty()) return base
        val vix = LinkedHashMap<String, Double>()
        val mmi = LinkedHashMap<String, Double>()
        val ad = LinkedHashMap<String, Map<TankIndexUniverse, Double>>()
        for ((dt, pt) in overrides) {
            val v = pt.indiaVix
            if (v != null && v > 0.0 && dt >= DeadlyComboEngine.INDIA_VIX_INCEPTION_DATE) vix[dt] = v
            val m = pt.mmiValue
            if (m != null && m in 0.0..100.0 && dt >= DeadlyComboEngine.MMI_VERIFIED_START_DATE) mmi[dt] = m
            if (pt.adPctByUniverse.isNotEmpty()) ad[dt] = pt.adPctByUniverse
        }
        return applyMacroSnapshot(base, RemoteMacroSnapshot(vix, mmi, ad))
    }

    private fun applyMacroSnapshot(
        existing: Map<String, MacroOverlayPoint>,
        snapshot: RemoteMacroSnapshot
    ): Map<String, MacroOverlayPoint> {
        if (snapshot.vixByDate.isEmpty() &&
            snapshot.mmiByDate.isEmpty() &&
            snapshot.adPctByDateAndUniverse.isEmpty()
        ) {
            return existing
        }
        val allDates = LinkedHashSet<String>(existing.size + snapshot.vixByDate.size + 16)
        allDates.addAll(existing.keys)
        allDates.addAll(snapshot.vixByDate.keys)
        allDates.addAll(snapshot.mmiByDate.keys)
        allDates.addAll(snapshot.adPctByDateAndUniverse.keys)

        val sorted = allDates.sorted()
        val result = LinkedHashMap<String, MacroOverlayPoint>(sorted.size)
        var runModernVix: Double? = null
        var runModernMmi: Double? = null
        for (dt in sorted) {
            val prev = existing[dt]
            val existingAd = prev?.adPctByUniverse.orEmpty()
            val newAd = snapshot.adPctByDateAndUniverse[dt].orEmpty()
            val mergedAd = when {
                existingAd.isEmpty() -> newAd
                newAd.isEmpty() -> existingAd
                else -> existingAd + newAd
            }

            if (dt < PRE2016_BOUNDARY_DATE) {
                val preVix = if (dt >= DeadlyComboEngine.INDIA_VIX_INCEPTION_DATE) {
                    snapshot.vixByDate[dt]?.takeIf { it > 0.0 }
                        ?: prev?.indiaVix?.takeIf { it > 0.0 }
                } else {
                    null
                }
                result[dt] = MacroOverlayPoint(
                    date = dt,
                    indiaVix = preVix,
                    mmiValue = null,
                    adPctByUniverse = mergedAd
                )
            } else {
                val nextVix = snapshot.vixByDate[dt]?.takeIf { it > 0.0 }
                    ?: prev?.indiaVix?.takeIf { it > 0.0 }
                    ?: runModernVix
                if (nextVix != null) runModernVix = nextVix

                val nextMmi = snapshot.mmiByDate[dt]?.takeIf { it in 0.0..100.0 }
                    ?: prev?.mmiValue?.takeIf { it in 0.0..100.0 }
                    ?: runModernMmi
                if (nextMmi != null) runModernMmi = nextMmi

                result[dt] = MacroOverlayPoint(
                    date = dt,
                    indiaVix = nextVix,
                    mmiValue = nextMmi,
                    adPctByUniverse = mergedAd
                )
            }
        }
        return result
    }

    private fun mergeExtraPointsIntoUniverses(
        baseHistories: Map<TankIndexUniverse, List<TankHistoryPoint>>,
        extraByUniverse: Map<TankIndexUniverse, List<TankHistoryPoint>>
    ): Map<TankIndexUniverse, List<TankHistoryPoint>> {
        if (extraByUniverse.isEmpty()) return baseHistories
        val out = LinkedHashMap<TankIndexUniverse, List<TankHistoryPoint>>(baseHistories.size)
        for ((u, base) in baseHistories) {
            val extras = extraByUniverse[u].orEmpty()
            if (extras.isEmpty()) {
                out[u] = base
                continue
            }
            val byDate = java.util.TreeMap<String, TankHistoryPoint>()
            for (pt in base) byDate[pt.date] = pt
            for (pt in extras) {
                if (pt.date > seedLatestDate && NseMarketCalendar.isValidTradingIsoDate(pt.date)) {
                    byDate[pt.date] = pt
                }
            }
            val combined = byDate.values.toMutableList()
            for (i in 1 until combined.size) {
                if (combined[i].date > seedLatestDate) {
                    val prev = combined[i - 1]
                    val cur = combined[i]
                    val newDelta = roundTwoDecimals(cur.tankLevel - prev.tankLevel)
                    val newPlateau = if (cur.tankLevel > TankCalculator.OVERHEATED_TANK_THRESHOLD_PCT) {
                        prev.plateauDays + 1
                    } else 0
                    val lb = i - TankCalculator.BREAKOUT_LOOKBACK_DAYS
                    val newB20 = if (lb >= 0) {
                        roundTwoDecimals(cur.breakoutPulsePct - combined[lb].breakoutPulsePct)
                    } else cur.breakoutPulseDelta20
                    combined[i] = cur.copy(
                        dailyDelta = newDelta,
                        plateauDays = newPlateau,
                        breakoutPulseDelta20 = newB20
                    )
                }
            }
            out[u] = combined
        }
        return out
    }

    fun validateIntegrityMetadata(): Boolean {
        return _datasetIntegrity.value.isSchemaValid() && _historyIntegrity.value.isSchemaValid()
    }

    private fun roundTwoDecimals(value: Double): Double = round(value * 100.0) / 100.0
    private fun roundFourDecimals(value: Double): Double = round(value * 10000.0) / 10000.0

    private fun loadUniverseConstituents(): Map<TankIndexUniverse, Set<String>> {
        return try {
            val raw = context.assets.open(INDEX_CONSTITUENTS_ASSET).bufferedReader().use { it.readText() }
            val json = JSONObject(raw)
            val result = LinkedHashMap<TankIndexUniverse, Set<String>>(TankIndexUniverse.entries.size)
            for (universe in TankIndexUniverse.entries) {
                val arr = json.optJSONArray(universe.constituentKey) ?: continue
                val set = LinkedHashSet<String>(arr.length())
                for (i in 0 until arr.length()) {
                    val sym = arr.optString(i, "").trim().uppercase(Locale.US)
                    if (sym.isNotEmpty()) set.add(sym)
                }
                result[universe] = set
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading $INDEX_CONSTITUENTS_ASSET", e)
            emptyMap()
        }
    }

    private fun loadSubIndexHistories(
        fallbackHistory: List<TankHistoryPoint>
    ): Map<TankIndexUniverse, List<TankHistoryPoint>> {
        return try {
            val raw = context.assets.open(SUBINDEX_HISTORY_ASSET).bufferedReader().use { it.readText() }
            val root = JSONObject(raw)
            val subObj = root.optJSONObject("subIndices") ?: return emptyMap()
            val parser = (remoteDataSource as? LiveMarketTankDataSource) ?: LiveMarketTankDataSource(context)
            val result = LinkedHashMap<TankIndexUniverse, List<TankHistoryPoint>>()
            for (universe in TankIndexUniverse.entries) {
                if (universe == TankIndexUniverse.NIFTY_500) continue
                val node = subObj.optJSONObject(universe.subIndexHistoryKey) ?: continue
                val arr = node.optJSONArray("history") ?: continue
                val pts = parser.parseHistoryArray(arr)
                if (pts.isNotEmpty()) {
                    result[universe] = pts
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading $SUBINDEX_HISTORY_ASSET, using scaled fallback", e)
            emptyMap()
        }
    }

    private fun loadMacroOverlays(): Map<String, MacroOverlayPoint> {
        val vixByDate = HashMap<String, Double>(2800)
        val mmiByDate = HashMap<String, Double>(2800)
        val adByDate = HashMap<String, Map<TankIndexUniverse, Double>>(2800)
        val allDates = LinkedHashSet<String>(2800)

        try {
            val rawVix = context.assets.open(INDIA_VIX_ASSET).bufferedReader().use { it.readText() }
            val vixJson = JSONObject(rawVix)
            val keys = vixJson.keys()
            while (keys.hasNext()) {
                val dt = keys.next()
                val v = vixJson.optDouble(dt, Double.NaN)
                if (!v.isNaN() && v > 0.0) {
                    vixByDate[dt] = v
                    allDates.add(dt)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading $INDIA_VIX_ASSET", e)
        }

        try {
            val rawMmi = context.assets.open(MMI_HISTORY_ASSET).bufferedReader().use { it.readText() }
            val mmiArr = JSONArray(rawMmi)
            for (i in 0 until mmiArr.length()) {
                val obj = mmiArr.optJSONObject(i) ?: continue
                val srcDate = obj.optString("source_date", "").take(10)
                val v = obj.optDouble("mmi_value", Double.NaN)
                if (srcDate.length == 10 && !v.isNaN() && v >= 0.0) {
                    mmiByDate[srcDate] = v
                    allDates.add(srcDate)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading $MMI_HISTORY_ASSET", e)
        }

        try {
            val rawAd = context.assets.open(ADVANCE_DECLINE_ASSET).bufferedReader().use { it.readText() }
            val adJson = JSONObject(rawAd)
            val keys = adJson.keys()
            while (keys.hasNext()) {
                val dt = keys.next()
                val dayObj = adJson.optJSONObject(dt) ?: continue
                val uMap = LinkedHashMap<TankIndexUniverse, Double>(TankIndexUniverse.entries.size)
                for (u in TankIndexUniverse.entries) {
                    val uObj = dayObj.optJSONObject(u.constituentKey) ?: continue
                    val pct = uObj.optDouble("pct", Double.NaN)
                    if (!pct.isNaN()) {
                        uMap[u] = pct
                    }
                }
                if (uMap.isNotEmpty()) {
                    adByDate[dt] = uMap
                    allDates.add(dt)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading $ADVANCE_DECLINE_ASSET", e)
        }

        val sortedDates = allDates.sorted()
        val result = LinkedHashMap<String, MacroOverlayPoint>(sortedDates.size)
        var lastVix: Double? = null
        var lastMmi: Double? = null
        for (dt in sortedDates) {
            vixByDate[dt]?.let { lastVix = it }
            mmiByDate[dt]?.let { lastMmi = it }
            val safeVix = if (dt >= DeadlyComboEngine.INDIA_VIX_INCEPTION_DATE) lastVix else null
            val safeMmi = if (dt >= DeadlyComboEngine.MMI_VERIFIED_START_DATE) lastMmi else null
            result[dt] = MacroOverlayPoint(
                date = dt,
                indiaVix = safeVix,
                mmiValue = safeMmi,
                adPctByUniverse = adByDate[dt].orEmpty()
            )
        }
        return result
    }

    private fun loadSeedHistoryPayload(): RemoteHistoryPayload {
        return try {
            val raw = context.assets.open(SEED_HISTORY_ASSET).bufferedReader().use { it.readText() }
            val parser = (remoteDataSource as? LiveMarketTankDataSource) ?: LiveMarketTankDataSource(context)
            parser.parseStructuredHistoryJson(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading $SEED_HISTORY_ASSET", e)
            RemoteHistoryPayload(emptyList(), DatasetIntegrityMetadata())
        }
    }

    private fun loadSeedStocks(): List<StockTank> {
        return try {
            val raw = context.assets.open(SEED_STOCKS_ASSET).bufferedReader().use { it.readText() }
            parseStockArray(JSONArray(raw))
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading $SEED_STOCKS_ASSET", e)
            emptyList()
        }
    }

    private fun parseStockArray(arr: JSONArray): List<StockTank> {
        val list = ArrayList<StockTank>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val symbol = obj.optString("symbol", "").trim()
            if (symbol.isEmpty()) continue
            val price = obj.optDouble("price", 0.0)
            val todayHigh = obj.optDouble("todayHigh", price)
            val priorHigh = if (obj.has("prior52WHigh") && !obj.isNull("prior52WHigh")) {
                obj.optDouble("prior52WHigh", Double.NaN).takeIf { !it.isNaN() && it > 0.0 }
            } else {
                null
            }
            val peTtm = if (obj.has("peTtm") && !obj.isNull("peTtm")) {
                obj.optDouble("peTtm", Double.NaN).takeIf { it.isFinite() && it > 0.0 && it < 5000.0 }
            } else null
            val peLow52W = if (obj.has("peLow52W") && !obj.isNull("peLow52W")) {
                obj.optDouble("peLow52W", Double.NaN).takeIf { it.isFinite() && it > 0.0 }
            } else null
            val peHigh52W = if (obj.has("peHigh52W") && !obj.isNull("peHigh52W")) {
                obj.optDouble("peHigh52W", Double.NaN).takeIf { it.isFinite() && it > 0.0 }
            } else null
            val epsTtm = if (obj.has("epsTtm") && !obj.isNull("epsTtm")) {
                obj.optDouble("epsTtm", Double.NaN).takeIf { it.isFinite() }
            } else null
            list.add(
                StockTank(
                    symbol = symbol,
                    name = obj.optString("name", symbol),
                    sector = obj.optString("sector", "Consumer & Tech"),
                    currentPrice = price,
                    low52W = obj.optDouble("low52W", 0.0),
                    high52W = obj.optDouble("high52W", 1.0),
                    dailyChangePct = obj.optDouble("chg", 0.0),
                    todayHigh = if (todayHigh.isNaN() || todayHigh <= 0.0) price else todayHigh,
                    prior52WHigh = priorHigh,
                    rollingHistoryDays = obj.optInt("rollingHistoryDays", TankCalculator.FULL_52W_TRADING_DAYS),
                    peTtm = peTtm,
                    peLow52W = peLow52W,
                    peHigh52W = peHigh52W,
                    epsTtm = epsTtm
                )
            )
        }
        return list.sortedByDescending { it.score }
    }

    private fun getOrLoadValuationPeArchive(): ValuationPeArchive? {
        valuationPeArchive?.let { return it }
        val loaded = loadValuationPeArchive()
        if (loaded != null) {
            valuationPeArchive = loaded
        }
        return loaded
    }

    private fun readPossiblyGzippedAssetText(assetName: String): String {
        val assetStream = try {
            context.assets.open(assetName)
        } catch (notFound: FileNotFoundException) {
            if (assetName.endsWith(".gz", ignoreCase = true)) {
                context.assets.open(assetName.removeSuffix(".gz"))
            } else {
                throw notFound
            }
        }
        return assetStream.use { rawInput ->
            val buffered = BufferedInputStream(rawInput, 16_384)
            buffered.mark(2)
            val b0 = buffered.read()
            val b1 = buffered.read()
            buffered.reset()
            val isGzip = (b0 == 0x1f && b1 == 0x8b)
            val decoded = if (isGzip) GZIPInputStream(buffered) else buffered
            decoded.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }

    private fun loadValuationPeArchive(): ValuationPeArchive? {
        return try {
            val raw = readPossiblyGzippedAssetText(VALUATION_PE_ASSET)
            val root = JSONObject(raw)

            val stockAnchors = HashMap<String, StockPeAnchor>(512)
            val stocksObj = root.optJSONObject("stocks")
            if (stocksObj != null) {
                val keys = stocksObj.keys()
                while (keys.hasNext()) {
                    val sym = keys.next().trim().uppercase(Locale.US)
                    val arr = stocksObj.optJSONArray(sym) ?: continue
                    if (arr.length() < 3) continue
                    val pe = arr.optDouble(0, Double.NaN)
                    val lo = arr.optDouble(1, Double.NaN)
                    val hi = arr.optDouble(2, Double.NaN)
                    val eps = if (arr.length() >= 4 && !arr.isNull(3)) {
                        arr.optDouble(3, Double.NaN).takeIf { it.isFinite() }
                    } else null
                    if (pe.isFinite() && pe > 0.0 && lo.isFinite() && lo > 0.0 && hi.isFinite() && hi > lo) {
                        stockAnchors[sym] = StockPeAnchor(
                            peTtm = pe,
                            peLow52W = lo,
                            peHigh52W = hi,
                            epsTtm = eps
                        )
                    }
                }
            }

            val universeSamples = LinkedHashMap<TankIndexUniverse, List<HistoricalPeSample>>(TankIndexUniverse.entries.size)
            val uniObj = root.optJSONObject("universes")
            if (uniObj != null) {
                for (u in TankIndexUniverse.entries) {
                    val arr = uniObj.optJSONArray(u.subIndexHistoryKey) ?: continue
                    val list = ArrayList<HistoricalPeSample>(arr.length())
                    for (i in 0 until arr.length()) {
                        val row = arr.optJSONArray(i) ?: continue
                        if (row.length() < 5) continue
                        val dt = row.optString(0, "").trim()
                        if (dt.length != 10) continue
                        val epochDay = try {
                            LocalDate.parse(dt).toEpochDay()
                        } catch (_: Exception) {
                            continue
                        }
                        val peTank = row.optDouble(1, Double.NaN)
                        if (!peTank.isFinite() || peTank !in 0.0..100.0) continue
                        val medPe = if (!row.isNull(2)) {
                            row.optDouble(2, Double.NaN).takeIf { it.isFinite() && it > 0.0 }
                        } else null
                        val activeCnt = row.optInt(3, 0).coerceAtLeast(0)
                        val binsArr = row.optJSONArray(4)
                        val bins = if (binsArr != null && binsArr.length() == TankCalculator.SCORE_HISTOGRAM_BIN_COUNT) {
                            List(TankCalculator.SCORE_HISTOGRAM_BIN_COUNT) { b -> binsArr.optInt(b, 0).coerceAtLeast(0) }
                        } else {
                            emptyList()
                        }
                        list.add(
                            HistoricalPeSample(
                                date = dt,
                                epochDay = epochDay,
                                peTankLevel = roundTwoDecimals(peTank),
                                medianPe = medPe?.let { roundTwoDecimals(it) },
                                peActiveCount = activeCnt,
                                peScoreBins = bins
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        list.sortBy { it.epochDay }
                        universeSamples[u] = list
                    }
                }
            }

            val officialPeSamples = LinkedHashMap<TankIndexUniverse, List<OfficialIndexPeSample>>(TankIndexUniverse.entries.size)
            val officialObj = root.optJSONObject("officialIndexPe")
            if (officialObj != null) {
                for (u in TankIndexUniverse.entries) {
                    val arr = officialObj.optJSONArray(u.subIndexHistoryKey) ?: continue
                    val list = ArrayList<OfficialIndexPeSample>(arr.length())
                    for (i in 0 until arr.length()) {
                        val row = arr.optJSONArray(i) ?: continue
                        if (row.length() < 2) continue
                        val dt = row.optString(0, "").trim()
                        if (dt.length != 10) continue
                        val epochDay = try {
                            LocalDate.parse(dt).toEpochDay()
                        } catch (_: Exception) {
                            continue
                        }
                        val pe = row.optDouble(1, Double.NaN)
                        if (!pe.isFinite() || pe <= 0.0) continue
                        val pb = if (row.length() >= 3 && !row.isNull(2)) {
                            row.optDouble(2, Double.NaN).takeIf { it.isFinite() && it > 0.0 }
                        } else null
                        val dy = if (row.length() >= 4 && !row.isNull(3)) {
                            row.optDouble(3, Double.NaN).takeIf { it.isFinite() && it >= 0.0 }
                        } else null
                        list.add(
                            OfficialIndexPeSample(
                                date = dt,
                                epochDay = epochDay,
                                indexPe = roundTwoDecimals(pe),
                                indexPb = pb?.let { roundTwoDecimals(it) },
                                indexDivYield = dy?.let { roundTwoDecimals(it) }
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        list.sortBy { it.epochDay }
                        officialPeSamples[u] = list
                    }
                }
            }

            ValuationPeArchive(
                stockAnchors = stockAnchors,
                universeSamples = universeSamples,
                officialIndexPeSamples = officialPeSamples
            )
        } catch (e: Exception) {
            Log.w(TAG, "Valuation P/E archive unavailable: ${e.message}")
            null
        }
    }

    private fun enrichStocksWithPeAnchors(
        stocks: List<StockTank>,
        anchors: Map<String, StockPeAnchor>?
    ): List<StockTank> {
        if (stocks.isEmpty() || anchors.isNullOrEmpty()) return stocks
        return stocks.map { st ->
            val anchor = anchors[st.symbol.uppercase(Locale.US)] ?: return@map st
            val livePe = st.peTtm?.takeIf { it.isFinite() && it > 0.0 && it < 5000.0 } ?: anchor.peTtm
            val liveEps = st.epsTtm?.takeIf { it.isFinite() } ?: anchor.epsTtm
            val baseLow = st.peLow52W?.takeIf { it.isFinite() && it > 0.0 } ?: anchor.peLow52W
            val baseHigh = st.peHigh52W?.takeIf { it.isFinite() && it > 0.0 } ?: anchor.peHigh52W
            val effLow = kotlin.math.min(baseLow, livePe)
            val effHigh = kotlin.math.max(baseHigh, livePe)
            st.copy(
                peTtm = roundTwoDecimals(livePe),
                peLow52W = roundTwoDecimals(effLow),
                peHigh52W = roundTwoDecimals(effHigh),
                epsTtm = liveEps
            )
        }
    }

    /**
     * Causally joins real Screener/TradingView Valuation P/E snapshots onto daily breadth bars
     * using the most recent complete P/E observation on or before each date within [PE_CAUSAL_MAX_STALE_DAYS].
     * Filters out anomalous low-coverage constituent drops (< 75% of rolling 20-sample median) and
     * upgrades any stale/partial cached vault points when the backfilled archive has higher coverage.
     * Pre-2007 dates without genuine constituent P/E observations stay null (never fabricated).
     */
    private fun attachValuationPeToHistory(
        history: List<TankHistoryPoint>,
        samples: List<HistoricalPeSample>?,
        officialSamples: List<OfficialIndexPeSample>? = null
    ): List<TankHistoryPoint> {
        if (history.isEmpty() || (samples.isNullOrEmpty() && officialSamples.isNullOrEmpty())) return history

        // Data-completeness filter: drop any anomalous partial-coverage sample (< 75% of recent rolling median)
        val reliableSamples = ArrayList<HistoricalPeSample>(samples?.size ?: 0)
        if (!samples.isNullOrEmpty()) {
            val recentCounts = ArrayDeque<Int>(20)
            for (sample in samples) {
                if (sample.peActiveCount <= 0) continue
                val isAnomalousDrop = if (recentCounts.size >= 5) {
                    val sorted = recentCounts.sorted()
                    val rollingMed = sorted[sorted.size / 2]
                    sample.peActiveCount < (rollingMed * 0.75).toInt()
                } else false
                if (!isAnomalousDrop) {
                    reliableSamples.add(sample)
                    if (recentCounts.size >= 20) recentCounts.removeFirst()
                    recentCounts.addLast(sample.peActiveCount)
                }
            }
        }
        val officialList = officialSamples.orEmpty()
        if (reliableSamples.isEmpty() && officialList.isEmpty()) return history

        val firstSampleEpoch = reliableSamples.firstOrNull()?.epochDay ?: Long.MAX_VALUE
        val firstOfficialEpoch = officialList.firstOrNull()?.epochDay ?: Long.MAX_VALUE
        var ptr = 0
        var offPtr = 0
        val nSamples = reliableSamples.size
        val nOfficial = officialList.size
        val out = ArrayList<TankHistoryPoint>(history.size)

        for (pt in history) {
            if (pt.isIncompleteColdStart) {
                out.add(pt)
                continue
            }
            val ptEpoch = try {
                LocalDate.parse(pt.date).toEpochDay()
            } catch (_: Exception) {
                out.add(pt)
                continue
            }

            var updated = pt

            // 1. Causally join Official Point-in-Time NSE Index P/E, P/B, DivYield (1999..present, zero survivorship bias)
            if (nOfficial > 0 && ptEpoch >= firstOfficialEpoch) {
                while (offPtr + 1 < nOfficial && officialList[offPtr + 1].epochDay <= ptEpoch) {
                    offPtr++
                }
                val offMatched = officialList[offPtr]
                val offAge = ptEpoch - offMatched.epochDay
                if (offAge in 0L..PE_CAUSAL_MAX_STALE_DAYS) {
                    updated = updated.copy(
                        indexPe = offMatched.indexPe,
                        indexPb = offMatched.indexPb,
                        indexDivYield = offMatched.indexDivYield
                    )
                }
            }

            // 2. Causally join Stock-Level 52W Normalized Valuation P/E Tank (2007..present)
            if (nSamples > 0 && ptEpoch >= firstSampleEpoch) {
                while (ptr + 1 < nSamples && reliableSamples[ptr + 1].epochDay <= ptEpoch) {
                    ptr++
                }
                val matched = reliableSamples[ptr]
                val ageDays = ptEpoch - matched.epochDay
                val hasExistingComplete = updated.peTankLevel != null &&
                    updated.peScoreBins.size == TankCalculator.SCORE_HISTOGRAM_BIN_COUNT &&
                    updated.peActiveCount >= matched.peActiveCount
                if (!hasExistingComplete && ageDays in 0L..PE_CAUSAL_MAX_STALE_DAYS) {
                    updated = updated.copy(
                        peTankLevel = matched.peTankLevel,
                        medianPe = matched.medianPe,
                        peActiveCount = matched.peActiveCount,
                        peScoreBins = matched.peScoreBins
                    )
                }
            }

            out.add(updated)
        }
        return out
    }

    private data class LiveCacheSnapshot(
        val tradeDate: String,
        val syncedDisplay: String,
        val sourceName: String,
        val stocks: List<StockTank>
    )

    private fun deleteLegacyCacheIfPresent() {
        try {
            val legacy = File(context.filesDir, LEGACY_LIVE_CACHE_FILE)
            if (legacy.exists()) legacy.delete()
            val legacyV2 = File(context.filesDir, LEGACY_LIVE_CACHE_V2_FILE)
            if (legacyV2.exists()) legacyV2.delete()
        } catch (_: Exception) {
        }
    }

    private fun loadLiveCache(): LiveCacheSnapshot? {
        return try {
            val file = File(context.filesDir, LIVE_CACHE_FILE)
            if (!file.exists()) return null
            val root = JSONObject(file.readText())
            val stocksArr = root.optJSONArray("stocks") ?: return null
            LiveCacheSnapshot(
                tradeDate = root.optString("tradeDate", "2026-11-13"),
                syncedDisplay = root.optString("syncedDisplay", "Auto-Synced: Cached EOD"),
                sourceName = root.optString("sourceName", "Cached NSE Feed"),
                stocks = parseStockArray(stocksArr)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading live cache", e)
            null
        }
    }

    private fun saveLiveCache(snapshot: LiveCacheSnapshot) {
        try {
            val arr = JSONArray()
            for (s in snapshot.stocks) {
                arr.put(
                    JSONObject().apply {
                        put("symbol", s.symbol)
                        put("name", s.name)
                        put("sector", s.sector)
                        put("price", s.currentPrice)
                        put("low52W", s.low52W)
                        put("high52W", s.high52W)
                        put("chg", s.dailyChangePct)
                        put("todayHigh", s.todayHigh)
                        if (s.prior52WHigh != null) {
                            put("prior52WHigh", s.prior52WHigh)
                        }
                        put("rollingHistoryDays", s.rollingHistoryDays)
                        s.peTtm?.takeIf { it.isFinite() && it > 0.0 }?.let { put("peTtm", it) }
                        s.peLow52W?.takeIf { it.isFinite() && it > 0.0 }?.let { put("peLow52W", it) }
                        s.peHigh52W?.takeIf { it.isFinite() && it > 0.0 }?.let { put("peHigh52W", it) }
                        s.epsTtm?.takeIf { it.isFinite() }?.let { put("epsTtm", it) }
                    }
                )
            }
            val root = JSONObject().apply {
                put("tradeDate", snapshot.tradeDate)
                put("syncedDisplay", snapshot.syncedDisplay)
                put("sourceName", snapshot.sourceName)
                put("modelVersion", DatasetIntegrityMetadata.MODEL_VERSION)
                put("calculationVersion", DatasetIntegrityMetadata.CALCULATION_VERSION)
                put("stocks", arr)
            }
            File(context.filesDir, LIVE_CACHE_FILE).writeText(root.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving live cache", e)
        }
    }

    private fun loadLiveHistoryVault(): Map<TankIndexUniverse, List<TankHistoryPoint>> {
        return try {
            val file = File(context.filesDir, LIVE_HISTORY_VAULT_FILE)
            if (!file.exists()) return emptyMap()
            val root = JSONObject(file.readText())
            val parser = (remoteDataSource as? LiveMarketTankDataSource) ?: LiveMarketTankDataSource(context)
            val out = LinkedHashMap<TankIndexUniverse, List<TankHistoryPoint>>()
            for (u in TankIndexUniverse.entries) {
                val arr = root.optJSONArray(u.subIndexHistoryKey) ?: continue
                val pts = parser.parseHistoryArray(arr)
                if (pts.isNotEmpty()) out[u] = pts
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading live history vault", e)
            emptyMap()
        }
    }

    private fun saveLiveHistoryVault(allHistories: Map<TankIndexUniverse, List<TankHistoryPoint>>) {
        try {
            val root = JSONObject()
            for ((u, list) in allHistories) {
                val postSeed = list.filter { it.date > seedLatestDate }
                if (postSeed.isEmpty()) continue
                val arr = JSONArray()
                for (pt in postSeed) {
                    val binsArr = JSONArray().apply {
                        for (b in pt.scoreBins) put(b)
                    }
                    arr.put(
                        JSONObject().apply {
                            put("date", pt.date)
                            put("tankLevel", pt.tankLevel)
                            put("dailyDelta", pt.dailyDelta)
                            put("flowingCount", pt.flowingCount)
                            put("activeCount", pt.activeCount)
                            put("highPressureCount", pt.highPressureCount)
                            put("avgScore", pt.avgScore)
                            put("euphoriaPressurePct", pt.euphoriaPressurePct)
                            put("breakoutCount", pt.breakoutCount)
                            put("breakoutPulsePct", pt.breakoutPulsePct)
                            put("breakoutPulseDelta20", pt.breakoutPulseDelta20)
                            put("capitulationCount", pt.capitulationCount)
                            put("capitulationDepthPct", pt.capitulationDepthPct)
                            put("plateauDays", pt.plateauDays)
                            if (pt.scoreBins.size == TankCalculator.SCORE_HISTOGRAM_BIN_COUNT) {
                                put("bins", binsArr)
                            }
                            pt.peTankLevel?.takeIf { it.isFinite() }?.let { put("peTankLevel", it) }
                            pt.medianPe?.takeIf { it.isFinite() && it > 0.0 }?.let { put("medianPe", it) }
                            if (pt.peActiveCount > 0) put("peActiveCount", pt.peActiveCount)
                            if (pt.peScoreBins.size == TankCalculator.SCORE_HISTOGRAM_BIN_COUNT) {
                                put("peBins", JSONArray().apply {
                                    for (b in pt.peScoreBins) put(b)
                                })
                            }
                        }
                    )
                }
                root.put(u.subIndexHistoryKey, arr)
            }
            File(context.filesDir, LIVE_HISTORY_VAULT_FILE).writeText(root.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving live history vault", e)
        }
    }

    private fun loadLiveMacroVault(): Map<String, MacroOverlayPoint> {
        return try {
            val file = File(context.filesDir, LIVE_MACRO_VAULT_FILE)
            if (!file.exists()) return emptyMap()
            val root = JSONObject(file.readText())
            val out = LinkedHashMap<String, MacroOverlayPoint>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val dt = keys.next()
                val obj = root.optJSONObject(dt) ?: continue
                val vix = obj.optDouble("vix", Double.NaN)
                val mmi = obj.optDouble("mmi", Double.NaN)
                val adObj = obj.optJSONObject("ad")
                val adMap = LinkedHashMap<TankIndexUniverse, Double>()
                if (adObj != null) {
                    for (u in TankIndexUniverse.entries) {
                        val v = adObj.optDouble(u.constituentKey, Double.NaN)
                        if (!v.isNaN()) adMap[u] = v
                    }
                }
                out[dt] = MacroOverlayPoint(
                    date = dt,
                    indiaVix = if (vix.isNaN() || vix <= 0.0 || dt < DeadlyComboEngine.INDIA_VIX_INCEPTION_DATE) null else vix,
                    mmiValue = if (mmi.isNaN() || mmi !in 0.0..100.0 || dt < DeadlyComboEngine.MMI_VERIFIED_START_DATE) null else mmi,
                    adPctByUniverse = adMap
                )
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading live macro vault", e)
            emptyMap()
        }
    }

    private fun saveLiveMacroVault(overlays: Map<String, MacroOverlayPoint>) {
        try {
            val root = JSONObject()
            for ((dt, pt) in overlays) {
                if (dt <= seedLatestDate) continue
                val adObj = JSONObject()
                for ((u, v) in pt.adPctByUniverse) {
                    adObj.put(u.constituentKey, v)
                }
                root.put(
                    dt,
                    JSONObject().apply {
                        pt.indiaVix?.takeIf { it > 0.0 }?.let { put("vix", it) }
                        pt.mmiValue?.takeIf { it in 0.0..100.0 }?.let { put("mmi", it) }
                        if (adObj.length() > 0) put("ad", adObj)
                    }
                )
            }
            File(context.filesDir, LIVE_MACRO_VAULT_FILE).writeText(root.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving live macro vault", e)
        }
    }
}
