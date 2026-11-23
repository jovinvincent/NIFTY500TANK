# Depth Tank (NIFTY-500-TANK) — Final Audit Report (v5)

**Date:** 2026-11-23  
**Repo:** `jovinvincent/NIFTY-500-TANK` @ `2b4bc24` · 224 files  
**Method:** 4 parallel auditors + parent verification + two-way reconciliation with companion (`H\audit\REPORT.md` Rev 3) + **fresh data-accuracy pass** (stocks / PE / VIX / MMI / A-D / tank)  
**Scope note:** Owner directed: **CAS parser / PDF password are not important — removed from priority.** Focus is data accuracy.

**Totals (final):** CRITICAL **5** (C1–C5) · HIGH **20+** · MEDIUM **20+** · LOW **10** · CORE **1** (C10) · OUT-OF-SCOPE **1** (C6, demoted)

---

## VERDICT (final)

Three headline claims **do not survive contact with the data** (re-verified 2026-11-23):

1. **Default modern series is synthetic** (`ILLUSTRATIVE_FALLBACK`) and re-labelled **VERIFIED EOD**.  
2. **P/E percentiles straddle an undisclosed −17% methodology splice** at 2021-03-31 (schema even stores `consolidatedTransitionDate` but percentiles ignore it).  
3. **Peak Sniper has no demonstrated edge** (sell calls → above-baseline 120d returns).

**Data accuracy is mixed:** constituent lists, tank internal math, index extremes, and pre-2016 SHA-256 **pass**. PE splice/holes, VIX fabrication/manifest lie, MMI conflicts, and A/D coverage overclaim **fail**.

C6 (CAS password/PII) is **out of scope** per owner — not counted, not in fix order.

---

## DATA ACCURACY FINAL (fresh re-run 2026-11-23)

Scripts: `data_accuracy_check.py` + `data_accuracy_pass2.py` against shipped assets (no source edits).

### Stocks / universes — PASS
| Check | Result |
|---|---|
| Constituent sizes | 30/50/50/150/250/500/1000 exact; 0 dups |
| Disjointness | 50∩Next50∩Mid150∩Small250 all disjoint; union = 500 = nifty500 |
| Seed stocks | 500 symbols; **exact match** to nifty500; 0 missing |
| Seed 52W sanity | **0** prices outside [low52W, high52W]; 0 invalid |
| Seed chg range | −5.64% … +10.68% (plausible) |
| Pre-2016 SHA-256 | All 3 archives: raw+gz bytes **and** hashes **OK** |
| Tank internal | 2,617 rows; bins=active **2617/2617**; dailyDelta=level-diff **0 mismatches**; 0 dup dates |
| Index extremes | Nifty500 min **6,243.0 @ 2020-03-23**, max **24,496.9 @ 2024-09-26** — real |
| Pre-2016→modern join | 2016-03-31 → 2016-04-01 exact, boundary gap = 1 day |

**Stock data caveats (still real):**
- Modern tank header: `dataQuality=ILLUSTRATIVE_FALLBACK`, `isIllustrative=true`, but `constituentCoverage=1.0` — **coverage claim false for 2016–2019 A/D** (see below).
- Pre-2016: **160** `INCOMPLETE_COLD_START` zeros (1995-01-02→1995-08-24) + **721** `RECONSTRUCTED_HISTORICAL` (→1998-07-31); only **4,380** untagged real rows.
- Valuation stocks: **480/500** — 20 nifty500 names lack PE tuple (GVT&D, MEESHO, ABREL, …).
- Index file dual-keyed: **2,616 ISO + 2,616** `"01-Apr-2016"` style keys (5,232 keys for 2,616 days).

### P/E — FAIL (splice + holes + weekend junk)
Schema facts from `valuation_pe_history.json.gz`:
- `officialPeStartDate=1999-01-01`
- **`consolidatedTransitionDate=2021-03-31`** ← splice is *documented in the asset* but percentile code does not adjust
- `pointInTimeConstituentUniverses=['nifty50','next50']` only

**Nifty 500 official PE (parent re-parse):**
```
2021-03-30  PE 43.33  PB 3.99
2021-03-31  PE 35.89  PB 3.96   ← −17.2% P/E, P/B flat → earnings-basis switch
2021-04-01  PE 36.38  PB 4.02
```
Current PE **22.50 @ 2026-11-20**:
| Window | Percentile |
|---|---|
| Mixed 2007+ | **46.4th** |
| Clean 1999+ | **58.8th** |
| Pre-splice only | **67.7th** |
| Post-splice only | **23.8th** |
| Dates where 2007+ vs 1999+ ranks **straddle 50th** | **700/4928 = 14.2%** |

