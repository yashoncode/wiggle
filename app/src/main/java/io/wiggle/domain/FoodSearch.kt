package io.wiggle.domain

import kotlin.math.roundToInt

/**
 * A food as the add sheet offers it: nutrients per 100 g and one household serving. Where it came
 * from goes in [source]: `IN` and `US` for the bundled tables, `OFF` for Open Food Facts, `MY` for
 * one the person made.
 */
data class Food(
    val name: String,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    val servingLabel: String,
    val servingG: Double,
    val source: String,
    val aka: String = "",
) {
    /** What identifies a food across the database, favourites and recents. */
    val key: String get() = "$source:$name"

    fun grams(servings: Double): Double = servingG * servings

    fun kcal(servings: Double): Int = (kcal100 * grams(servings) / 100).roundToInt()

    fun protein(servings: Double): Double = protein100 * grams(servings) / 100

    fun carbs(servings: Double): Double = carbs100 * grams(servings) / 100

    fun fat(servings: Double): Double = fat100 * grams(servings) / 100

    /** "1 chapati (40 g)", or "100 g" when there is no household measure. */
    val servingText: String
        get() = if (servingLabel.isBlank()) "${servingG.format(0)} g"
        else "$servingLabel (${servingG.format(0)} g)"
}

/** "2 × 1 chapati", "½ × 1 katori", or plain grams when there is no household measure. */
fun portionText(servings: Double, servingLabel: String, servingG: Double): String {
    if (servingLabel.isBlank()) return "${(servingG * servings).format(0)} g"
    if (servings == 1.0) return servingLabel
    val count = if (servings == 0.5) "½" else servings.format(if (servings % 1.0 == 0.0) 0 else 1)
    return "$count × $servingLabel"
}

private val GramsInLabel =
    Regex("\\(?\\s*\\d+(?:[.,]\\d+)?\\s*(?:g|gm|gms|gram|grams|ml)\\b\\s*\\)?", RegexOption.IGNORE_CASE)

/**
 * The household part of a product's serving size: "1 packet (70 g)" is "1 packet", and a bare
 * "30 g" has none, so it comes back empty and the serving is shown in grams.
 */
fun servingLabelFrom(servingSize: String): String =
    servingSize.replace(GramsInLabel, "").trim().trimEnd(',', '-', '/').trim()

/**
 * The bundled food table and the search over it.
 *
 * A linear scan rather than an index: a few thousand rows scored per keystroke takes a few
 * milliseconds, and an FTS table would be a second database to build, ship and migrate.
 */
class FoodSearch(foods: List<Food>) {

    private class Indexed(val food: Food, val name: String, val words: List<String>, val akaWords: List<String>)

    private val index = foods.map { food ->
        val name = food.name.lowercase()
        Indexed(food, name, words(name), words(food.aka.lowercase()))
    }

    val size: Int get() = index.size

    /**
     * Foods matching every word of [query], best first. A word matches when it starts a word of the
     * name or of the other names a food goes by, so "dal" finds "Toor dal" and "arhar" finds it too.
     */
    fun search(query: String, limit: Int = 40): List<Food> {
        val q = query.trim().lowercase()
        val terms = words(q)
        if (terms.isEmpty()) return emptyList()
        return index
            .mapNotNull { item -> score(item, q, terms)?.let { item to it } }
            .sortedWith(compareByDescending<Pair<Indexed, Int>> { it.second }.thenBy { it.first.name.length })
            .take(limit)
            .map { it.first.food }
    }

    private fun score(item: Indexed, query: String, terms: List<String>): Int? {
        var score = 0
        for (term in terms) {
            score += when {
                item.words.any { it == term } -> 12
                item.words.any { it.startsWith(term) } -> 8
                item.akaWords.any { it.startsWith(term) } -> 5
                term.length >= 4 && item.name.contains(term) -> 2
                else -> return null
            }
        }
        if (item.name == query) score += 40
        else if (item.name.startsWith(query)) score += 20
        // The first word carries the meaning: "Rice, white, cooked" is rice, "Rice flour" less so.
        if (item.words.firstOrNull()?.startsWith(terms.first()) == true) score += 6
        // Everyday Indian dishes first for an Indian kitchen; the USDA table is the long tail.
        if (item.food.source == "IN") score += 4
        // A household serving marks something eaten as is, rather than an ingredient like flour.
        if (item.food.servingLabel.isNotEmpty()) score += 3
        return score
    }

    companion object {
        private val Separators = Regex("[^\\p{L}\\p{N}]+")

        private fun words(text: String): List<String> = text.split(Separators).filter { it.isNotEmpty() }

        /**
         * Reads the bundled TSV. Columns are found by their header names, so the build script can add
         * one without breaking older code. Rows that do not parse are skipped, not fatal.
         */
        fun parse(lines: Sequence<String>): List<Food> {
            val iterator = lines.iterator()
            if (!iterator.hasNext()) return emptyList()
            val header = iterator.next().split('\t').map { it.trim().lowercase() }
            fun column(name: String) = header.indexOf(name)
            val name = column("name")
            val aka = column("aka")
            val kcal = column("kcal")
            val protein = column("protein")
            val carbs = column("carbs")
            val fat = column("fat")
            val serving = column("serving")
            val servingG = column("serving_g")
            val source = column("source")
            if (name < 0 || kcal < 0) return emptyList()

            val foods = ArrayList<Food>(8192)
            while (iterator.hasNext()) {
                val cells = iterator.next().split('\t')
                fun cell(index: Int) = if (index < 0) "" else cells.getOrNull(index)?.trim().orEmpty()
                fun number(index: Int) = cell(index).toDoubleOrNull() ?: 0.0
                val foodName = cell(name)
                val energy = cell(kcal).toDoubleOrNull() ?: continue
                if (foodName.isEmpty()) continue
                foods += Food(
                    name = foodName,
                    kcal100 = energy,
                    protein100 = number(protein),
                    carbs100 = number(carbs),
                    fat100 = number(fat),
                    servingLabel = cell(serving),
                    servingG = cell(servingG).toDoubleOrNull()?.takeIf { it > 0 } ?: 100.0,
                    source = cell(source).ifEmpty { "US" },
                    aka = cell(aka),
                )
            }
            return foods
        }
    }
}
