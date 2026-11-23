package com.example.depthtank.data

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class TankDailySyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = TankRepository(applicationContext)
        val syncResult = repository.refreshLiveMarketData()
        val status = repository.pipelineStatus.value
        val valid = repository.validateIntegrityMetadata()
        Log.i(
            TAG,
            "Daily EOD sync complete: success=${syncResult.isSuccess}, tradeDate=${status.lastTradeDate}, " +
                "quality=${status.integrity.dataQuality}, coverage=${status.integrity.constituentCoverage}, " +
                "model=${status.integrity.modelVersion}/${status.integrity.calculationVersion}, valid=$valid"
        )
        scheduleNextWeekdayEodSync(applicationContext)
        return if (syncResult.isSuccess || runAttemptCount >= 2) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "TankDailySyncWorker"
        private const val UNIQUE_WORK_NAME = "nifty500_depth_tank_daily_eod_sync"
        private val EOD_SYNC_TIME: LocalTime = LocalTime.of(16, 15)

        fun scheduleNextWeekdayEodSync(context: Context) {
            val nowIst = ZonedDateTime.now(NseMarketCalendar.IST_ZONE)
            var nextRun = nowIst.with(EOD_SYNC_TIME)
            if (!nextRun.isAfter(nowIst)) {
                nextRun = nextRun.plusDays(1)
            }
            while (!NseMarketCalendar.isTradingDay(nextRun.toLocalDate())) {
                nextRun = nextRun.plusDays(1)
            }
            val delayMillis = Duration.between(nowIst, nextRun).toMillis().coerceAtLeast(60_000L)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<TankDailySyncWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
