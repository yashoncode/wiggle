package io.wiggle.data

import io.wiggle.BuildConfig
import io.wiggle.domain.AppRelease
import io.wiggle.domain.Updates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val TIMEOUT_MILLIS = 8_000

/**
 * Asks GitHub what the newest published release is.
 *
 * `HttpURLConnection` and `org.json` rather than a networking library: this is one unauthenticated
 * GET of a small document, and an HTTP stack is a lot of APK for that.
 */
@Singleton
class UpdateChecker @Inject constructor() {

    /** The latest release when it is newer than this build, otherwise null. */
    suspend fun newerRelease(current: String = BuildConfig.VERSION_NAME): AppRelease? =
        withContext(Dispatchers.IO) {
            // Anything can go wrong out here — no network, rate limiting, a repository with no
            // releases yet — and none of it is worth interrupting someone weighing themselves.
            val body = runCatching { fetch(Updates.LATEST_RELEASE_API) }.getOrNull()
                ?: return@withContext null
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext null

            val tag = json.optString("tag_name").ifBlank { json.optString("name") }
            if (!Updates.isNewer(tag, current)) return@withContext null

            AppRelease(
                version = tag.trim().trimStart('v', 'V'),
                notes = json.optString("body").trim(),
                downloadUrl = apkAsset(json)
                    ?: json.optString("html_url").ifBlank { Updates.RELEASES_PAGE },
            )
        }

    private fun fetch(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** The attached APK, so the button downloads the build instead of opening a web page. */
    private fun apkAsset(json: JSONObject): String? {
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                return asset.optString("browser_download_url").ifBlank { null }
            }
        }
        return null
    }
}
