package io.wiggle.ui.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.wiggle.domain.Food
import io.wiggle.domain.format
import io.wiggle.domain.portionText
import io.wiggle.ui.components.GlassButton
import io.wiggle.ui.components.GlassIconButton
import io.wiggle.ui.components.MinTouch
import io.wiggle.ui.components.PrimaryButton
import io.wiggle.ui.components.SegmentedControl
import io.wiggle.ui.glass.GlassSheet
import io.wiggle.ui.glass.glass
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.theme.WiggleTheme
import io.wiggle.ui.today.groupedSigned

/**
 * Add food: search the bundled table and Open Food Facts, scan a packet, or pick from recent,
 * starred and your own foods. Pick several, adjust servings, and they go in together.
 */
@Composable
fun BoxScope.AddFoodSheet(
    onSaved: () -> Unit,
    viewModel: AddFoodViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val colors = WiggleTheme.colors
    val context = LocalContext.current

    fun scan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
            )
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode -> barcode.rawValue?.let(viewModel::onBarcode) }
            .addOnFailureListener { viewModel.scanFailed("The scanner needs Google Play services, which this phone does not have.") }
    }

    GlassSheet(visible = state.visible, onDismiss = viewModel::close, label = "Add food") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .defaultMinSize(minWidth = 64.dp, minHeight = MinTouch)
                    .clickable(role = Role.Button, onClick = viewModel::close),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("Cancel", style = MaterialTheme.typography.titleMedium, color = colors.foodSoft)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                GlassButton(
                    onClick = viewModel::nextMeal,
                    height = 38.dp,
                    horizontalPadding = 14.dp,
                    contentDescription = "Change meal",
                ) {
                    Text("Add to ${mealLabel(state.meal)}", style = MaterialTheme.typography.labelLarge, color = colors.ink)
                    Icon(Lucide.ChevronDown, size = 14.dp, tint = colors.ink, strokeWidth = 2.6f)
                }
            }
            Spacer(Modifier.width(64.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .height(MinTouch)
                    .glass(shape = RoundedCornerShape(16.dp), blurRadius = 18.dp, elevation = 4.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Lucide.Search, size = 18.dp, tint = colors.inkMuted, strokeWidth = 2.2f)
                BasicTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.food),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (state.query.isEmpty()) {
                            Text("Search dal, dosa, eggs…", style = MaterialTheme.typography.bodyLarge, color = colors.inkFaint)
                        }
                        inner()
                    },
                )
                if (state.query.isNotEmpty()) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).clickable(onClickLabel = "Clear search") { viewModel.setQuery("") },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Lucide.X, size = 16.dp, tint = colors.inkMuted, strokeWidth = 2.4f) }
                }
            }
            GlassIconButton(icon = Lucide.Barcode, contentDescription = "Scan a barcode", onClick = ::scan)
        }

        state.scanMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
        }

        if (state.query.isBlank()) {
            SegmentedControl(
                options = FoodList.entries.toList(),
                selected = state.list,
                onSelect = viewModel::setList,
                label = { it.label },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.query.isNotBlank()) {
                if (state.matches.isEmpty() && state.online.isEmpty() && !state.searchingOnline) {
                    Hint("Nothing in the food table by that name. Open Food Facts is searched once you pause.")
                }
                FoodRows(state.matches, state, lists.favoriteKeys, viewModel)
                if (state.searchingOnline || state.online.isNotEmpty()) {
                    Text(
                        if (state.searchingOnline) "Searching packaged foods…" else "Packaged · Open Food Facts",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.inkMuted,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                    )
                }
                FoodRows(state.online, state, lists.favoriteKeys, viewModel)
            } else when (state.list) {
                FoodList.Recent -> {
                    if (lists.recent.isEmpty()) Hint("Foods you log show up here, ready to add again.")
                    FoodRows(lists.recent, state, lists.favoriteKeys, viewModel)
                }
                FoodList.Favorites -> {
                    if (lists.favorites.isEmpty()) Hint("Star a food after picking it to keep it here.")
                    FoodRows(lists.favorites, state, lists.favoriteKeys, viewModel)
                }
                FoodList.Mine -> {
                    if (state.creating) {
                        CreateFoodForm(onSave = viewModel::createFood, onCancel = viewModel::cancelCreate)
                    } else {
                        GlassButton(
                            onClick = viewModel::startCreate,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            height = 44.dp,
                            contentDescription = "Create a food",
                        ) {
                            Icon(Lucide.Plus, size = 16.dp, tint = colors.foodSoft, strokeWidth = 2.6f)
                            Text("Create a food", style = MaterialTheme.typography.labelLarge, color = colors.ink)
                        }
                        if (lists.mine.isEmpty()) Hint("Home recipes and anything without a barcode go here.")
                    }
                    FoodRows(lists.mine, state, lists.favoriteKeys, viewModel)
                }
            }
        }

        val count = state.picked.size
        PrimaryButton(
            text = if (count == 0) "Pick foods to add"
            else "Add $count ${if (count == 1) "item" else "items"} · ${groupedSigned(state.pickedKcal)} kcal",
            onClick = { viewModel.save(onSaved) },
            enabled = count > 0,
            modifier = Modifier.fillMaxWidth(),
            color = colors.food,
            contentColor = if (colors.isDark) colors.background else colors.onAccent,
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = WiggleTheme.colors.inkFaint,
        modifier = Modifier.padding(vertical = 10.dp),
    )
}

