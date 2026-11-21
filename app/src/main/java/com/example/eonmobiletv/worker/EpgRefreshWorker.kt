package com.example.eonmobiletv.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.eonmobiletv.data.EonPreferencesRepository
import com.example.eonmobiletv.data.EpgCacheStore
import com.example.eonmobiletv.data.GzipStreamHelper
import com.example.eonmobiletv.data.XmlTvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.concurrent.TimeUnit

class EpgRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = EonPreferencesRepository(applicationContext)
        val cacheStore = EpgCacheStore(applicationContext)
        val url = prefs.externalEpgUrlFlow.first()?.trim()

        if (url.isNullOrEmpty()) {
            return@withContext Result.success()
        }

        return@withContext try {
            val parsedMap = URL(url).openStream().use { raw ->
                GzipStreamHelper.openPossiblyGzipped(raw, url).use { xmlStream ->
                    XmlTvParser.parseProgrammesByChannelId(xmlStream)
                }
            }
            cacheStore.writeCache(parsedMap)
            prefs.markEpgRefreshed()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "eon_xmltv_epg_refresh_6h"

        fun scheduleEverySixHours(context: Context, replaceExisting: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<EpgRefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            val policy = if (replaceExisting) {
                ExistingPeriodicWorkPolicy.UPDATE
            } else {
                ExistingPeriodicWorkPolicy.KEEP
            }

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, policy, request)
        }

        fun cancelSchedule(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
