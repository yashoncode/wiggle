package io.wiggle.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.wiggle.data.db.Meal
import io.wiggle.ui.FoodSection
import io.wiggle.ui.SettingsButton
import io.wiggle.ui.components.MinTouch
import io.wiggle.ui.components.PrimaryButton
import io.wiggle.ui.components.ScreenHeader
import io.wiggle.ui.components.SegmentedControl
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.theme.WiggleTheme
import io.wiggle.ui.water.WaterScreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The Food tab: calories, water and tablets under one header, switched by a segmented control. */
@Composable
fun FoodTab(
    section: FoodSection,
    onSection: (FoodSection) -> Unit,
    onAddFood: (Meal) -> Unit,
    onOpenReminders: () -> Unit,
    onWaterGoalReached: () -> Unit,
    onEditWaterGoal: () -> Unit,
    onCustomWater: () -> Unit,
) {
    val colors = WiggleTheme.colors
    val tablets: TabletsViewModel = hiltViewModel()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(
            eyebrow = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM")),
            title = "Food",
        ) {
            when (section) {
                FoodSection.Calories -> PrimaryButton(
                    text = "Add food",
                    onClick = { onAddFood(CaloriesViewModel.mealForNow()) },
                    icon = Lucide.Plus,
                    height = MinTouch,
                    color = colors.ink,
                    contentColor = colors.background,
                )
                FoodSection.Tablets -> PrimaryButton(
                    text = "Add tablet",
                    onClick = tablets::add,
                    icon = Lucide.Plus,
                    height = MinTouch,
                    color = colors.ink,
                    contentColor = colors.background,
                )
                FoodSection.Water -> Unit
            }
            SettingsButton()
        }

        SegmentedControl(
            options = FoodSection.entries.toList(),
            selected = section,
            onSelect = onSection,
            label = { it.label },
            modifier = Modifier.fillMaxWidth(),
        )

        when (section) {
            FoodSection.Calories -> CaloriesScreen(onAddFood = onAddFood)
            FoodSection.Water -> WaterScreen(
                onGoalReached = onWaterGoalReached,
                onEditGoal = onEditWaterGoal,
                onCustomAmount = onCustomWater,
            )
            FoodSection.Tablets -> TabletsScreen(onOpenReminders = onOpenReminders, viewModel = tablets)
        }
    }
}
