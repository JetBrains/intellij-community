/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.data.LoadingContainer.LoadingState
import com.jetbrains.packagesearch.intellij.plugin.fus.FUSGroupIds
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.ComponentActionWrapper
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.FilterOptions
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InstalledDependenciesUsages
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageIdentifier
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ProjectDataProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.SearchResultUiState
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.get
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.toUiPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.updateAndRepaint
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import com.jetbrains.packagesearch.intellij.plugin.ui.util.onOpacityChanged
import com.jetbrains.packagesearch.intellij.plugin.ui.util.onVisibilityChanged
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import com.jetbrains.packagesearch.intellij.plugin.util.CoroutineLRUCache
import com.jetbrains.packagesearch.intellij.plugin.util.FeatureFlags
import com.jetbrains.packagesearch.intellij.plugin.util.KotlinPluginStatus
import com.jetbrains.packagesearch.intellij.plugin.util.PowerSaveModeState
import com.jetbrains.packagesearch.intellij.plugin.util.combineLatest
import com.jetbrains.packagesearch.intellij.plugin.util.hasKotlinModules
import com.jetbrains.packagesearch.intellij.plugin.util.kotlinPluginStatusFlow
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.loadingContainer
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.logTrace
import com.jetbrains.packagesearch.intellij.plugin.util.lookAndFeelFlow
import com.jetbrains.packagesearch.intellij.plugin.util.modifyPackages
import com.jetbrains.packagesearch.intellij.plugin.util.moduleChangesSignalFlow
import com.jetbrains.packagesearch.intellij.plugin.util.onEach
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectCachesService
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService
import com.jetbrains.packagesearch.intellij.plugin.util.shareInAndCatchAndLog
import com.jetbrains.packagesearch.intellij.plugin.util.timer
import com.jetbrains.packagesearch.intellij.plugin.util.uiStateSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JViewport
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