**Gaps (≥10 missing weekdays):**
| Universe | n | Range | Gaps |
|---|---|---|---|
| nifty500 / nifty50 / next50 | 6940 | 1999-01-01→2026-11-20 | **0** |
| midcap150 | 2613 | 2016-04-01→2026-11-20 | **1**: 2020-09-24→2020-10-30 (25 wd / 36 cal) |
| smallcap250 | 2464 | 2016-04-01→2026-11-20 | **3**: 44d, **163d**, 35d calendar |

**Other PE issues:** 36 weekend rows in big universes (11/10 in mid/small); smallcap250 PE extremes (max ~6,239×, many >1000× in mid-2016); midcap150 single-step **+140.9%** on 2020-06-25→26 (27→65).

### VIX — FAIL (fabrication + missing pre-2016 series)
| Check | Result |
|---|---|
| Modern series | 2,605 pts, 2016-04-01→2026-11-13 |
| Value range | min 9.15, max **83.61**, mean 16.37; **0** out-of-range |
| COVID spike | 2020-03 climbs 25→41+ — plausible |
| **Weekend fabrication** | **Saturday 2026-10-31 = 14.85 = exact copy of 2026-10-20** |
| Missing weekdays between sessions | **167** calendar-weekday dates absent (mix of holidays + real holes; e.g. shared hole era 2016-08-12 *present* in VIX, absent elsewhere) |
| **pre2016 VIX claim** | Manifest `vixDates: 1978` but macro gz has only `{date, ad}` — **no VIX array. FALSE.** |
| Pre-2016 VIX factors | `pre2016VixSessionCount = 0` → silent denominator shrink |

### MMI — FAIL (conflicts + non-trading rows)
| Check | Result |
|---|---|
| Rows / unique dates | 2,633 / 2,619 |
| **Duplicate dates** | **14** |
| **Conflicting mood/value same date** | **12** (last-wins) |
| Example | **2026-01-14**: 62.45 “Greed” vs **25.0 “Extreme Fear”** (src 20260114 vs 20260115) |
| Example | **2026-04-13**: 19.62 Extreme Fear vs 62.1 Greed |
| Empty `mood` | **12** rows |
| Weekend dates | **2** (2025-02-01 Sun, 2026-02-01 Sun) |
| Holidays present | 2025-12-25, 2026-01-01, 2026-03-04 (Holi) |
| AD placeholder | **2026-01-15**: adv=0, dec=0, total=500 (also in A/D file) |

### A/D breadth coverage — FAIL (overclaim)
Nifty500 A/D totals (`advance_decline_historical.json`):
| Year | n | mean total | min | % rows total&lt;500 |
|---|---|---|---|---|
| 2016 | 184 | 302 | 295 | **100%** |
| 2017 | 248 | 316 | 308 | **100%** |
| 2018 | 245 | 335 | 327 | **100%** |
| 2019 | 241 | 351 | 344 | **100%** |
| 2020–2026 | … | 500 | 500 | 0% |
| **All** | 2617 | **439.4** | **295** | **918 (35.1%)** |

First row 2016-04-01 total=296; absolute min **295 @ 2016-05-02**.  
Asset still claims `constituentCoverage: 1.0` — **false for 2016–2019**.  
Placeholder **2026-01-15** adv=0&dec=0.

### Accuracy scorecard (final)

| Domain | Verdict | One-liner |
|---|---|---|
| Stock lists / seed | **PASS** | Exact sizes, disjoint, seed↔500 match, 52W sane |
| Tank math / join / SHA | **PASS** | Internal consistency + hashes + 2016 boundary clean |
| Index prices | **PASS** | Real COVID low & 2024 peak; dual-key waste only |
| **P/E** | **FAIL** | Undocumented-to-code −17% splice; mid/small holes; weekend rows; 20 stocks missing |
| **VIX** | **FAIL** | Saturday copy row; manifest VIX claim false; pre-2016 factors empty |
| **MMI** | **FAIL** | 12 conflicting dups; weekend/holiday rows; empty moods |
| **A/D coverage** | **FAIL** | 35% partial-universe while claiming coverage 1.0 |
| Pre-2016 quality tags | **WARN** | 160 zeros + 721 reconstructed before 1998-08 |

---

## WHAT IS GENUINELY GOOD (verified)

