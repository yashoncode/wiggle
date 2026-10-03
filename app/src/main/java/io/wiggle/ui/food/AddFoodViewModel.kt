package io.wiggle.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.wiggle.data.FoodDatabase
import io.wiggle.data.OpenFoodFacts
import io.wiggle.data.WiggleRepository
import io.wiggle.data.db.Meal
import io.wiggle.data.toFood
import io.wiggle.domain.Food
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FoodList(val label: String) { Recent("Recent"), Favorites("Favorites"), Mine("My foods") }

/** One food picked in the sheet, and how many servings of it. */
data class Picked(val food: Food, val servings: Double)

data class AddFoodState(
    val visible: Boolean = false,
    val meal: Meal = Meal.Breakfast,
    val query: String = "",
    val list: FoodList = FoodList.Recent,
    /** Matches from the bundled table, instant. */
    val matches: List<Food> = emptyList(),
    /** Packaged foods from Open Food Facts, which arrive a moment later. */
    val online: List<Food> = emptyList(),
    val searchingOnline: Boolean = false,
    /** Picked foods by key, in the order they were picked. */
    val picked: Map<String, Picked> = emptyMap(),
    val creating: Boolean = false,
    /** A line to show after a scan: "Not found", or which product it was. */
    val scanMessage: String? = null,
) {
    val pickedKcal: Int get() = picked.values.sumOf { it.food.kcal(it.servings) }
}

/** The add-food sheet's state, a search across the bundled table and Open Food Facts. */
@HiltViewModel
class AddFoodViewModel @Inject constructor(
    private val repository: WiggleRepository,
    private val foodDatabase: FoodDatabase,
    private val openFoodFacts: OpenFoodFacts,
) : ViewModel() {

    private val _state = MutableStateFlow(AddFoodState())

    data class Lists(val recent: List<Food>, val favorites: List<Food>, val mine: List<Food>, val favoriteKeys: Set<String>)

    val lists: StateFlow<Lists> = combine(repository.recentFoods, repository.savedFoods) { recent, saved ->
        Lists(
            recent = recent,
            favorites = saved.filter { it.favorite }.map { it.toFood() },
            mine = saved.filter { it.custom }.map { it.toFood() },
            favoriteKeys = saved.filter { it.favorite }.mapTo(HashSet()) { it.toFood().key },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Lists(emptyList(), emptyList(), emptyList(), emptySet()))

    val state: StateFlow<AddFoodState> = _state.asStateFlow()

    private var onlineJob: Job? = null

    fun open(meal: Meal) {
        _state.value = AddFoodState(visible = true, meal = meal)
        // Warm the table while the sheet animates in, so the first keystroke is not the slow one.
        viewModelScope.launch { foodDatabase.search() }
    }

    fun close() = _state.update { it.copy(visible = false) }

    fun nextMeal() = _state.update { it.copy(meal = Meal.entries[(it.meal.ordinal + 1) % Meal.entries.size]) }

    fun setList(list: FoodList) = _state.update { it.copy(list = list, creating = false) }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query, scanMessage = null) }
        onlineJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(matches = emptyList(), online = emptyList(), searchingOnline = false) }
            return
        }
        onlineJob = viewModelScope.launch {
            val matches = foodDatabase.search().search(query)
            _state.update { if (it.query == query) it.copy(matches = matches) else it }
            if (query.trim().length < 3) return@launch
            // Wait for typing to pause, so Open Food Facts gets one request per word, not per letter.
            delay(700)
            _state.update { it.copy(searchingOnline = true) }
            val online = openFoodFacts.search(query.trim())
            _state.update {
                if (it.query == query) it.copy(online = online, searchingOnline = false) else it
            }
        }
    }

    /** Picks a food at one serving, or puts it back. */
    fun toggle(food: Food) = _state.update {
        val picked = it.picked.toMutableMap()
        if (picked.remove(food.key) == null) picked[food.key] = Picked(food, 1.0)
        it.copy(picked = picked)
    }

    /** Half a serving per step, from a half up to twenty. */
    fun step(food: Food, up: Boolean) = _state.update {
        val current = it.picked[food.key] ?: return@update it
        val next = (current.servings + if (up) 0.5 else -0.5).coerceIn(0.5, 20.0)
        it.copy(picked = it.picked + (food.key to current.copy(servings = next)))
    }

    fun toggleFavorite(food: Food) = viewModelScope.launch {
        val profileId = repository.settings.first().activeProfileId
        if (profileId != 0L) repository.toggleFavorite(profileId, food)
    }

    fun startCreate() = _state.update { it.copy(list = FoodList.Mine, creating = true) }

    fun cancelCreate() = _state.update { it.copy(creating = false) }

    /**
     * Saves a food typed in by hand, per serving as the label on a packet reads, and picks it.
     * Stored per 100 g like every other food, so a serving size can be changed later.
     */
    fun createFood(name: String, servingLabel: String, servingG: Double, kcal: Double, protein: Double, carbs: Double, fat: Double) {
        if (name.isBlank() || servingG <= 0 || kcal < 0) return
        val per100 = 100 / servingG
        val food = Food(
            name = name.trim(),
            kcal100 = kcal * per100,
            protein100 = protein * per100,
            carbs100 = carbs * per100,
            fat100 = fat * per100,
            servingLabel = servingLabel.trim(),
            servingG = servingG,
            source = "MY",
        )
        viewModelScope.launch {
            val profileId = repository.settings.first().activeProfileId
            if (profileId == 0L) return@launch
            repository.saveCustomFood(profileId, food)
            _state.update { it.copy(creating = false, picked = it.picked + (food.key to Picked(food, 1.0))) }
        }
    }

    /** A scanned barcode: looked up, and picked when Open Food Facts knows it. */
    fun onBarcode(barcode: String) {
        _state.update { it.copy(scanMessage = "Looking up $barcode…") }
        viewModelScope.launch {
            val food = openFoodFacts.product(barcode)
            _state.update {
                if (food == null) {
                    it.copy(scanMessage = "Not in Open Food Facts yet. Add it under My foods.")
                } else {
                    it.copy(
                        scanMessage = "Found ${food.name}",
                        online = listOf(food) + it.online.filter { other -> other.key != food.key },
                        picked = it.picked + (food.key to Picked(food, 1.0)),
                    )
                }
            }
        }
    }

    fun scanFailed(message: String) = _state.update { it.copy(scanMessage = message) }

    /** Logs everything picked to the chosen meal and closes the sheet. */
    fun save(onSaved: () -> Unit = {}) = viewModelScope.launch {
        val current = _state.value
        val profileId = repository.settings.first().activeProfileId
        if (profileId == 0L || current.picked.isEmpty()) return@launch
        repository.addFoods(profileId, current.meal, current.picked.values.map { it.food to it.servings })
        _state.update { it.copy(visible = false) }
        onSaved()
    }
}
