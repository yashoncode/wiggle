package io.wiggle.data

import io.wiggle.BuildConfig
import io.wiggle.domain.Food
import io.wiggle.domain.servingLabelFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

private const val TIMEOUT_MILLIS = 10_000

/**
 * Packaged food from Open Food Facts, the open product database: millions of products, Indian
 * shelves included, under the Open Database Licence. Used for what the bundled table cannot know,
 * brands and barcodes, and only when online.
 *
 * `HttpURLConnection` and `org.json` for the same reason as `UpdateChecker`: two small GETs are not
 * worth an HTTP stack in the APK.
 */
@Singleton
class OpenFoodFacts @Inject constructor() {

    /**
     * Products matching [query]. Products sold in India first, then the rest of the world when
     * India alone turns up too few. Anything without an energy value is dropped, because a food
     * with no calories cannot be counted.
     */
    suspend fun search(query: String): List<Food> = withContext(Dispatchers.IO) {
        val india = runCatching { searchOnce("$query countries_tags:\"en:india\"") }.getOrDefault(emptyList())
        if (india.size >= 8) return@withContext india
        val world = runCatching { searchOnce(query) }.getOrDefault(emptyList())
        (india + world).distinctBy { it.key }
    }

    /** The product behind a scanned barcode, or null when it is not in the database. */
    suspend fun product(barcode: String): Food? = withContext(Dispatchers.IO) {
        val code = barcode.filter(Char::isDigit).ifEmpty { return@withContext null }
        val body = runCatching { fetch("https://world.openfoodfacts.org/api/v2/product/$code?fields=$FIELDS") }
            .getOrNull() ?: return@withContext null
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext null
        if (json.optInt("status") != 1) return@withContext null
        json.optJSONObject("product")?.let(::toFood)
    }

    private fun searchOnce(query: String): List<Food> {
        val url = "https://search.openfoodfacts.org/search?page_size=40&fields=$FIELDS&q=" +
            URLEncoder.encode(query, "UTF-8")
        val body = fetch(url) ?: return emptyList()
        val hits = JSONObject(body).optJSONArray("hits") ?: return emptyList()
        return (0 until hits.length()).mapNotNull { hits.optJSONObject(it)?.let(::toFood) }
    }

    private fun toFood(product: JSONObject): Food? {
        val nutriments = product.optJSONObject("nutriments") ?: return null
        val kcal = nutriments.number("energy-kcal_100g")
            ?: nutriments.number("energy_100g")?.div(4.184)
            ?: return null
        val name = product.optString("product_name").trim().ifEmpty { return null }
        val brand = when (val brands = product.opt("brands")) {
            is JSONArray -> brands.optString(0)
            is String -> brands.substringBefore(',')
            else -> ""
        }.trim()
        val servingG = product.number("serving_quantity")?.takeIf { it in 1.0..2000.0 }
        return Food(
            name = if (brand.isEmpty() || name.contains(brand, ignoreCase = true)) name else "$name · $brand",
            kcal100 = kcal,
            protein100 = nutriments.number("proteins_100g") ?: 0.0,
            carbs100 = nutriments.number("carbohydrates_100g") ?: 0.0,
            fat100 = nutriments.number("fat_100g") ?: 0.0,
            servingLabel = if (servingG == null) "" else servingLabelFrom(product.optString("serving_size")),
            servingG = servingG ?: 100.0,
            source = "OFF",
        )
    }

    private fun JSONObject.number(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key).takeIf { it.isFinite() && it >= 0 }
            ?: optString(key).replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
    }

    private fun fetch(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            // Open Food Facts asks every app to say who it is.
            setRequestProperty("User-Agent", "Wiggle/${BuildConfig.VERSION_NAME} (Android; github.com/yashoncode/wiggle)")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val FIELDS = "code,product_name,brands,serving_size,serving_quantity,nutriments"
    }
}