| Claim | Evidence |
|---|---|
| SHA-256 archive verification | All 3 pre-2016 archives: raw+gz hashes **and** byte counts match `pre2016_manifest.json` exactly. Runtime throw on mismatch (`TankRepository.kt:465-467`). Tests incl. corrupt-cache recovery. |
| Tank history internal consistency | 2,617 rows 2016-04-01→2026-11-13; bins sum to activeCount 2617/2617; dailyDelta matches level diff 2616/2616; 0 duplicate dates. |
| Index price file is real | Nifty 500 low 6,243.0 (2020-03-23), peak 24,496.9 (2024-09-26) — matches actual extremes. |
| 9-factor engine is real code | 9 peak (p1–p9) + 9 trough rules with thresholds + availability gating (`DeadlyComboModels.kt:470–756`). Not a mock-up. |
| Constituents | Exact sizes 50/50/150/250/500/1000; 0 dups; disjoint; union = 500. |
| Causal live signal | `computeCausalSignalAt` uses history prefix only (`CycleTurningPointEngine.kt:204–238`). |
| 1995→2016 join | 0 duplicates, 0 overlaps, 0 ordering violations; boundary exact at 2016-04-01. |
| A/D formula | `adv/(adv+dec)*100` — correct. |
| Causal PE attach | As-of pointer; staleness cap **14d** in `TankRepository` (`PE_CAUSAL_MAX_STALE_DAYS=14L`, `:55`) and **10d** on one DeadlyCombo episode path (`:1298`) — both exist; nulls never fabricated (`:1503–1592`). |

---

## CRITICAL (C1–C5)

> **v5:** C6 (CAS password/PII) **removed from CRITICAL and fix order** per owner — out of scope. C7–C9 remain severity-adjusted HIGH. C10 is the core “no edge” finding.

### C1. Synthetic data displayed as “VERIFIED EOD” *(companion C1 — parent VERIFIED)*
- Asset self-declares: `nifty500_tank_history.json` → `"dataQuality":"ILLUSTRATIVE_FALLBACK","isIllustrative":true` (entire 2016→2026 modern series).
- `TankRepository.kt:207-215` (cache path) and `:237-244` (vault path) **hardcode**:
  ```
  dataQuality = DataQuality.VERIFIED_EOD
  isIllustrative = false
  ```
  even when history is the illustrative seed/vault series.
- Live path only checks stock count ≥ 450 (`:694-699`) — not whether *history* is real.
- `DashboardScreen.kt:338-339,508-511` badge reads `datasetIntegrity` → renders **“VERIFIED EOD (N%)”**.
- Invalidates copy “Verified across 2,617 days” (`DeadlyComboModels.kt:474`).
- **Fix:** propagate asset header; never emit `VERIFIED_EOD` without a live ≥450-stock session **and** non-illustrative history; badge whole app ILLUSTRATIVE otherwise.

### C2. Undisclosed valuation splice at 2021-03-31 *(companion C2 — parent VERIFIED by decompress)*
```
2021-03-30  Nifty500 P/E 43.33  P/B 3.99
2021-03-31  Nifty500 P/E 35.89  P/B 3.96   ← −17.2% P/E in one session, P/B flat
2021-04-01  P/E 36.38
```
Earnings-basis switch, not a market move. `DeadlyComboModels.kt:1096-1103` filters 2021+ for “Cons.” percentile while the “20Y” band still mixes bases. Companion Rev 3 (revised after their own non-reproduction of an earlier 43.9% figure): current P/E reads **46.3rd** percentile on the mixed 2007+ window vs **58.8th** on the clean 1999+ window (−12.5 pp bias); same absolute P/E = **67.7th** pre-splice only vs **23.6th** post-splice only (44 pp swing from the methodology break alone); **14.3%** of dates straddle the 50th-percentile line (multi-band regime labels flip correspondingly more often).  
**Fix:** ratio back-adjust pre-2021 by `35.89/43.33`, or split series + label the break.

### C3. Pre-2016 VIX hours claimed, never shipped *(companion C3 — parent VERIFIED)*
- `pre2016_manifest.json:16` → `"vixDates": 1978`
- `pre2016_macro_overlays.json.gz` structure: top-level `vixInceptionDate` + `dates[{date, ad}]` only — **no VIX value array**.
- Any pre-2016 VIX factor silently empty → sniper denominator shrinks → pass mark drops.  
**Fix:** remove manifest claim or ship series; never shrink denominator silently — show N/A per factor.

### C4. Look-ahead disguised as its opposite *(companion C4 — parent VERIFIED)*
`DeadlyComboModels.kt:958-975`:
```kotlin
// Only fold immediate live macro updates (<= 7 calendar days after lastHistDate)
// so historical point-in-time reports never suffer look-ahead bias
if (k > lastHistDate) {
    if (calendarDaysBetween(lastHistDate, k) > 7L) break
    // folds VIX/MMI/A/D into "current" reading
}
```
Folds macro data from **up to 7 days after** the report date into the point-in-time reading. Companion recomputation: flips Peak rule 7 on **491 days**, rule 8 on **564 days**.  
**Fix:** clamp to `k <= lastHistDate`.

