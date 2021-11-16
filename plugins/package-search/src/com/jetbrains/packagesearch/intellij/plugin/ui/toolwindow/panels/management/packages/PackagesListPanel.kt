package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.jetbrains.packagesearch.api.v2.ApiPackagesResponse
import com.jetbrains.packagesearch.api.v2.ApiStandardPackage
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.data.PackageUpgradeCandidates
import com.jetbrains.packagesearch.intellij.plugin.fus.FUSGroupIds
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.ComponentActionWrapper
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.FilterOptions
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InstalledDependency
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.KnownRepositories
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.OperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageIdentifier
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackagesToUpgrade
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ProjectDataProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.SearchResultUiState
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.matchesCoordinates
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.toUiPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.updateAndRepaint
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import com.jetbrains.packagesearch.intellij.plugin.ui.util.onOpacityChanged
import com.jetbrains.packagesearch.intellij.plugin.ui.util.onVisibilityChanged
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaledEmptyBorder
import com.jetbrains.packagesearch.intellij.plugin.util.AppUI
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.logWarn
import com.jetbrains.packagesearch.intellij.plugin.util.lookAndFeelFlow
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService
import com.jetbrains.packagesearch.intellij.plugin.util.uiStateSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ItemEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.event.DocumentEvent

