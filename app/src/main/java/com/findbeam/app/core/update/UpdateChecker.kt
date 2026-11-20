package com.findbeam.app.core.update

import com.findbeam.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Check for updates" — queries the GitHub Releases API for the latest
 * published release of this app. Manual action only; nothing is collected.
 */
object UpdateChecker {

    data class UpdateInfo(
        val version: String,
        val notes: String,
        val pageUrl: String,
        val apkUrl: String?
    )

    sealed interface Outcome {
        data object UpToDate : Outcome
        data class Available(val info: UpdateInfo) : Outcome
        data object Offline : Outcome
    }

    suspend fun check(currentVersion: String): Outcome = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return@withContext Outcome.Offline
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            val tag = json.optString("tag_name", "").removePrefix("v").trim()
            if (tag.isEmpty()) return@withContext Outcome.Offline

            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        apkUrl = a.optString("browser_download_url")
                        break
                    }
                }
            }

            val info = UpdateInfo(
                version = tag,
                notes = json.optString("body", "").take(1200),
                pageUrl = json.optString("html_url"),
                apkUrl = apkUrl
            )

            if (isNewer(currentVersion, tag)) Outcome.Available(info) else Outcome.UpToDate
        } catch (_: Exception) {
            Outcome.Offline
        }
    }

    /** Numeric semver-ish compare: "1.2.10" > "1.2.9". */
    fun isNewer(current: String, latest: String): Boolean {
        val c = current.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val l = latest.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val len = maxOf(c.size, l.size)
        for (i in 0 until len) {
            val cv = c.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }
}
