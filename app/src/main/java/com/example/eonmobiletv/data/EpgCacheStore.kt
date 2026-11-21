package com.example.eonmobiletv.data

import android.content.Context
import com.example.eonmobiletv.model.EpgProgramme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

class EpgCacheStore(private val context: Context) {

    private val cacheFile: File
        get() = File(context.filesDir, "epg_programmes_cache.json")

    suspend fun writeCache(map: Map<String, List<EpgProgramme>>) = withContext(Dispatchers.IO) {
        val root = JSONObject()
        map.forEach { (channelKey, programmes) ->
            val arr = JSONArray()
            programmes.forEach { prog ->
                val obj = JSONObject()
                    .put("title", prog.title)
                    .put("startSec", prog.startTime.epochSecond)
                    .put("endSec", prog.endTime.epochSecond)
                    .put("categoryTag", prog.categoryTag)
                    .put("description", prog.description)
                arr.put(obj)
            }
            root.put(channelKey, arr)
        }
        cacheFile.writeText(root.toString(), Charsets.UTF_8)
    }

    suspend fun readCache(): Map<String, List<EpgProgramme>> = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) return@withContext emptyMap()
        runCatching {
            val raw = cacheFile.readText(Charsets.UTF_8)
            val root = JSONObject(raw)
            val result = mutableMapOf<String, List<EpgProgramme>>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val arr = root.optJSONArray(key) ?: continue
                val list = mutableListOf<EpgProgramme>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val title = obj.optString("title", "")
                    if (title.isBlank()) continue
                    val startSec = obj.optLong("startSec", 0L)
                    val endSec = obj.optLong("endSec", startSec + 3600L)
                    list.add(
                        EpgProgramme(
                            title = title,
                            startTime = Instant.ofEpochSecond(startSec),
                            endTime = Instant.ofEpochSecond(endSec),
                            categoryTag = obj.optString("categoryTag", "EPG"),
                            description = obj.optString("description", "")
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    result[key] = list.sortedBy { it.startTime }
                }
            }
            result
        }.getOrDefault(emptyMap())
    }
}
