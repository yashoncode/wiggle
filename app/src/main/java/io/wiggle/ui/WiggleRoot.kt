package io.wiggle.ui

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.rememberHazeState
import io.wiggle.alarm.Alarms
import io.wiggle.ui.components.GlassConfetti
import io.wiggle.ui.components.UndoSnackbar
import io.wiggle.ui.glass.AuroraBackground
import io.wiggle.ui.glass.GlassTabBar
import io.wiggle.ui.glass.TabBarHeight
import io.wiggle.ui.log.LogWeightSheet
import io.wiggle.ui.onboarding.OnboardingScreen
import io.wiggle.ui.settings.SettingsScreen
import io.wiggle.ui.settings.SettingsSheet
import io.wiggle.ui.settings.SettingsSheets
import io.wiggle.ui.settings.SettingsViewModel
import io.wiggle.ui.water.WaterCustomAmountSheet
import io.wiggle.ui.water.WaterScreen
import io.wiggle.ui.water.WaterViewModel
import io.wiggle.ui.profile.ProfileSwitcherSheet
import io.wiggle.ui.body.BodyScreen
import io.wiggle.ui.bulk.BulkAddSheet
import io.wiggle.ui.bulk.BulkAddViewModel
import io.wiggle.ui.body.BodyViewModel
import io.wiggle.ui.body.MeasureEditorSheet
import io.wiggle.ui.theme.WiggleTheme
import io.wiggle.ui.today.QuickAddSheet
import io.wiggle.ui.today.TodayScreen
import io.wiggle.ui.update.UpdateSheet
import io.wiggle.ui.update.UpdateViewModel
import io.wiggle.ui.trends.TrendsScreen
import io.wiggle.ui.trends.TrendsViewModel

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

    WiggleTheme(mode = state.settings.themeMode, hazeState = hazeState) {
        val scrollOffset = remember { mutableFloatStateOf(0f) }
        var tabIndex by remember { mutableIntStateOf(0) }
        var logSheetVisible by remember { mutableStateOf(false) }
        var editingEntryId by remember { mutableStateOf<Long?>(null) }
        var profileSheetVisible by remember { mutableStateOf(false) }
        var quickAddVisible by remember { mutableStateOf(false) }
        var confettiKey by remember { mutableStateOf<Any?>(null) }
        var waterCustomVisible by remember { mutableStateOf(false) }
        val bulkViewModel: BulkAddViewModel = hiltViewModel()
        val bulkState by bulkViewModel.state.collectAsStateWithLifecycle()
        val settingsViewModel: SettingsViewModel = hiltViewModel()
        val waterViewModel: WaterViewModel = hiltViewModel()

        LaunchedEffect(openTarget) {
            when (openTarget) {
                Alarms.OPEN_WEIGHT -> { editingEntryId = null; logSheetVisible = true }
                Alarms.OPEN_BODY -> tabIndex = 2
                Alarms.OPEN_WATER -> tabIndex = 3
                else -> return@LaunchedEffect
            }
            onOpenTargetHandled()
        }

        CompositionLocalProvider(LocalScrollOffset provides scrollOffset) {
            AuroraBackground(
                modifier = Modifier.fillMaxSize(),
                scrollProvider = { scrollOffset.floatValue },
            ) {
                if (state.ready && !state.settings.onboardingComplete && state.profiles.isEmpty()) {
                    OnboardingScreen(onDone = { tabIndex = 0 })
                    return@AuroraBackground
                }

                when (WiggleTabs[tabIndex].route) {
                    Routes.Body -> ScreenScaffold { BodyScreen() }
                    Routes.Trends -> ScreenScaffold {
                        TrendsScreen(
                            onEditEntry = { editingEntryId = it; logSheetVisible = true },
                            onBulkAdd = { bulkViewModel.open() },
                            onLogWeight = { editingEntryId = null; logSheetVisible = true },
                        )
                    }
                    Routes.Today -> ScreenScaffold {
                        TodayScreen(
                            onQuickAdd = { quickAddVisible = true },
                            onOpenProfiles = { profileSheetVisible = true },
                            onOpenWater = { tabIndex = 3 },
                            onOpenTrends = { tabIndex = 1 },
                            onOpenReminders = { tabIndex = 4 },
                        )
                    }
                    Routes.Water -> ScreenScaffold {
                        WaterScreen(
                            onGoalReached = { confettiKey = System.currentTimeMillis() },
                            onEditGoal = {
                                tabIndex = 4
                                settingsViewModel.openSheet(SettingsSheet.WaterGoal)
                            },
                            onCustomAmount = { waterCustomVisible = true },
                        )
                    }
                    else -> ScreenScaffold {
                        SettingsScreen(
                            onSwitchPerson = { profileSheetVisible = true },
                            onBulkAdd = { bulkViewModel.open() },
                        )
                    }
                }

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

                val bodyViewModel: BodyViewModel = hiltViewModel()

                QuickAddSheet(
                    visible = quickAddVisible,
                    onDismiss = { quickAddVisible = false },
                    onLogWeight = {
                        quickAddVisible = false
                        editingEntryId = null
                        logSheetVisible = true
                    },
                    onLogBody = {
                        quickAddVisible = false
                        tabIndex = 2
                        bodyViewModel.openEditor()
                    },
                    onLogWater = {
                        quickAddVisible = false
                        tabIndex = 3
                    },
                )

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
                        tabIndex = 2
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
                        tabIndex = 4
                        settingsViewModel.openSheet(SettingsSheet.AddPerson)
                    },
                    onDismiss = { profileSheetVisible = false },
                )

                // Undo for a swiped-away weight entry. The Trends view model is activity-scoped,
                // so this is the same instance the Trends screen deleted from.
                val trendsViewModel: TrendsViewModel = hiltViewModel()
                val deleted by trendsViewModel.lastDeleted.collectAsStateWithLifecycle()
                val deletedWater by waterViewModel.lastDeleted.collectAsStateWithLifecycle()
                UndoSnackbar(
                    visible = deleted != null || deletedWater != null,
                    message = if (deletedWater != null) "Drink deleted" else "Entry deleted",
                    onUndo = {
                        if (deletedWater != null) waterViewModel.undoDelete()
                        else trendsViewModel.undoDelete()
                    },
                    onTimeout = {
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