@Composable
private fun FoodRows(
    foods: List<Food>,
    state: AddFoodState,
    favoriteKeys: Set<String>,
    viewModel: AddFoodViewModel,
) {
    foods.forEach { food ->
        FoodRow(
            food = food,
            picked = state.picked[food.key],
            favorite = food.key in favoriteKeys,
            onToggle = { viewModel.toggle(food) },
            onStep = { up -> viewModel.step(food, up) },
            onFavorite = { viewModel.toggleFavorite(food) },
        )
    }
}

@Composable
private fun FoodRow(
    food: Food,
    picked: Picked?,
    favorite: Boolean,
    onToggle: () -> Unit,
    onStep: (Boolean) -> Unit,
    onFavorite: () -> Unit,
) {
    val colors = WiggleTheme.colors
    val servings = picked?.servings ?: 1.0
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (picked == null) "Pick ${food.name}" else "Remove ${food.name}", onClick = onToggle)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    food.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${portionText(servings, food.servingLabel, food.servingG)} · P ${food.protein(servings).format(0)} g" +
                        if (food.source == "OFF") " · packaged" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
            }
            Text("${groupedSigned(food.kcal(servings))} kcal", style = MaterialTheme.typography.titleSmall, color = colors.ink)
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (picked != null) colors.food else colors.track),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (picked != null) Lucide.Check else Lucide.Plus,
                    size = 16.dp,
                    tint = if (picked != null) colors.background else colors.foodSoft,
                    strokeWidth = 2.8f,
                )
            }
        }
        if (picked != null) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassIconButton(Lucide.Minus, "Fewer servings", onClick = { onStep(false) }, size = 40.dp)
                Text(
                    "${if (servings == 0.5) "½" else servings.format(if (servings % 1.0 == 0.0) 0 else 1)} × ${food.servingText}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                GlassIconButton(Lucide.Plus, "More servings", onClick = { onStep(true) }, size = 40.dp)
                GlassIconButton(
                    Lucide.Star,
                    if (favorite) "Unstar ${food.name}" else "Star ${food.name}",
                    onClick = onFavorite,
                    size = 40.dp,
                    tint = if (favorite) colors.goal else colors.inkMuted,
                )
            }
        }
    }
}

/** A food typed in from its packet, per serving, the way labels print it. */
@Composable
private fun CreateFoodForm(
    onSave: (name: String, servingLabel: String, servingG: Double, kcal: Double, protein: Double, carbs: Double, fat: Double) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = WiggleTheme.colors
    var name by remember { mutableStateOf("") }
    var serving by remember { mutableStateOf("1 serving") }
    var grams by remember { mutableStateOf("100") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    fun number(text: String) = text.replace(',', '.').toDoubleOrNull()
    val valid = name.isNotBlank() && (number(grams) ?: 0.0) > 0 && number(kcal) != null

    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(name, { name = it.take(60) }, "Name, e.g. Amma's sambar", KeyboardType.Text)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(serving, { serving = it.take(30) }, "Serving", KeyboardType.Text, Modifier.weight(1.4f))
            Field(grams, { grams = it.take(6) }, "Grams", KeyboardType.Decimal, Modifier.weight(1f))
        }
        Field(kcal, { kcal = it.take(6) }, "Calories per serving", KeyboardType.Decimal)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(protein, { protein = it.take(5) }, "Protein g", KeyboardType.Decimal, Modifier.weight(1f))
            Field(carbs, { carbs = it.take(5) }, "Carbs g", KeyboardType.Decimal, Modifier.weight(1f))
            Field(fat, { fat = it.take(5) }, "Fat g", KeyboardType.Decimal, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(onClick = onCancel, modifier = Modifier.weight(1f), height = 44.dp) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.inkMuted)
            }
            PrimaryButton(
                text = "Save food",
                onClick = {
                    onSave(
                        name,
                        serving,
                        number(grams) ?: 100.0,
                        number(kcal) ?: 0.0,
                        number(protein) ?: 0.0,
                        number(carbs) ?: 0.0,
                        number(fat) ?: 0.0,
                    )
                },
                enabled = valid,
                modifier = Modifier.weight(1f),
                height = 44.dp,
                color = colors.food,
                contentColor = if (colors.isDark) colors.background else colors.onAccent,
            )
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardType,
    modifier: Modifier = Modifier,
) {
    val colors = WiggleTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = if (keyboard == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            imeAction = ImeAction.Next,
        ),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.food),
        modifier = modifier
            .fillMaxWidth()
            .glass(shape = RoundedCornerShape(14.dp), blurRadius = 18.dp, elevation = 2.dp)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.inkFaint, maxLines = 1)
            inner()
        },
    )
}