### C5. The backtest number is a literal + typed episode ledger *(companion C5 — parent VERIFIED)*
- `DailyRecommendationSummaryScreen.kt:462`:
  ```kotlin
  .let { if (it.isNaN()) 2.4 else it }
  // UI: "Backtested alpha: +2.4% vs blind SIPs"
  ```
  When no positive alphas exist → **hardcoded +2.4%**.
- 17 `HistoricalComboEpisode(...)` literals (`DeadlyComboModels.kt:1903-2338`), including COVID bottom (VIX 71.9, MMI 4.5, fwd-6M +40.5%), rendered as computed analytics.
- Pre-2016 episodes: `maxByOrNull{tankLevel}` inside hand-drawn windows.  
**Fix:** delete fallback; compute episodes or label “editorial commentary”.

### C6. *(OUT OF SCOPE — demoted by owner)* CAS parser / PDF password / family name filters
- Exists in `CasParser.kt` (password constant + name/address filters).  
- **Owner directive: not important for this app — removed from priority and totals.**  
- Optional hygiene later: delete `nse_cookies.txt`, move filters to config. **Not part of final fix order.**

---

## SEVERITY-ADJUSTED HIGH (C7–C9; not counted as CRITICAL)

### C7. Gemini API key in BuildConfig + query string *(HIGH design)*
- `build.gradle.kts:75-80` secrets plugin → `BuildConfig.GEMINI_API_KEY`
- `GeminiService.kt:58-65,85,216` → sent as `?key=$apiKey`
- **No live key in the repo today** (`.env` gitignored; `.env.example` placeholder). Risk is **design**: any real key used at build time is baked into the APK and visible to OkHttp/logs/proxies. Companion correctly downgraded this from “active leak.”  
**Severity: HIGH (design), not CRITICAL (no shipped secret found).**  
**Fix:** backend proxy; never ship key in APK; rotate any key that ever shipped.

### C8. Release signing cannot work as configured *(HIGH)*
- `storePassword`/`keyPassword` only from env (`build.gradle.kts:23-30,39-46`)
- `my-upload-key.jks` not in repo; no `*.jks` found  
**Severity: HIGH** (blocks release builds; not a data/trust critical).  
**Fix:** document keystore provisioning; fail the build clearly if missing.

### C9. Project cannot be built from a clean clone *(companion H9 — HIGH)*
- **No** `gradlew`, `gradlew.bat`, or `gradle/wrapper/*` (only `libs.versions.toml`)
- `kotlinx.coroutines.tasks.await` used (`FirebaseAuthFirestoreManager.kt:128`, etc.) **without** `kotlinx-coroutines-play-services` in version catalog
- No `google-services.json`; Firebase path structurally dead
- Debug signing points at gitignored `${rootDir}/debug.keystore` (no keystore in tree)  
**Severity: HIGH** (companion classifies as H9).  
**Fix:** commit Gradle wrapper; add missing dependency or delete dead Firebase path; fix signing config.

---

## CORE FINDING (not a bug ID)

### C10. “No demonstrated edge” on Peak Sniper *(companion core — forward-return recomputation)*
Recomputed Peak-Sniper calls on shipped series (companion report; baseline n=2,496):

| Metric | Peak “sell” calls | Unconditional baseline |
|---|---|---|
| Mean fwd 120d | **+10.95%** | **+6.52%** |
| Mean fwd 20d (trough) | +2.23% | +1.07% |
| 2020-03-09 trough | **−13.78%** in 20d | — |

Nine consecutive 2020–21 peak calls preceded +13%…+37% returns. Validation also runs on the **ILLUSTRATIVE** series (C1).  
**Conclusion:** trough detection weakly positive; **peak detection has none**. The promise “time the top euphoria” is **not supported by the app’s own data**.  
**Fix:** walk-forward, weighted, out-of-sample design before marketing any hit rate; do not ship peak “sell” as actionable until then.

---

## HIGH (selected — all parent-verified unless noted)