internal class PackagesListPanel(
    private val project: Project,
    operationFactory: PackageSearchOperationFactory,
    operationExecutor: OperationExecutor,
    viewModelFlow: Flow<ViewModel>,
    private val dataProvider: ProjectDataProvider
) : PackageSearchPanelBase(PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.title")) {

    private val searchFieldFocus = Channel<Unit>()

    private val packagesTable = PackagesTable(project, operationExecutor, ::onSearchResultStateChanged)

    private val onlyStableMutableStateFlow = MutableStateFlow(true)
    private val selectedPackageMutableStateFlow = packagesTable.selectedPackageStateFlow

    val onlyStableStateFlow: StateFlow<Boolean> = onlyStableMutableStateFlow
    val selectedPackageStateFlow: StateFlow<UiPackageModel<*>?> = selectedPackageMutableStateFlow

    private val onlyMultiplatformStateFlow = MutableStateFlow(false)
    private val searchQueryStateFlow = MutableStateFlow("")
    private val isSearchingStateFlow = MutableStateFlow(false)
    private val isLoadingStateFlow = MutableStateFlow(false)

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
            border = scaledEmptyBorder(left = 6)
            isSelected = true
        }

    private val onlyMultiplatformCheckBox =
        PackageSearchUI.checkBox(PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.filter.onlyMpp"))
            .apply {
                isOpaque = false
                border = scaledEmptyBorder(left = 6)
                isSelected = false
            }

    private val mainToolbar = ActionManager.getInstance().createActionToolbar("Packages.Manage", createActionGroup(), true).apply {
        setTargetComponent(toolbar)
        component.background = PackageSearchUI.HeaderBackgroundColor
        component.border = BorderFactory.createMatteBorder(0, 1.scaled(), 0, 0, JBUI.CurrentTheme.CustomFrameDecorations.paneBackground())
    }

    private fun createActionGroup() = DefaultActionGroup().apply {
        add(ComponentActionWrapper { onlyStableCheckBox })
        add(ComponentActionWrapper { onlyMultiplatformCheckBox })
    }

    private val searchPanel = PackageSearchUI.headerPanel {
        PackageSearchUI.setHeight(this, PackageSearchUI.MediumHeaderHeight)

        border = BorderFactory.createEmptyBorder()

        addToCenter(object : JPanel() {
            init {
                layout = MigLayout("ins 0, fill", "[left, fill, grow][right]", "center")
                add(searchTextField)
                add(mainToolbar.component)
            }

            override fun getBackground() = PackageSearchUI.UsualBackgroundColor
        })
    }

    private val headerPanel = HeaderPanel {
        logDebug("PackagesListPanel.headerPanel#onUpdateAllLinkClicked()") {
            "The user has clicked the update all link. This will cause ${it.size} operation(s) to be executed."
        }
        operationExecutor.executeOperations(it)
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

            // Here we should make sure we set IGNORE_SCROLLBAR_IN_INSETS, but alas it doesn't work with JTables
            // as of IJ 2020.3 (see JBViewport#updateBorder()). If it did, we could just set:
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
        background = PackageSearchUI.UsualBackgroundColor
        border = BorderFactory.createMatteBorder(1.scaled(), 0, 0, 0, JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
    }

    private data class SearchCommandModel(
        val onlyStable: Boolean,
        val onlyMultiplatform: Boolean,
        val searchQuery: String,
    )

    private data class SearchResultsModel(
        val onlyStable: Boolean,
        val onlyMultiplatform: Boolean,
        val searchQuery: String,
        val apiSearchResults: ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>?
    )

    init {
        registerForUiEvents()

        val searchResultsFlow =
            combine(onlyStableStateFlow, onlyMultiplatformStateFlow, searchQueryStateFlow) { onlyStable, onlyMultiplatform, searchQuery ->
                isSearchingStateFlow.emit(true)
                SearchCommandModel(onlyStable, onlyMultiplatform, searchQuery)
            }.debounce(250)
                .mapLatest { (onlyStable, onlyMultiplatform, searchQuery) ->
                    val model = SearchResultsModel(
                        onlyStable,
                        onlyMultiplatform,
                        searchQuery,
                        runCatching { dataProvider.doSearch(searchQuery, FilterOptions(onlyStable, onlyMultiplatform)) }.getOrNull()
                    )
                    isSearchingStateFlow.emit(false)
                    model
                }
                .shareIn(project.lifecycleScope, SharingStarted.Lazily)

        combine(
            viewModelFlow,
            searchResultsFlow,
            searchResultsUiStateOverridesState
        ) { (targetModules, installedPackages, packagesUpdateCandidates,
            knownRepositoriesInTargetModules), (onlyStable, onlyMultiplatform,
            searchQuery, apiSearchResults),
            searchResultsUiStateOverrides ->
            isLoadingStateFlow.emit(true)
            val packagesToUpgrade = packagesUpdateCandidates.getPackagesToUpgrade(onlyStable)
            val filteredPackageUpgrades = when (targetModules) {
                is TargetModules.All -> packagesToUpgrade.allUpdates
                is TargetModules.One -> packagesToUpgrade.getUpdatesForModule(targetModules.module)
                TargetModules.None -> emptyList()
            }

            val filteredInstalledPackages = installedPackages.filterByTargetModules(targetModules)

            val filteredInstalledPackagesUiModels = filteredInstalledPackages
                .let { list -> if (onlyMultiplatform) list.filter { it.isKotlinMultiplatform } else list }
                .map { it.toUiPackageModel(targetModules, project, knownRepositoriesInTargetModules, onlyStable) }
                .filter { it.packageModel.searchableInfo.contains(searchQuery) }

            val searchResultModels = computeSearchResultModels(
                apiSearchResults,
                filteredInstalledPackagesUiModels,
                onlyStable,
                targetModules,
                searchResultsUiStateOverrides,
                knownRepositoriesInTargetModules,
                project
            )

            val tableItems = computePackagesTableItems(
                project = project,
                packages = filteredInstalledPackagesUiModels + searchResultModels,
                targetModules = targetModules
            )

            val headerData = computeHeaderData(
                totalItemsCount = tableItems.size,
                packageUpdateInfos = filteredPackageUpgrades,
                hasSearchResults = apiSearchResults?.packages?.isNotEmpty() ?: false,
                targetModules = targetModules,
                knownRepositoriesInTargetModules = knownRepositoriesInTargetModules,
                operationFactory = operationFactory
            )

            withContext(Dispatchers.AppUI) {
                updateListEmptyState(targetModules)

                headerPanel.display(headerData)

                packagesTable.display(
                    PackagesTable.ViewModel(
                        tableItems,
                        onlyStable,
                        targetModules,
                        knownRepositoriesInTargetModules
                    )
                )

                tableScrollPane.isVisible = tableItems.isNotEmpty()

                listPanel.updateAndRepaint()
                packagesTable.updateAndRepaint()
                packagesPanel.updateAndRepaint()
            }
            isLoadingStateFlow.emit(false)
        }.catch { logWarn("Error in PackagesListPanel main flow", it) }
            .launchIn(project.lifecycleScope)

        combineTransform(
            isLoadingStateFlow,
            isSearchingStateFlow,
            project.packageSearchProjectService.isLoadingFlow
        ) { booleans -> emit(booleans.any { it }) }
            .debounce(150)
            .onEach { headerPanel.showBusyIndicator(it) }
            .flowOn(Dispatchers.AppUI)
            .launchIn(project.lifecycleScope)

        project.lookAndFeelFlow.onEach { updateUiOnLafChange() }
            .launchIn(project.lifecycleScope)

        searchResultsFlow.map { it.searchQuery }
            .debounce(500)
            .distinctUntilChanged()
            .filterNot { it.isBlank() }
            .onEach { PackageSearchEventsLogger.logSearchRequest(it) }
            .launchIn(project.lifecycleScope)
    }

    private fun updateListEmptyState(targetModules: TargetModules) {
        when {
            isSearching() -> {
                listPanel.emptyText.text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.empty.searching")
            }
            targetModules is TargetModules.None -> {
                listPanel.emptyText.text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.empty.noModule")
            }
            else -> {
                val targetModuleNames = when (targetModules) {
                    is TargetModules.All -> PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.empty.allModules")
                    is TargetModules.One -> targetModules.module.projectModule.name
                    is TargetModules.None -> error("No module selected empty state should be handled separately")
                }
                listPanel.emptyText.text =
                    PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.empty.packagesOnly", targetModuleNames)
            }
        }
    }

    private fun isSearching() = !searchTextField.text.isNullOrBlank()

    internal data class ViewModel(
        val targetModules: TargetModules,
        val installedPackages: List<PackageModel.Installed>,
        val packagesUpdateCandidates: PackageUpgradeCandidates,
        val knownRepositoriesInTargetModules: KnownRepositories.InTargetModules
    )

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
            .flowOn(Dispatchers.AppUI)
            .launchIn(project.lifecycleScope)
    }

    private suspend fun updateUiOnLafChange() = withContext(Dispatchers.AppUI) {
        @Suppress("MagicNumber") // Dimension constants
        with(searchTextField) {
            textEditor.putClientProperty("JTextField.Search.Gap", 6.scaled())
            textEditor.putClientProperty("JTextField.Search.GapEmptyText", (-1).scaled())
            textEditor.border = scaledEmptyBorder(left = 6)
            textEditor.isOpaque = true
            textEditor.background = PackageSearchUI.HeaderBackgroundColor
        }
    }

    override fun build() = PackageSearchUI.boxPanel {
        add(searchPanel)
        add(headerPanel)
        add(listPanel)

        @Suppress("MagicNumber") // Dimension constants
        minimumSize = Dimension(200.scaled(), minimumSize.height)
    }

    private fun onSearchResultStateChanged(
        searchResult: PackageModel.SearchResult,
        overrideVersion: PackageVersion?,
        overrideScope: PackageScope?
    ) {
        project.lifecycleScope.launch {
            val uiStates = searchResultsUiStateOverridesState.value.toMutableMap()
            uiStates[searchResult.identifier] = SearchResultUiState(overrideVersion, overrideScope)
            searchResultsUiStateOverridesState.emit(uiStates)
        }
    }
}