internal class PackagesListPanel(
    private val project: Project,
    targetModulesFlow: Flow<TargetModules>,
    private val dataProvider: ProjectDataProvider,
    private val searchCache: CoroutineLRUCache<SearchCommandModel, ProjectDataProvider.ParsedSearchResponse> =
        project.packageSearchProjectCachesService.searchCache
) : PackageSearchPanelBase(PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.title")) {

    private val searchFieldFocus = Channel<Unit>()

    private val packagesTable = PackagesTable(project, ::onSearchResultStateChanged)

    private val onlyStableMutableStateFlow = MutableStateFlow(true)

    val onlyStableStateFlow: StateFlow<Boolean> = onlyStableMutableStateFlow
    val selectedPackageStateFlow: StateFlow<UiPackageModel<*>?> = packagesTable.selectedPackageStateFlow

    private val onlyMultiplatformStateFlow = MutableStateFlow(false)
    private val searchQueryStateFlow = MutableStateFlow("")

    private val searchResultsUiStateOverridesState: MutableStateFlow<Map<PackageIdentifier, SearchResultUiState>> =
        MutableStateFlow(emptyMap())

    private val searchTextField = PackagesSmartSearchField(searchFieldFocus.consumeAsFlow(), project)
        .apply {
            goToTable = {
                if (packagesTable.hasInstalledItems) {
                    packagesTable.selectedIndex = packagesTable.firstPackageIndex
                    IdeFocusManager.getInstance(project).requestFocus(packagesTable, false)
                    true
                } else {
                    false
                }
            }
            fieldClearedListener = {
                PackageSearchEventsLogger.logSearchQueryClear()
            }
        }

    private val packagesPanel = PackageSearchUI.borderPanel {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }

    private val onlyStableCheckBox = PackageSearchUI.checkBox(PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.filter.onlyStable"))
        .apply {
            isOpaque = false
            border = emptyBorder(left = 6)
            isSelected = true
        }

    private val onlyMultiplatformCheckBox =
        PackageSearchUI.checkBox(PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.filter.onlyMpp")) {
            isOpaque = false
            border = emptyBorder(left = 6)
            isSelected = false
        }

    private val searchFiltersToolbar = ActionManager.getInstance()
        .createActionToolbar("Packages.Manage", createActionGroup(), true)
        .apply {
            component.background = if (PackageSearchUI.isNewUI) {
                PackageSearchUI.Colors.panelBackground
            } else {
                PackageSearchUI.Colors.headerBackground
            }
            component.border = JBUI.Borders.customLineLeft(PackageSearchUI.Colors.panelBackground)
        }

    private fun createActionGroup() = DefaultActionGroup().apply {
        add(ComponentActionWrapper { onlyStableCheckBox })
        add(ComponentActionWrapper { onlyMultiplatformCheckBox })
    }

    private val searchPanel = PackageSearchUI.headerPanel {
        PackageSearchUI.setHeightPreScaled(this, PackageSearchUI.mediumHeaderHeight.get())

        border = BorderFactory.createEmptyBorder()

        addToCenter(object : JPanel() {
            init {
                layout = MigLayout("ins 0, fill", "[left, fill, grow][right]", "fill")
                add(searchTextField)
                add(searchFiltersToolbar.component)

                searchFiltersToolbar.targetComponent = this

                if (PackageSearchUI.isNewUI) {
                    project.lifecycleScope.launch {
                        // This is a hack — the ActionToolbar will reset its own background colour,
                        // so we need to wait for the next frame to set it
                        delay(16.milliseconds)
                        withContext(Dispatchers.EDT) {
                            searchFiltersToolbar.component.background = PackageSearchUI.Colors.panelBackground
                        }
                    }
                }

                border = JBUI.Borders.customLineBottom(PackageSearchUI.Colors.separator)
            }

            override fun getBackground() = PackageSearchUI.Colors.panelBackground
        })
    }

    private val headerPanel = HeaderPanel { operations ->
        logDebug("PackagesListPanel.headerPanel#onUpdateAllLinkClicked()") {
            "The user has clicked the update all link. This will cause many operation(s) to be executed."
        }
        project.modifyPackages(operations)
    }.apply {
        border = JBUI.Borders.customLineTop(PackageSearchUI.Colors.separator)
    }

    private val tableScrollPane = JBScrollPane(
        packagesPanel.apply {
            add(packagesTable)
            add(Box.createVerticalGlue())
        },
        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    ).apply {
        border = emptyBorder()
        viewportBorder = emptyBorder()
        viewport.scrollMode = JViewport.SIMPLE_SCROLL_MODE
        verticalScrollBar.apply {
            headerPanel.adjustForScrollbar(isVisible, isOpaque)

            // Here, we should make sure we set IGNORE_SCROLLBAR_IN_INSETS, but alas, it doesn't work with JTables
            // as of IJ 2022.3 (see JBViewport#updateBorder()). If it did, we could just set:
            // UIUtil.putClientProperty(this, JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, false)
            // Instead, we have to work around the issue, inferring if the scrollbar is "floating" by looking at
            // its isOpaque property — since Swing maps the opacity of scrollbars to whether they're "floating"
            // (e.g., on macOS, System Preferences > General > Show scroll bars: "When scrolling")
            onOpacityChanged { newIsOpaque ->
                headerPanel.adjustForScrollbar(isVisible, newIsOpaque)
            }
            onVisibilityChanged { newIsVisible ->
                headerPanel.adjustForScrollbar(newIsVisible, isOpaque)
            }
        }
    }

    private val listPanel = JBPanelWithEmptyText().apply {
        emptyText.text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.empty.base")
        layout = BorderLayout()
        add(tableScrollPane, BorderLayout.CENTER)
        background = PackageSearchUI.Colors.panelBackground
        border = JBUI.Borders.customLineTop(PackageSearchUI.Colors.separator)
    }

    internal data class SearchCommandModel(
        val onlyStable: Boolean,
        val onlyMultiplatform: Boolean,
        val searchQuery: String,
    )

    internal data class SearchResultsModel(
        val searchQuery: String,
        val apiSearchResults: ProjectDataProvider.ParsedSearchResponse
    )

    private data class ViewModels(
        val targetModules: TargetModules,
        val headerData: PackagesHeaderData,
        val viewModel: PackagesTable.ViewModel
    )

    init {
        registerForUiEvents()

        val searchResultsFlow = combineLatest(
            onlyStableStateFlow,
            onlyMultiplatformStateFlow,
            searchQueryStateFlow.debounce(150.milliseconds),
            project.loadingContainer
        ) { onlyStable, onlyMultiplatform, searchQuery ->
            SearchResultsModel(
                searchQuery = searchQuery,
                apiSearchResults = searchCache.getOrTryPutDefault(SearchCommandModel(onlyStable, onlyMultiplatform, searchQuery)) {
                    dataProvider.doSearch(
                        searchQuery = searchQuery,
                        filterOptions = FilterOptions(onlyStable, onlyMultiplatform)
                    )
                }
            )
        }.shareIn(project.lifecycleScope, SharingStarted.Eagerly, 1)

        combineLatest(
            project.packageSearchProjectService.installedDependenciesFlow,
            project.packageSearchProjectService.repositoriesDeclarationsByModuleFlow,
            project.packageSearchProjectService.allKnownRepositoriesFlow,
            targetModulesFlow,
            onlyStableStateFlow,
            onlyMultiplatformStateFlow,
            searchResultsFlow,
            searchResultsUiStateOverridesState,
            project.loadingContainer
        ) { installedDependencies, repositoriesDeclarationsByModule,
            allKnownRepositories, targetModules, onlyStable, onlyMultiplatform,
            (searchQuery, apiSearchResults),
            searchResultsUiStateOverridesState ->

            logDebug("PackagesListPanel main flow") { "Starting..." }

            val initTime = TimeSource.Monotonic.markNow()
            val (filteredInstalledPackagesUiModels, time1) = measureTimedValue {
                computeInstalledUiPackages(targetModules, installedDependencies, onlyMultiplatform, onlyStable, searchQuery, project)
            }

            logTrace("PackagesListPanel main flow") { "filteredInstalledPackagesUiModels took $time1" }

            val (searchResultModels, time2) = measureTimedValue {
                computeSearchResultModels(
                    searchResults = apiSearchResults,
                    installedPackages = filteredInstalledPackagesUiModels,
                    onlyStable = onlyStable,
                    targetModules = targetModules,
                    searchResultsUiStateOverrides = searchResultsUiStateOverridesState,
                    knownRepositoriesInTargetModules = repositoriesDeclarationsByModule,
                    project = project,
                    allKnownRepositories = allKnownRepositories,
                )
            }
            logTrace("PackagesListPanel main flow") { "computeSearchResultModels took $time2" }

            val tableItems = computePackagesTableItems(
                packages = filteredInstalledPackagesUiModels + searchResultModels,
                targetModules = targetModules,
            )

            val (headerData, time3) = measureTimedValue {
                computeHeaderData(
                    totalItemsCount = tableItems.size,
                    packageUpdateInfos = tableItems.items.filterIsInstance<PackagesTableItem.InstalledPackage>(),
                    hasSearchResults = apiSearchResults.data.packages.isNotEmpty(),
                    targetModules = targetModules,
                )
            }
            logTrace("PackagesListPanel main flow") { "headerData took $time3" }

            val computationTime = initTime.elapsedNow()
            logTrace("PackagesListPanel main flow") { "Total elaboration took $computationTime" }
            ViewModels(
                targetModules = targetModules,
                headerData = headerData,
                viewModel = PackagesTable.ViewModel(
                    items = tableItems,
                    onlyStable = onlyStable,
                    targetModules = targetModules,
                    knownRepositoriesInTargetModules = repositoriesDeclarationsByModule,
                    allKnownRepositories = allKnownRepositories
                )
            )
        }
            .flowOn(project.lifecycleScope.coroutineDispatcher)
            .onEach { (targetModules, headerData, packagesTableViewModel) ->
                val renderingTime = measureTime {
                    updateListEmptyState(targetModules, project.loadingContainer.loadingFlow.value)

                    headerPanel.display(headerData)

                    packagesTable.display(packagesTableViewModel)

                    tableScrollPane.isVisible = packagesTableViewModel.items.isNotEmpty()

                    listPanel.updateAndRepaint()
                    packagesTable.updateAndRepaint()
                    packagesPanel.updateAndRepaint()
                }
                logTrace("PackagesListPanel main flow") {
                    "Rendering took $renderingTime for ${packagesTableViewModel.items.size} items"
                }
            }
            .flowOn(Dispatchers.EDT)
            .catch {
                logDebug("Error in PackagesListPanel main flow", it)
            }
            .launchIn(project.lifecycleScope)

        project.loadingContainer
            .loadingFlow
            .debounce(150)
            .onEach { headerPanel.showBusyIndicator(it == LoadingState.LOADING) }
            .flowOn(Dispatchers.EDT)
            .launchIn(project.lifecycleScope)

        project.lookAndFeelFlow.onEach { updateUiOnLafChange() }
            .launchIn(project.lifecycleScope)

        // The results may have changed server-side. Better clear caches...
        timer(10.minutes)
            .onEach { searchCache.clear() }
            .launchIn(project.lifecycleScope)

        searchResultsFlow.map { it.searchQuery }
            .debounce(500)
            .distinctUntilChanged()
            .filterNot { it.isBlank() }
            .onEach { PackageSearchEventsLogger.logSearchRequest(it) }
            .launchIn(project.lifecycleScope)

        combine(
            ApplicationManager.getApplication().kotlinPluginStatusFlow,
            FeatureFlags.smartKotlinMultiplatformCheckboxEnabledFlow,
            project.moduleChangesSignalFlow,
        ) { kotlinPluginStatus, useSmartCheckbox, _ ->
            val isKotlinPluginAvailable = kotlinPluginStatus == KotlinPluginStatus.AVAILABLE
            isKotlinPluginAvailable && (!useSmartCheckbox || project.hasKotlinModules())
        }
            .onEach(Dispatchers.EDT) { onlyMultiplatformCheckBox.isVisible = it }
            .launchIn(project.lifecycleScope)
    }

    private fun updateListEmptyState(targetModules: TargetModules, loadingState: LoadingState) {
        listPanel.emptyText.clear()
        when {
            PowerSaveModeState.getCurrentState() == PowerSaveModeState.ENABLED -> {
                listPanel.emptyText.appendLine(
                    PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.empty.powerSaveMode")
                )
            }
            isSearching() -> {
                listPanel.emptyText.text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.empty.searching")
            }
            else -> when {
                targetModules is TargetModules.None -> {
                    listPanel.emptyText.text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.empty.noModule")
                }
                loadingState == LoadingState.IDLE -> {
                    val targetModuleNames = when (targetModules) {
                        is TargetModules.All -> PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.empty.allModules")
                        is TargetModules.One -> targetModules.module.name
                        is TargetModules.None -> error("No module selected empty state should be handled separately")
                    }
                    listPanel.emptyText.appendLine(
                        PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.empty.packagesOnly", targetModuleNames)
                    )
                    listPanel.emptyText.appendLine(
                        PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.empty.learnMore"),
                        SimpleTextAttributes.LINK_ATTRIBUTES
                    ) {
                        BrowserUtil.browse("https://www.jetbrains.com/help/idea/package-search-build-system-support-limitations.html")
                    }
                }
                else -> listPanel.emptyText.appendLine(
                    PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.empty.loading")
                )
            }
        }
    }

    private fun isSearching() = !searchTextField.text.isNullOrBlank()

    private fun registerForUiEvents() {
        packagesTable.transferFocusUp = {
            IdeFocusManager.getInstance(project).requestFocus(searchTextField, false)
        }

        searchTextField.addOnTextChangedListener { text ->
            searchQueryStateFlow.tryEmit(text)
        }

        onlyStableCheckBox.addSelectionChangedListener { selected ->
            onlyStableMutableStateFlow.tryEmit(selected)
            PackageSearchEventsLogger.logToggle(FUSGroupIds.ToggleTypes.OnlyStable, selected)
        }

        onlyMultiplatformCheckBox.addSelectionChangedListener { selected ->
            onlyMultiplatformStateFlow.tryEmit(selected)
            PackageSearchEventsLogger.logToggle(FUSGroupIds.ToggleTypes.OnlyKotlinMp, selected)
        }

        project.uiStateSource.searchQueryFlow.onEach { searchTextField.text = it }
            .flowOn(Dispatchers.EDT)
            .launchIn(project.lifecycleScope)
    }

    private suspend fun updateUiOnLafChange() = withContext(Dispatchers.EDT) {
        @Suppress("MagicNumber") // Dimension constants
        with(searchTextField) {
            textEditor.putClientProperty("JTextField.Search.Gap", 6.scaled())
            textEditor.putClientProperty("JTextField.Search.GapEmptyText", (-1).scaled())
            textEditor.border = emptyBorder(left = 6)
            textEditor.isOpaque = true
            textEditor.background = PackageSearchUI.Colors.headerBackground
        }
    }

    override fun build() = PackageSearchUI.boxPanel {
        add(searchPanel)
        add(headerPanel)
        add(listPanel)

        @Suppress("MagicNumber") // Dimension constants
        minimumSize = Dimension(200.scaled(), minimumSize.height)
    }

    override fun getData(dataId: String) = null

    private fun onSearchResultStateChanged(
        searchResult: PackageModel.SearchResult,
        overrideVersion: NormalizedPackageVersion<*>?,
        overrideScope: PackageScope?
    ) {
        project.lifecycleScope.launch {
            val uiStates = searchResultsUiStateOverridesState.value.toMutableMap()
            uiStates[searchResult.identifier] = SearchResultUiState(overrideVersion, overrideScope)
            searchResultsUiStateOverridesState.emit(uiStates)
        }
    }
}

private fun computeInstalledUiPackages(
    targetModules: TargetModules,
    installedDependencies: InstalledDependenciesUsages,
    onlyMultiplatform: Boolean,
    onlyStable: Boolean,
    searchQuery: String,
    project: Project
) = when (targetModules) {
    TargetModules.None -> emptySequence()
    is TargetModules.One -> installedDependencies.all.asSequence()
        .filter { searchQuery in it.searchableInfo }
    is TargetModules.All -> targetModules.modules.asSequence()
        .flatMap { installedDependencies.byModule[it] ?: emptyList() }
        .filter { if (searchQuery.isNotEmpty()) searchQuery in it.searchableInfo else true }
        .distinctBy { it.identifier }
}
    .filter { if (onlyMultiplatform) it.isKotlinMultiplatform else true }
    .flatMap { it.toUiPackageModel(targetModules, project, onlyStable) }
    .filter { it.sortedVersions.isNotEmpty() }
    .sortedBy { it.packageModel.identifier.rawValue }
    .toList()