| ID | Finding | Evidence |
|---|---|---|
| **H1** | Breadth coverage 59%→100% unnormalised; Nifty500 A/D min 296/500 (2016-04-01); 918/2617 days partial-universe while asset claims `constituentCoverage: 1.0` | `advance_decline_historical.json` |
| **H2** | Survivorship/look-ahead **not** eliminated: one static undated constituent snapshot applied to 1995; only Nifty50+Next50 marked PIT (`DeadlyComboModels.kt:1073`); UI presents all five as PIT | code + data |
| **H3** | “From Jan 1995” = 160 cold-start zeros + 721 reconstructed; real breadth from **1998-08** | `pre2016_manifest.json` |
| **H4** | MMI feed: 14 conflicting duplicate dates (e.g. 2026-01-14: 62.45 Greed vs 25.0 Extreme Fear); non-trading rows (2025-12-25, Sun 2026-02-01); AD placeholder adv=0,dec=0,unc=500 | `goodreturns_mmi_history.json` |
| **H5** | VIX calendar: 17 weekday holes; **Saturday 2026-10-31** carries copy of 2026-10-20; shared hole 2016-08-12; `index_historical` every date **twice** (ISO + `01-Apr-2016`) | assets |
| **H6** | **Trough factor 8 tautology** — `isTriggered = maxVix15 >= 15.0 && curVix <= maxVix15` (`DeadlyComboModels.kt:723`); `curVix` is last element of same 15d window → second clause **always true**. Reduces to “VIX max ≥15”, fires ~67.8% of days | parent VERIFIED |
| **H7** | 9 factors not independent: unweighted votes; **P4/P5/P6 all require `h75Max15d ≥ 38.0`** (`:519,528,541`) — 3 of 9 gated on same condition; all from one histogram | parent VERIFIED |
| **H8** | Copy “≥8/9” (`:151`, `:1860`) vs engine `proportionalCriticalMin(9)=7` (`:275-276`); companion: 14/22 calls fired at **7/9** | parent VERIFIED |
| **H9** | Build broken from clean clone (= C9, severity-adjusted HIGH) | no gradlew |
| **H9b** | Release signing cannot work as configured (= C8) | env-only passwords; no `*.jks` |
| **H9c** | Gemini key in APK via `?key=` (= C7, design; no live key in repo) | `GeminiService.kt:58-65` |
| **H10** | Firebase vault dead (no google-services.json / plugin); live quotes from **TradingView scanner**, not NSE | `FirebaseAuthFirestoreManager.kt` |
| **H11** | Device scraping + exposure: NSE UA/Referer spoof + cookie replay; goodreturns Cloudflare-blocked (`page.html`); `allowBackup=true` empty rules ships Room portfolio DB; FileProvider `external-path "."`; **55 public screenshots (~27 MB) with real portfolio values** | `AndroidManifest.xml:9`, `public/` |
| **H12** | VIX nearest-neighbor **no max-gap**; far dates → first/last VIX or fallback **14.54** (`IndiaVixManager.kt:260-278`) | parent |
| **H13** | A/D rate-limit ineffective: `now-last<30s && isFetching` almost never blocks → hammers NSE/TradingView (`AdvanceDeclineManager.kt:425-429`) | parent |
| **H14** | UI pipeline: `uiState = combine(...).stateIn` with **no `flowOn`** (`TankViewModel.kt:172,391`) — transform lambda runs on collector; heavy work is partly in repo `suspend`/IO, so ANR risk is **real but softer than “all on Main.” Jank on hole/aggregation changes remains plausible. | parent (nuanced) |
| **H15** | Daily EOD sync only when ViewModel created; `runAttemptCount≥2` forces success (masks failure) | parent |
| **H16** | NavUpdateWorker “past limit” inverted (before 19:00 treated as past → skips to next day); cleartext HTTP dead on API 28+ | parent |
| **H17** | Gemini model IDs (`gemini-3.5-flash`, `gemini-3.1-pro-preview`, `gemini-3.1-flash-lite` in `GeminiService.kt:30-34`) — companion Rev 3 **refutes “likely 404s”**: these are real Google IDs; `gemini-3.5-flash` is deprecated but API requests **auto-route** (e.g. to `gemini-3.6-flash`), no 404. **Retract 404 claim.** Residual risk is design: key in APK via `?key=` (see C7/H17 design), hardcoded model enum with no runtime version check, and `modelUsed` toast hardcodes `"gemini-3.5-flash"` even after transcription (`GeminiChatScreen.kt:125,315`). | parent (softened per companion Rev 3) |
| **H18** | A/D fallback synthesises breadth from price delta `(50+dailyDelta*8)` — circular with p1–p5 | quant agent |
| **H19** | MMI weights: **docstrings say 45/35/20** (`MarketMoodIndexEngine.kt:56,115`) vs code `0.30/0.30/0.40` (`:80-82`); MMI is **40% VIX** so Peak P7+P8 double-count volatility; non-authentic dates → MMI=50; cache floor rejects fear (`gScore≥46`) | parent VERIFIED |
| **H20** | Primary PE percentile mixes standalone/consolidated eras; “20Y” window is ~19y or ~27y (`DeadlyComboModels.kt:1079`) | parent |

---

## MEDIUM / LOW (condensed)

