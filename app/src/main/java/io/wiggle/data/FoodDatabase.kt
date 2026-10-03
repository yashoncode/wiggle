package io.wiggle.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.wiggle.domain.FoodSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The food table that ships inside the APK, built by `tools/build_foods.py` from openly licensed
 * food-composition data. Read once, on first search, and kept in memory: a few thousand rows is a
 * megabyte or two, and searching a list is faster than anything a query could do.
 */
@Singleton
class FoodDatabase @Inject constructor(@ApplicationContext private val context: Context) {

    private val mutex = Mutex()
    private var loaded: FoodSearch? = null

    suspend fun search(): FoodSearch = mutex.withLock {
        loaded ?: load().also { loaded = it }
    }

    private suspend fun load(): FoodSearch = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(ASSET).bufferedReader().useLines { FoodSearch(FoodSearch.parse(it)) }
        }.getOrElse {
            // Search then falls back to Open Food Facts alone, which still answers when online.
            Log.w("FoodDatabase", "Could not read $ASSET", it)
            FoodSearch(emptyList())
        }
    }

    private companion object {
        // Kept gzipped in the repository as foods.tsv.gz; the asset packager unpacks a .gz asset and
        // drops the extension, so inside the APK it is plain foods.tsv (and zip-compressed there).
        const val ASSET = "foods.tsv"
    }
}
