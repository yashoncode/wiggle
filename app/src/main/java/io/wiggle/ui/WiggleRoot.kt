package io.wiggle.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.rememberHazeState
import io.wiggle.alarm.Alarms
import io.wiggle.data.db.Meal
import io.wiggle.ui.body.BodyTab
import io.wiggle.ui.body.BodyViewModel
import io.wiggle.ui.body.MeasureEditorSheet
import io.wiggle.ui.bulk.BulkAddSheet
import io.wiggle.ui.bulk.BulkAddViewModel
import io.wiggle.ui.components.GlassConfetti
import io.wiggle.ui.components.UndoSnackbar
import io.wiggle.ui.food.AddFoodSheet
import io.wiggle.ui.food.AddFoodViewModel
import io.wiggle.ui.food.CaloriesViewModel
import io.wiggle.ui.food.FoodTab
import io.wiggle.ui.food.MedicationSheet
import io.wiggle.ui.glass.AuroraBackground
import io.wiggle.ui.glass.GlassTabBar
import io.wiggle.ui.glass.TabBarHeight
import io.wiggle.ui.log.LogWeightSheet
import io.wiggle.ui.onboarding.OnboardingScreen
import io.wiggle.ui.profile.ProfileSwitcherSheet
import io.wiggle.ui.reminders.RemindersScreen
import io.wiggle.ui.settings.SettingsScreen
import io.wiggle.ui.settings.SettingsSheet
import io.wiggle.ui.settings.SettingsSheets
import io.wiggle.ui.settings.SettingsViewModel
import io.wiggle.ui.steps.StepsScreen
import io.wiggle.ui.theme.WiggleTheme
import io.wiggle.ui.today.TodayScreen
import io.wiggle.ui.trends.TrendsViewModel
import io.wiggle.ui.update.UpdateSheet
import io.wiggle.ui.update.UpdateViewModel
import io.wiggle.ui.water.WaterCustomAmountSheet
import io.wiggle.ui.water.WaterViewModel

/** A full screen that covers the tabs: Settings, or the Reminders list. */
private enum class Overlay { None, Settings, Reminders }

/**
 * [openTarget] is the screen a notification tap asked for, one of the `Alarms.OPEN_*` values.
 * It is cleared through [onOpenTargetHandled] so a configuration change does not reopen it.
 */