- **M1** `euphoriaPressurePct` ≡ highPressureCount/activeCount (corr 1.000) — not independent  
- **M2** “20Y” hardcoded label even when window is 5.6 years  
- **M3** Valuation asset: 480/500 stocks, one 4-tuple each — no per-stock history → 52W norm untestable; 36 weekend rows in “official” series; smallcap 239 rows >100× P/E (max 44,711×)  
- **M3b** Official PE holes (parent re-parsed `officialIndexPe` only): **midcap150 36d** `2020-09-24→2020-10-30` (Oct2020 n=1); **smallcap250** 44d/163d/35d; **nifty500/50/next50: no gaps >10d** — earlier “Next50 missing Oct 2020” claim was **wrong** (see Cross-Audit)  
- **M4** Hole slider: 92 rows deviate >1pt from own bins; seam 2016-04-01 dailyDelta 0.00 vs true −1.91  
- **M5** Gap-day sessions synthesised from Yahoo closes, 52W bounds stretched to current price, **persisted into history**  
- **M6** `RECORD_AUDIO` **not declared** but `MediaRecorder` used (`AudioRecordingManager.kt`) → voice capture throws SecurityException; instrumented test asserts `com.example` vs `applicationId com.aistudio.portfolio.rkemel`  
- **M7** ~64/84 Kotlin files (legacy portfolio tracker) unreachable from MainActivity; many asset JSONs unused  
- **M8** Chart fixed presets (not true 1–31y slicer); Stock Explorer has **no P/E filter** (display only); LTCG “lot harvesting” is prose  
- **M9** NSE holiday list only through **2027-11** — wrong calendar after that  
- **M10** Many hardcoded “today” defaults (`2026-11-13`, `31-Oct-2026`)  
- **M11** `BuiltInNavLoader` dual-format parse never falls through (SimpleDateFormat throws → `0L`)  
- **M12** Δ20 breakout lookback off-by-one (bar 21, not 20)  
- **M13** SHA-256 only on pre-2016 trio — `valuation_pe_history.json.gz` + all plain JSON **unchecked**  
- **M14** Empty main tank → 0.0 reads as capitulation; flat 52W range → score 50 mid (live path gated by `hasValid52WeekHistory`; backfill path not)  
- **M15** Synthetic bin reconstruction (×1.15/×0.55) when bins missing  
- **M16** XIRR fallback hardcodes `set(2026, Calendar.JANUARY, 1)` — `DailyRecommendationSummaryScreen.kt:158,164` and `SmartSipRecommendationScreen.kt:95,101` (and siblings) — companion’s “no such literal” claim is **false**  
- **L1** Package still `com.example`; applicationId AI Studio template  
- **L2** No product README in tree  
- **L3** Dark-only M3; a11y unaudited  
- **L4** `page.html` = Cloudflare block page artifact  
- **L5** Snapshot tests brittle to asset updates  
- **L6** `nse_cookies.txt` header-only (135 B) — delete for hygiene  

---

## Claims vs reality (updated)

| Claim | Verdict |
|---|---|
| Five NSE universes | **TRUE** |
| Hole 15–85 valves | **TRUE** |
| Depth DB to Jan 1995 | **OVERSTATED** — real data from 1998-08 |
| SHA-256 + chronological joins | **TRUE** for pre-2016 trio only |
| Official PIT P/E, P/B, DY all universes | **FALSE/PARTIAL** — PE from 1999/2016; PI stock PE only nifty50+next50; DY cosmetic; −17% splice undisclosed |
| Survivorship/look-ahead eliminated | **FALSE** — static constituents applied historically |
| Dual-valuation engine | **TRUE** structurally; **mixed-era percentile bug** |
| 9-Factor sniper | **TRUE count** — overfit, non-independent, 7/9 vs 8/9 copy, factor-8 tautology, **no edge** |
| GFC/COVID/Dot-Com analogs | **UI TRUE** — **not** a matching engine (hardcoded windows) |
| Real-time | **OVERSTATED** — EOD 16:15 IST + opportunistic HTTP |
| Stock Explorer P/E filters | **FALSE** — display only |
| Backtested alpha +2.4% | **FALSE** when NaN — literal fallback |
| “VERIFIED EOD” badge | **FALSE** for default history path |
| Build from clone | **FALSE** — no wrapper, missing deps |

---

## Priority fix order (FINAL — data accuracy first; CAS/password removed)

