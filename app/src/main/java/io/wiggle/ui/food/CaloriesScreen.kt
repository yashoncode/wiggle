package io.wiggle.ui.food

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wiggle.data.db.FoodEntryEntity
import io.wiggle.data.db.Meal
import io.wiggle.data.kcal
import io.wiggle.domain.format
import io.wiggle.domain.portionText
import io.wiggle.ui.components.CardRow
import io.wiggle.ui.components.GlassIconButton
import io.wiggle.ui.components.ProgressBar
import io.wiggle.ui.components.ProgressRing
import io.wiggle.ui.components.SwipeToDelete
import io.wiggle.ui.glass.GlassCard
import io.wiggle.ui.glass.GlassDefaults
import io.wiggle.ui.glass.cardEntrance
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.theme.WiggleTheme
import io.wiggle.ui.today.groupedSigned

/** Food › Calories: what is left today, where it went, and a plus on every meal. */
@Composable
fun CaloriesScreen(
    onAddFood: (Meal) -> Unit,
    viewModel: CaloriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SummaryCard(state, Modifier.cardEntrance(0))
        MealsCard(state, onAddFood, viewModel::delete, Modifier.cardEntrance(1))
        Text(
            "Calories are estimates from food tables and step counts.",
            style = MaterialTheme.typography.bodySmall,
            color = WiggleTheme.colors.inkFaint,
        )
    }
}

@Composable
private fun SummaryCard(state: CaloriesUiState, modifier: Modifier = Modifier) {
    val colors = WiggleTheme.colors
    val budget = state.budget
    GlassCard(modifier.fillMaxWidth(), contentPadding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                ProgressRing(
                    progress = budget?.progress ?: 0f,
                    color = colors.food,
                    trackColor = colors.track,
                    strokeWidth = 12.dp,
                    modifier = Modifier.size(132.dp),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        budget?.let { groupedSigned(it.left) } ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.ink,
                    )
                    Text(
                        if ((budget?.left ?: 0) < 0) "kcal over" else "kcal left",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.inkMuted,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val goals = state.macroGoals
                MacroBar("Protein", state.proteinG, goals?.proteinG, colors.protein)
                MacroBar("Carbs", state.carbsG, goals?.carbsG, colors.carbs)
                MacroBar("Fat", state.fatG, goals?.fatG, colors.fat)
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            BudgetFigure("Goal", budget?.goal?.let(::groupedSigned) ?: "—", colors.ink, Modifier.weight(1f))
            BudgetFigure(
                "Food",
                budget?.eaten?.let { if (it == 0) "0" else "−" + groupedSigned(it) } ?: "—",
                colors.ink,
                Modifier.weight(1f),
            )
            BudgetFigure("Steps", budget?.walked?.let { "+" + groupedSigned(it) } ?: "—", colors.stepsSoft, Modifier.weight(1f))
            BudgetFigure("Left", budget?.left?.let(::groupedSigned) ?: "—", colors.foodSoft, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroBar(label: String, grams: Double, goal: Int?, color: Color) {
    val colors = WiggleTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.ink)
            Text(
                if (goal == null) "${grams.format(0)} g" else "${grams.format(0)} / $goal g",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted,
            )
        }
        ProgressBar(
            progress = if (goal == null || goal == 0) 0f else (grams / goal).toFloat(),
            color = color,
            trackColor = colors.track,
            height = 6.dp,
        )
    }
}

@Composable
private fun BudgetFigure(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = WiggleTheme.colors.inkMuted)
    }
}

@Composable
private fun MealsCard(
    state: CaloriesUiState,
    onAdd: (Meal) -> Unit,
    onDelete: (FoodEntryEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WiggleTheme.colors
    var open by remember { mutableStateOf<Meal?>(null) }
    GlassCard(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GlassDefaults.SmallRadius),
        contentPadding = 16.dp,
    ) {
        state.meals.forEachIndexed { index, group ->
            val expanded = open == group.meal && group.entries.isNotEmpty()
            CardRow(
                modifier = Modifier.semantics {
                    if (group.entries.isNotEmpty()) stateDescription = if (expanded) "Expanded" else "Collapsed"
                },
                showDivider = index > 0,
                onClick = { open = if (open == group.meal) null else group.meal },
            ) {
                Column(Modifier.weight(1f)) {
                    Text(mealLabel(group.meal), style = MaterialTheme.typography.titleSmall, color = colors.ink)
                    Text(
                        when {
                            group.entries.isNotEmpty() -> group.entries.joinToString(", ") { it.name.substringBefore(',') }
                            group.meal != Meal.Snacks && state.perMealLeft != null ->
                                "Not logged · ~${groupedSigned(state.perMealLeft)} kcal fits"
                            else -> "Not logged"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    if (group.entries.isEmpty()) "—" else groupedSigned(group.kcal),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.ink,
                )
                GlassIconButton(
                    icon = Lucide.Plus,
                    contentDescription = "Add food to ${mealLabel(group.meal)}",
                    onClick = { onAdd(group.meal) },
                    tint = colors.foodSoft,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(start = 6.dp, bottom = 6.dp)) {
                    group.entries.forEach { entry ->
                        SwipeToDelete(onDelete = { onDelete(entry) }) {
                            Row(
                                Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name, style = MaterialTheme.typography.bodyMedium, color = colors.ink, maxLines = 2)
                                    Text(
                                        portionText(entry.servings, entry.servingLabel, entry.servingG),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.inkFaint,
                                    )
                                }
                                Text(
                                    "${groupedSigned(entry.kcal)} kcal",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.inkMuted,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    }
                    Text(
                        "Swipe left on a food to remove it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkFaint,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

fun mealLabel(meal: Meal): String = when (meal) {
    Meal.Breakfast -> "Breakfast"
    Meal.Lunch -> "Lunch"
    Meal.Snacks -> "Snacks"
    Meal.Dinner -> "Dinner"
}