@Composable
fun WiggleRoot(
    openTarget: String? = null,
    onOpenTargetHandled: () -> Unit = {},
    viewModel: RootViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hazeState = rememberHazeState()

    // Steps live in Health Connect and change while Wiggle is closed, so every return re-reads them.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshSteps()
        onPauseOrDispose { }
    }

    WiggleTheme(mode = state.settings.themeMode, hazeState = hazeState, reduceMotion = state.settings.reduceMotion) {
        val scrollOffset = remember { mutableFloatStateOf(0f) }
        var tabIndex by remember { mutableIntStateOf(0) }
        var foodSection by remember { mutableStateOf(FoodSection.Calories) }
        var bodySection by remember { mutableStateOf(BodySection.Weight) }
        var overlay by remember { mutableStateOf(Overlay.None) }
        var logSheetVisible by remember { mutableStateOf(false) }
        var editingEntryId by remember { mutableStateOf<Long?>(null) }
        var profileSheetVisible by remember { mutableStateOf(false) }
        var confettiKey by remember { mutableStateOf<Any?>(null) }
        var waterCustomVisible by remember { mutableStateOf(false) }
        val bulkViewModel: BulkAddViewModel = hiltViewModel()
        val bulkState by bulkViewModel.state.collectAsStateWithLifecycle()
        val settingsViewModel: SettingsViewModel = hiltViewModel()
        val waterViewModel: WaterViewModel = hiltViewModel()
        val bodyViewModel: BodyViewModel = hiltViewModel()
        val addFood: AddFoodViewModel = hiltViewModel()
        val calories: CaloriesViewModel = hiltViewModel()

        fun go(tab: String) {
            overlay = Overlay.None
            tabIndex = WiggleTabs.indexOfFirst { it.route == tab }
        }
        fun openFood(section: FoodSection) { foodSection = section; go(Routes.Food) }
        fun openBody(section: BodySection) { bodySection = section; go(Routes.Body) }
        fun logWeight() { editingEntryId = null; logSheetVisible = true }
        fun logMeal(meal: Meal) = addFood.open(meal)

        LaunchedEffect(openTarget) {
            when (openTarget) {
                Alarms.OPEN_WEIGHT -> logWeight()
                Alarms.OPEN_BODY -> openBody(BodySection.Measurements)
                Alarms.OPEN_WATER -> openFood(FoodSection.Water)
                Alarms.OPEN_FOOD -> openFood(FoodSection.Calories)
                Alarms.OPEN_TABLETS -> openFood(FoodSection.Tablets)
                Alarms.OPEN_STEPS -> go(Routes.Steps)
                else -> return@LaunchedEffect
            }
            onOpenTargetHandled()
        }

        BackHandler(enabled = overlay != Overlay.None) { overlay = Overlay.None }

        CompositionLocalProvider(
            LocalScrollOffset provides scrollOffset,
            LocalOpenSettings provides { overlay = Overlay.Settings },
        ) {
            AuroraBackground(
                modifier = Modifier.fillMaxSize(),
                scrollProvider = { scrollOffset.floatValue },
            ) {
                if (state.ready && !state.settings.onboardingComplete && state.profiles.isEmpty()) {
                    OnboardingScreen(onDone = { tabIndex = 0 })
                    return@AuroraBackground
                }

                when (overlay) {
                    Overlay.Settings -> ScreenScaffold {
                        SettingsScreen(
                            onClose = { overlay = Overlay.None },
                            onSwitchPerson = { profileSheetVisible = true },
                            onBulkAdd = { bulkViewModel.open() },
                            onOpenReminders = { overlay = Overlay.Reminders },
                            onOpenSteps = { go(Routes.Steps) },
                        )
                    }
                    Overlay.Reminders -> ScreenScaffold { RemindersScreen(onBack = { overlay = Overlay.None }) }
                    Overlay.None -> when (WiggleTabs[tabIndex].route) {
                        Routes.Food -> ScreenScaffold {
                            FoodTab(
                                section = foodSection,
                                onSection = { foodSection = it },
                                onAddFood = ::logMeal,
                                onOpenReminders = { overlay = Overlay.Reminders },
                                onWaterGoalReached = { confettiKey = System.currentTimeMillis() },
                                onEditWaterGoal = { settingsViewModel.openSheet(SettingsSheet.WaterGoal) },
                                onCustomWater = { waterCustomVisible = true },
                            )
                        }
                        Routes.Steps -> ScreenScaffold { StepsScreen() }
                        Routes.Body -> ScreenScaffold {
                            BodyTab(
                                section = bodySection,
                                onSection = { bodySection = it },
                                onLogWeight = ::logWeight,
                                onEditEntry = { editingEntryId = it; logSheetVisible = true },
                                onBulkAdd = { bulkViewModel.open() },
                                onOpenReminders = { overlay = Overlay.Reminders },
                            )
                        }
                        else -> ScreenScaffold {
                            TodayScreen(
                                onOpenReminders = { overlay = Overlay.Reminders },
                                onOpenWeight = { openBody(BodySection.Weight) },
                                onOpenCalories = { openFood(FoodSection.Calories) },
                                onOpenSteps = { go(Routes.Steps) },
                                onOpenWater = { openFood(FoodSection.Water) },
                                onLogWeight = ::logWeight,
                                onLogMeal = { logMeal(CaloriesViewModel.mealForNow()) },
                                onLogMeasure = {
                                    openBody(BodySection.Measurements)
                                    bodyViewModel.openEditor()
                                },
                            )
                        }
                    }
                }

                if (overlay == Overlay.None) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 8.dp),
                    ) {
                        GlassTabBar(
                            tabs = WiggleTabs,
                            selectedIndex = tabIndex,
                            onSelect = { tabIndex = it },
                        )
                    }
                }

                LogWeightSheet(
                    visible = logSheetVisible,
                    entryId = editingEntryId,
                    onDismiss = { logSheetVisible = false; editingEntryId = null },
                    onSaved = { lowest ->
                        logSheetVisible = false
                        editingEntryId = null
                        if (lowest) confettiKey = System.currentTimeMillis()
                    },
                    onAddMeasurements = {
                        logSheetVisible = false
                        openBody(BodySection.Measurements)
                    },
                )

                val bodyEditor by bodyViewModel.editor.collectAsStateWithLifecycle()
                MeasureEditorSheet(
                    editor = bodyEditor,
                    unit = state.settings.lengthUnit,
                    onDismiss = bodyViewModel::closeEditor,
                    onSetValue = bodyViewModel::setValue,
                    onSkip = bodyViewModel::skipCurrent,
                    onNext = bodyViewModel::nextStep,
                    onPrevious = bodyViewModel::previousStep,
                    onGoToStep = bodyViewModel::goToStep,
                    onSave = { bodyViewModel.saveSession { confettiKey = null } },
                    onMeasuredAt = bodyViewModel::setMeasuredAt,
                    onStartAddType = bodyViewModel::startAddingType,
                    onCancelAddType = bodyViewModel::cancelAddingType,
                    onNewTypeName = bodyViewModel::setNewTypeName,
                    onNewTypeAnchor = bodyViewModel::setNewTypeAnchor,
                    onConfirmAddType = { bodyViewModel.confirmAddType() },
                    onDeleteCustomType = { bodyViewModel.deleteCustomType(it) },
                )

                AddFoodSheet(onSaved = { openFood(FoodSection.Calories) }, viewModel = addFood)

                MedicationSheet()

                ProfileSwitcherSheet(
                    visible = profileSheetVisible,
                    profiles = state.profiles,
                    activeId = state.settings.activeProfileId,
                    unit = state.settings.weightUnit,
                    onSelect = {
                        viewModel.selectProfile(it)
                        profileSheetVisible = false
                    },
                    onAdd = {
                        profileSheetVisible = false
                        settingsViewModel.openSheet(SettingsSheet.AddPerson)
                    },
                    onDismiss = { profileSheetVisible = false },
                )

                // Undo for a swiped-away weight, drink or food. The view models are activity-scoped,
                // so these are the same instances the screens deleted from.
                val trendsViewModel: TrendsViewModel = hiltViewModel()
                val deleted by trendsViewModel.lastDeleted.collectAsStateWithLifecycle()
                val deletedWater by waterViewModel.lastDeleted.collectAsStateWithLifecycle()
                val deletedFood by calories.lastDeleted.collectAsStateWithLifecycle()
                UndoSnackbar(
                    visible = deleted != null || deletedWater != null || deletedFood != null,
                    message = when {
                        deletedFood != null -> "Food removed"
                        deletedWater != null -> "Drink deleted"
                        else -> "Entry deleted"
                    },
                    onUndo = {
                        when {
                            deletedFood != null -> calories.undoDelete()
                            deletedWater != null -> waterViewModel.undoDelete()
                            else -> trendsViewModel.undoDelete()
                        }
                    },
                    onTimeout = {
                        calories.clearUndo()
                        waterViewModel.clearUndo()
                        trendsViewModel.clearUndo()
                    },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = TabBarHeight + 20.dp),
                )

                BulkAddSheet(
                    state = bulkState,
                    onDismiss = bulkViewModel::close,
                    onMode = bulkViewModel::setMode,
                    onSpan = { bulkViewModel.setSpan(it) },
                    onTyped = bulkViewModel::setTyped,
                    onClearTyped = bulkViewModel::clearTyped,
                    onSaveGrid = { bulkViewModel.saveGrid { } },
                    onPasteText = bulkViewModel::setPasteText,
                    onPasteDayFirst = bulkViewModel::setPasteDayFirst,
                    onSavePaste = { bulkViewModel.savePaste { } },
                )

                WaterCustomAmountSheet(
                    visible = waterCustomVisible,
                    unit = state.settings.volumeUnit,
                    onDismiss = { waterCustomVisible = false },
                    onAdd = { amount ->
                        waterCustomVisible = false
                        waterViewModel.add(amount) { confettiKey = System.currentTimeMillis() }
                    },
                )

                SettingsSheets(settingsViewModel)

                // The check runs when the view model is created, so simply being here is enough
                // to be offered a new release on launch.
                val updateViewModel: UpdateViewModel = hiltViewModel()
                val update by updateViewModel.state.collectAsStateWithLifecycle()
                val context = LocalContext.current
                UpdateSheet(
                    state = update,
                    onDismiss = updateViewModel::dismiss,
                    onDownload = { url ->
                        updateViewModel.dismiss()
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    },
                )

                GlassConfetti(burstKey = confettiKey, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * The common page frame: scrolls, reports its scroll to the background for parallax, and leaves
 * room under the floating tab bar.
 */
@Composable
fun BoxScope.ScreenScaffold(
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    ReportScroll(scrollState)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .statusBarsPadding()
            .padding(contentPadding)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        content()
        // Clearance for the floating tab bar plus the gesture inset.
        Spacer(Modifier.height(TabBarHeight + 44.dp))
    }
}
