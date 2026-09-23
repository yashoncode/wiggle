package io.wiggle.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wiggle.data.db.Sex
import io.wiggle.domain.LengthUnit
import io.wiggle.domain.Stats
import io.wiggle.domain.WeightUnit
import io.wiggle.domain.format
import io.wiggle.ui.components.GlassButton
import io.wiggle.ui.components.PrimaryButton
import io.wiggle.ui.components.RollingText
import io.wiggle.ui.components.RulerWheel
import io.wiggle.ui.components.SegmentedControl
import io.wiggle.ui.glass.GlassCard
import io.wiggle.ui.glass.glass
import io.wiggle.ui.icons.Icon
import io.wiggle.ui.icons.Lucide
import io.wiggle.ui.motion.Motion
import io.wiggle.ui.theme.WiggleTheme

/**
 * First run: name, body, goal, first weigh-in — each one skippable, and all of it editable later
 * in Settings. The last step is the only one that writes anything, so backing out changes nothing.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = WiggleTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepDots(current = state.stepIndex, total = OnboardingStep.entries.size)
            GlassButton(
                onClick = { viewModel.skip(onDone) },
                height = 36.dp,
                horizontalPadding = 14.dp,
                contentDescription = "Skip setup",
            ) {
                Text("Skip", style = MaterialTheme.typography.labelLarge, color = colors.inkMuted)
            }
        }

        Spacer(Modifier.height(24.dp))

        AnimatedContent(
            targetState = state.step,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val width = { full: Int -> if (forward) full else -full }
                (slideInHorizontally(Motion.snappy(), width) + fadeIn())
                    .togetherWith(slideOutHorizontally(Motion.snappy()) { -width(it) } + fadeOut())
            },
            label = "onboardingStep",
        ) { step ->
            Column(Modifier.fillMaxWidth()) {
                when (step) {
                    OnboardingStep.Welcome -> WelcomeStep()
                    OnboardingStep.Name -> NameStep(state, viewModel)
                    OnboardingStep.Body -> BodyStep(state, viewModel)
                    OnboardingStep.Goal -> GoalStep(state, viewModel)
                    OnboardingStep.FirstWeight -> FirstWeightStep(state, viewModel)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.stepIndex > 0) {
                GlassButton(
                    onClick = viewModel::back,
                    horizontalPadding = 0.dp,
                    modifier = Modifier.size(56.dp),
                    contentDescription = "Back",
                ) {
                    Icon(Lucide.ChevronLeft, size = 20.dp, tint = colors.ink, strokeWidth = 2.4f)
                }
            }
            PrimaryButton(
                text = when (state.step) {
                    OnboardingStep.Welcome -> "Get started"
                    OnboardingStep.FirstWeight -> "Finish"
                    else -> "Continue"
                },
                onClick = { viewModel.next(onDone) },
                modifier = Modifier.weight(1f),
                enabled = state.canContinue && !state.saving,
                icon = if (state.step == OnboardingStep.FirstWeight) Lucide.Check else Lucide.ArrowRight,
            )
        }
    }
}

@Composable
private fun StepDots(current: Int, total: Int) {
    val colors = WiggleTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { index ->
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (index == current) 22.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (index <= current) colors.weight else colors.track)
            )
        }
    }
}

@Composable
private fun StepHeading(title: String, subtitle: String) {
    val colors = WiggleTheme.colors
    Text(title, style = MaterialTheme.typography.displaySmall, color = colors.ink)
    Spacer(Modifier.height(6.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = colors.inkMuted)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun ColumnScope.WelcomeStep() {
    val colors = WiggleTheme.colors
    Spacer(Modifier.height(20.dp))
    StepHeading(
        title = "Wiggle",
        subtitle = "Weight, body measurements and water, in one quiet place.",
    )
    GlassCard(Modifier.fillMaxWidth()) {
        listOf(
            Lucide.Scale to "Weigh in daily. The chart smooths out the noise.",
            Lucide.Ruler to "Tape measurements when the scale stalls.",
            Lucide.Droplet to "Water, with a nudge when you are behind.",
            Lucide.Users to "Several people, each with their own history.",
        ).forEachIndexed { index, (icon, line) ->
            if (index > 0) Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, size = 18.dp, tint = colors.weight, strokeWidth = 2f)
                Spacer(Modifier.width(12.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
            }
        }
    }
}

@Composable
private fun ColumnScope.NameStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    val colors = WiggleTheme.colors
    StepHeading("Who is this for?", "The name shows on the home screen and in the switcher.")
    BasicTextField(
        value = state.name,
        onValueChange = viewModel::setName,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
        ),
        textStyle = MaterialTheme.typography.headlineSmall.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.weight),
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = RoundedCornerShape(18.dp), blurRadius = 18.dp, elevation = 6.dp)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        decorationBox = { inner ->
            if (state.name.isEmpty()) {
                Text("Your name", style = MaterialTheme.typography.headlineSmall, color = colors.inkFaint)
            }
            inner()
        },
    )
    Spacer(Modifier.height(20.dp))
    Text("Units", style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SegmentedControl(
            options = WeightUnit.entries.toList(),
            selected = state.weightUnit,
            onSelect = viewModel::setWeightUnit,
            label = { it.label },
            modifier = Modifier.weight(1f),
        )
        SegmentedControl(
            options = LengthUnit.entries.toList(),
            selected = state.lengthUnit,
            onSelect = viewModel::setLengthUnit,
            label = { it.label },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ColumnScope.BodyStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    val colors = WiggleTheme.colors
    StepHeading("How tall are you?", "Height turns weight into BMI, and the tape into a body fat estimate.")
    ValueLine(
        value = state.lengthUnit.fromCm(state.heightCm)
            .format(if (state.lengthUnit == LengthUnit.Cm) 0 else 1),
        unit = state.lengthUnit.label,
    )
    RulerWheel(
        value = state.lengthUnit.fromCm(state.heightCm),
        onValueChange = { viewModel.setHeight(state.lengthUnit.toCm(it)) },
        range = state.lengthUnit.fromCm(90.0)..state.lengthUnit.fromCm(230.0),
        step = 0.5,
        accent = colors.weight,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        "Sex, used only for the body fat estimate",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.inkMuted,
    )
    Spacer(Modifier.height(8.dp))
    SegmentedControl(
        options = listOf(Sex.Female, Sex.Male, Sex.Unspecified),
        selected = state.sex,
        onSelect = viewModel::setSex,
        label = { if (it == Sex.Unspecified) "Prefer not to" else it.name },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.GoalStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    val colors = WiggleTheme.colors
    StepHeading("A goal weight?", "Optional. It drives the progress bar and the projected date.")
    if (state.goalKg == null) {
        GlassButton(
            onClick = { viewModel.setGoal(70.0) },
            modifier = Modifier.fillMaxWidth(),
            contentDescription = "Set a goal weight",
        ) {
            Icon(Lucide.Target, size = 18.dp, tint = colors.goal, strokeWidth = 2.2f)
            Text("Set a goal", style = MaterialTheme.typography.titleSmall, color = colors.ink)
        }
    } else {
        ValueLine(state.weightUnit.fromKg(state.goalKg).format(), state.weightUnit.label)
        RulerWheel(
            value = state.weightUnit.fromKg(state.goalKg),
            onValueChange = { viewModel.setGoal(state.weightUnit.toKg(it)) },
            range = state.weightUnit.fromKg(30.0)..state.weightUnit.fromKg(250.0),
            accent = colors.goal,
        )
        Spacer(Modifier.height(14.dp))
        GlassButton(
            onClick = { viewModel.setGoal(null) },
            modifier = Modifier.fillMaxWidth(),
            height = 40.dp,
            contentDescription = "Remove the goal",
        ) {
            Text("No goal for now", style = MaterialTheme.typography.labelLarge, color = colors.inkMuted)
        }
    }
}

@Composable
private fun ColumnScope.FirstWeightStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    val colors = WiggleTheme.colors
    StepHeading("First weigh-in", "You can skip this and log later — nothing here is locked in.")
    val weight = state.currentKg
    if (weight == null) {
        GlassButton(
            onClick = { viewModel.setCurrent(70.0) },
            modifier = Modifier.fillMaxWidth(),
            contentDescription = "Log a first weight",
        ) {
            Icon(Lucide.Plus, size = 18.dp, tint = colors.weight, strokeWidth = 2.4f)
            Text("Log my weight now", style = MaterialTheme.typography.titleSmall, color = colors.ink)
        }
    } else {
        ValueLine(state.weightUnit.fromKg(weight).format(), state.weightUnit.label)
        RulerWheel(
            value = state.weightUnit.fromKg(weight),
            onValueChange = { viewModel.setCurrent(state.weightUnit.toKg(it)) },
            range = state.weightUnit.fromKg(20.0)..state.weightUnit.fromKg(400.0),
            accent = colors.weight,
        )
        Spacer(Modifier.height(16.dp))
        Stats.bmi(weight, state.heightCm)?.let { bmi ->
            Text(
                "BMI ${bmi.format(1)} · ${Stats.bmiCategory(bmi).label}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkMuted,
            )
        }
    }
}

/** The big number a step is about, with its unit alongside. */
@Composable
private fun ValueLine(value: String, unit: String) {
    val colors = WiggleTheme.colors
    Row(verticalAlignment = Alignment.Bottom) {
        RollingText(
            text = value,
            style = MaterialTheme.typography.displayMedium,
            color = colors.ink,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            unit,
            style = MaterialTheme.typography.titleMedium,
            color = colors.inkMuted,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    Spacer(Modifier.height(10.dp))
}
