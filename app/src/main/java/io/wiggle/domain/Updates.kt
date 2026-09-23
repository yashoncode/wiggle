package io.wiggle.domain

/** A published release of the app, as the update sheet needs to describe it. */
data class AppRelease(
    val version: String,
    val notes: String,
    val downloadUrl: String,
)

/**
 * Wiggle is distributed as an APK on GitHub rather than through a store, so the app has to do its
 * own looking. Everything here is pure string work; the fetching lives in `UpdateChecker`.
 */
object Updates {

    const val REPO = "yashoncode/wiggle"
    const val LATEST_RELEASE_API = "https://api.github.com/repos/$REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"

    /**
     * True when [latest] is a higher version than [current].
     *
     * Tolerant of what real tags and builds look like: a `v` prefix on the tag, a `-debug` suffix
     * on a debug build, and a different number of parts on either side, so `1.2.1` beats `1.2`
     * and `1.10` beats `1.9`.
     */
    fun isNewer(latest: String, current: String): Boolean {
        val newer = parts(latest)
        val installed = parts(current)
        if (newer.isEmpty()) return false
        for (i in 0 until maxOf(newer.size, installed.size)) {
            val a = newer.getOrElse(i) { 0 }
            val b = installed.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parts(version: String): List<Int> =
        version.trim().trimStart('v', 'V').substringBefore('-')
            .split('.')
            .mapNotNull { it.trim().toIntOrNull() }
}
