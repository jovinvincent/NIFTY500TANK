package com.example

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class HistoricalSyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        fun initialize(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<HistoricalSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    10, TimeUnit.MINUTES
                )
                .addTag("HistoricalSyncWorker")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "FullHistoricalSyncPeriodic",
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
        }
    }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("HistoricalSyncWorker", "Background offline historical sync requested. Recomputing...")
                val repository = FundRepository(AppDatabase.getDatabase(appContext).fundDao())
                BuiltInNavLoader.recomputeFundsFromHistory(repository)
                Result.success()
            } catch (e: Exception) {
                e.printStackTrace()
                Result.success()
            }
        }
    }
}