1. **Stop labelling synthetic as VERIFIED EOD** (C1) — propagate `ILLUSTRATIVE_FALLBACK`; badge honest  
2. **Splice or split P/E at 2021-03-31** (C2) — asset already has `consolidatedTransitionDate`; use it (ratio back-adjust `35.89/43.33` or two series + label)  
3. **Fix A/D `constituentCoverage: 1.0`** (H1) — store per-row coverage; 2016–2019 are ~59–70% universe  
4. **Clamp 7-day macro fold look-ahead** (C4) → `k <= lastHistDate`  
5. **VIX integrity** (C3/H5/H13): remove fabricated **Sat 2026-10-31** row; drop false `vixDates:1978` or ship series; cap nearest-date carry; delete `14.54` default  
6. **MMI feed hygiene** (H4): de-dupe 14 dups by `source_id` (12 conflicts); reject weekends/holidays; reject empty mood; reject adv=0&dec=0 rows  
7. **Engine honesty** (H6 tautology, H8 7/9 vs 8/9, H12 A/D-from-price, H19 MMI weights/doc mismatch)  
8. **Delete hardcoded +2.4% alpha + typed episodes** (C5)  
9. **Do not market peak hit rates** until walk-forward OOS (C10)  
10. PE holes: fill or N/A midcap150 36d + smallcap250 163d/44d/35d; flag 20 stocks missing valuation  
11. Pre-2016: start charts at 1998-08 or shade cold-start/reconstructed bands  
12. Build: Gradle wrapper + deps + signing (C9/C8); Gemini key not in APK (C7)  
13. Exposure: strip screenshots; `allowBackup=false`; narrow FileProvider (H11)  
14. Perf/TZ/rate-limit/worker; honest README with data caveats  

*(C6 CAS/password intentionally omitted.)*

---

## Cross-audit reconciliation (parent re-test of companion Rev 2 → Rev 3)

**Round 1 (companion Rev 2):** 5 “errors in the external report.” Parent re-ran each against source/data.

| # | Companion claim about *our* report | Parent verdict | Evidence |
|---|---|---|---|
| 1 | “Next50 missing all Oct 2020” is false | **THEY ARE RIGHT — we were wrong** | Parsed `officialIndexPe` only: next50 n=6940, **0 gaps >10d**, Oct2020 n=21. Only **midcap150** has the 36d hole (Oct2020 n=1). **Corrected above (M3b).** |
| 2 | “Causal PE ≤14d” wrong; code is 10d | **PARTIALLY WRONG (both exist)** | `TankRepository.kt:55` `PE_CAUSAL_MAX_STALE_DAYS = 14L`; `DeadlyComboModels.kt:1002,1061` use `<= 14L`; **also** `:1298` uses `<= 10L` on episode/nearest-PE path. Blanket “code is 10 not 14” is incorrect. |
| 3 | “MMI docs 45/35/20” — no such docstring | **THEY ARE WRONG** | `MarketMoodIndexEngine.kt:56` comment *“45% TMMI + 35% GMMI + 20% VIX”*; `:115` *“45% Tickertape…35% Goodreturns…20% India VIX”*; constants `:80-82` = 0.30/0.30/0.40. **Finding stands (H19).** |
| 4 | XIRR `2026-01-01` “no such literal” | **THEY ARE WRONG** | `DailyRecommendationSummaryScreen.kt:158,164` and `SmartSipRecommendationScreen.kt:95,101`: `set(2026, Calendar.JANUARY, 1)`. Also `FundFutureValueScreen`, `PortfolioCostSummaryScreen`, etc. **Finding stands (M16).** |
| 5 | Rating inflation (Gemini key, cookies, Main thread) | **PARTIALLY ACCEPTED** | Gemini: no live key in repo → **downgraded C7 to HIGH design risk**. Cookies: header-only → **LOW (L6)**. Main thread: `stateIn` without `flowOn` is real, but heavy IO is in repo suspend fns → **H14 softened**, not retracted. |

### What we adopt from companion (confirmed)

| Item | Status |
|---|---|
| VERIFIED_EOD hardcode (C1) | Adopted — parent re-read `:207-215,:237-244` |
| −17% PE splice exact values (C2) | Adopted — parent decompressed asset: 43.33→35.89 on 2021-03-31 |
| VIX manifest `vixDates:1978` but no array (C3) | Adopted |
| 7-day macro fold look-ahead (C4) | Adopted — `:958-975` |
| Hardcoded +2.4% alpha (C5) | Adopted — `:462` |
| Password + PII (C6) | Companion finding valid but **owner demoted — out of scope in v5** |
| Factor-8 tautology (H6) | Adopted — `:723` |
| 8/9 copy vs `proportionalCriticalMin=7` (H8) | Adopted |
| No Gradle wrapper (H9) | Adopted — no `gradlew*` in tree |
| Forward-return edge table (C10) | Adopted as companion recomputation; thresholds match code; not fully re-run line-by-line |
| A/D from `50+dailyDelta*8` (H12) | Adopted — `DeadlyComboModels.kt:947` |
| MMI 40% VIX double-count P7/P8 (H14 companion) | Merged into **H19** |
| Degenerate 52W→50 with eligibility nuance | Merged into **M14** |