@Suppress("FunctionName")
private fun SearchTextFieldTextChangedListener(action: (DocumentEvent) -> Unit) =
    object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) = action(e)
    }

private fun SearchTextField.addOnTextChangedListener(action: (String) -> Unit) =
    SearchTextFieldTextChangedListener { action(text) }.also { addDocumentListener(it) }

internal fun JCheckBox.addSelectionChangedListener(action: (Boolean) -> Unit) =
    addItemListener { e -> action(e.stateChange == ItemEvent.SELECTED) }

private fun computeHeaderData(
    totalItemsCount: Int,
    packageUpdateInfos: List<PackagesToUpgrade.PackageUpgradeInfo>,
    hasSearchResults: Boolean,
    targetModules: TargetModules,
    knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
    operationFactory: PackageSearchOperationFactory
): PackagesHeaderData {
    val moduleNames = if (targetModules is TargetModules.One) {
        targetModules.module.projectModule.name
    } else {
        PackageSearchBundle.message("packagesearch.ui.toolwindow.allModules").lowercase()
    }

    val title = if (hasSearchResults) {
        PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.searchResults")
    } else {
        PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.installedPackages.addedIn", moduleNames)
    }

    val operations = packageUpdateInfos.asSequence()
        .flatMap { packageUpdateInfo ->
            val repoToInstall = knownRepositoriesInTargetModules.repositoryToAddWhenInstallingOrUpgrading(
                packageModel = packageUpdateInfo.packageModel,
                selectedVersion = packageUpdateInfo.targetVersion
            )
            operationFactory.createChangePackageVersionOperations(
                packageModel = packageUpdateInfo.packageModel,
                newVersion = packageUpdateInfo.targetVersion,
                targetModules = targetModules,
                repoToInstall = repoToInstall
            )
        }

    return PackagesHeaderData(
        labelText = title,
        count = totalItemsCount.coerceAtLeast(0),
        availableUpdatesCount = packageUpdateInfos.distinctBy { it.packageModel.identifier }.size,
        updateOperations = operations.toList()
    )
}

private fun List<PackageModel.Installed>.filterByTargetModules(
    targetModules: TargetModules
) = when (targetModules) {
    is TargetModules.All -> this
    is TargetModules.One -> mapNotNull { installedPackage ->
        val filteredUsages = installedPackage.usageInfo.filter {
            it.projectModule == targetModules.module.projectModule
        }

        if (filteredUsages.isEmpty()) return@mapNotNull null

        installedPackage.copyWithUsages(filteredUsages)
    }
    TargetModules.None -> emptyList()
}

private fun computeSearchResultModels(
    searchResults: ApiPackagesResponse<ApiStandardPackage, ApiStandardPackage.ApiStandardVersion>?,
    installedPackages: List<UiPackageModel.Installed>,
    onlyStable: Boolean,
    targetModules: TargetModules,
    searchResultsUiStateOverrides: Map<PackageIdentifier, SearchResultUiState>,
    knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
    project: Project
): List<UiPackageModel.SearchResult> {
    if (searchResults == null || searchResults.packages.isEmpty()) return emptyList()

    val installedDependencies = installedPackages.map { it.packageModel }
        .map { InstalledDependency(it.groupId, it.artifactId) }

    return searchResults.packages
        .filterNot { installedDependencies.any { installed -> installed.matchesCoordinates(it) } }
        .mapNotNull { PackageModel.fromSearchResult(it) }
        .map {
            val uiState = searchResultsUiStateOverrides[it.identifier]
            it.toUiPackageModel(targetModules, project, uiState, knownRepositoriesInTargetModules, onlyStable)
        }
        .toList()
}
