package com.lynerrr.food.net

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Reads the newest published release of a public GitHub repository, where app updates are hosted. */
object GitHubReleases {
    /** [version] is the release tag without its "v" ("1.2"); [apkUrl] the release's .apk asset. */
    data class Release(val version: String, val notes: String, val apkUrl: String, val apkSize: Long)

    /** The latest release with an .apk attached, or null when there is none (blocking; call off the main thread). */
    fun latest(repo: String, api: String = "https://api.github.com"): Release? {
        val connection = URL("$api/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        val status = connection.responseCode
        if (status == 404) return null // no release published yet
        if (status != 200) throw IOException("GitHub answered HTTP $status")
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val assets = json.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") } ?: return null
        return Release(
            version = json.getString("tag_name").removePrefix("v"),
            notes = json.optString("body").trim(),
            apkUrl = apk.getString("browser_download_url"),
            apkSize = apk.optLong("size"),
        )
    }

    /** True when [remote] ("1.10") is a higher version than [current] ("1.9"); parts compare as numbers. */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val c = parts(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val diff = r.getOrElse(i) { 0 } - c.getOrElse(i) { 0 }
            if (diff != 0) return diff > 0
        }
        return false
    }
}