### Where we still differ (or add)

| Topic | Our position |
|---|---|
| PE stale days | **Both 14 and 10** by code path — not “10 only” |
| MMI 45/35/20 docs | **Exist** at `:56,:115` — companion error |
| XIRR 2026 literal | **Exists** in ≥4 screens — companion error |
| Gemini / cookies severity | Downgraded per evidence; not CRITICAL today |
| Main-thread ANR | Softened, not dropped |
| Build/security extras | Release signing env, Gemini `?key=`, NavUpdateWorker window, holiday list 2027, TZ/rate-limit — **our unique HIGHs**, not in companion |
| Claims table / README gap | Our claims-auditor unique deliverable |
| Fix-order on C6 | **Resolved:** owner says CAS/password not important → **removed from our fix order entirely** (v5) |
| Model IDs | Companion right: **retract “likely 404s.”** IDs are live/auto-routing; keep only the design risks (key-in-APK, no version pin) |
| C2 flip-rate figure | Companion right: **drop our carried 43.9%** (their earlier number, non-reproduced). Use 46.3 vs 58.8 percentile, 44 pp pre/post swing, **14.3%** straddle 50th |

**Material disagreements after full two-way (Rev 3):**  
- Companion conceded to us: MMI 45/35/20 docstrings exist; XIRR `2026-01-01` literal exists; PE-stale is both 14d and 10d by path.  
- We concede to companion: Next50 Oct-2020 PE gap claim was wrong (M3b); **Gemini 404 claim retracted (H17)**; **43.9% regime-flip replaced (C2)**.  
- Severity accepted: Gemini key → HIGH design; cookies → LOW; Main-thread → softened H14.  
- **Net: reports now agree on all CRITICAL/HIGH substance. Remaining diffs are fix-order preference and unique LOW/MEDIUM extras on each side.**

### Round 2 (companion Rev 3 — two-way complete)

Companion Rev 3 re-verified our report and listed **2 errors still standing in our prior v3.** Parent re-tested both:

| # | Companion claim about *our* report (Rev 3) | Parent verdict | Action in v4 |
|---|---|---|---|
| 1 | **“Gemini model IDs → likely 404s”** still unretracted | **THEY ARE RIGHT — we retract.** `gemini-3.5-flash` / `3.1-pro-preview` / `3.1-flash-lite` are real Google model IDs; deprecated IDs auto-route, no 404. Source enum at `GeminiService.kt:30-34` unchanged. | **H17 rewritten** — drop 404 claim; keep design risks (key-in-APK, no version pin, toast hardcodes model name). |
| 2 | **“Regime band flips on 43.9% of dates”** still in our C2 | **THEY ARE RIGHT — figure not reproducible (their own earlier number).** Corrected stats: **46.3rd vs 58.8th** percentile (mixed vs clean), **67.7th vs 23.6th** (pre- vs post-splice), **14.3%** straddle 50th line. | **C2 updated** — 43.9% removed; revised figures adopted. |

Companion Rev 3 also **adopted from us** (their “Confirmed on re-verification”): live-path ≥450 gate, A/D rate-limit hole, `runAttemptCount≥2` success force, NavUpdateWorker inverted window, holiday table through 2027-11, P4/P5/P6 shared gate, Dashboard badge ternary, MMI doc mismatch, XIRR literal — all already in this report.

**Final cross-audit state (v4 ↔ companion Rev 3):** all CRITICAL/HIGH substance agreed. Companion’s third note — *header CRITICAL 9 vs section listing 10* — **also fixed** (true CRITICALs = C1–C6; C7–C9 severity-adjusted HIGH; C10 core finding). Our unique extras retained: release signing env failure, NavUpdateWorker inversion, NSE holiday 2027 end, TZ/rate-limit details, full claims-vs-reality table. Companion unique extras retained in their report: forward-return edge table detail (adopted as C10), B8 tautology fire-rate breakdown (adopted as H6).

---

*No app source files were modified. Data scripts written for this audit only: `data_accuracy_check.py`, `data_accuracy_pass2.py`, `data_accuracy_results.txt`. Companion: `C:\Users\11023\Desktop\CC\500\H\audit\REPORT.md` (Rev 3).*

**FINAL (v5):** Data accuracy re-checked end-to-end. Stocks/tank/index/SHA **pass**. PE splice+holes, VIX fabrication+false manifest, MMI conflicts, A/D coverage overclaim **fail**. CAS/password **out of scope** per owner. Both AI reports agree on all material findings.
