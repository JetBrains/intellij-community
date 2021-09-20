package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.actions.ShowSettingsAction
import com.jetbrains.packagesearch.intellij.plugin.actions.TogglePackageDetailsAction
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.OperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.RootDataModelProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.SearchClient
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.SearchResultStateSetter
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.SelectedPackageSetter
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModuleSetter
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.modules.ModulesTree
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packagedetails.PackageDetailsPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesListPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.computeModuleTreeModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.computePackagesTableItems
import com.jetbrains.packagesearch.intellij.plugin.ui.updateAndRepaint
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchDataService
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.newCoroutineContext
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JScrollPane

@Suppress("MagicNumber") // Swing dimension constants
internal class PackageManagementPanel(
    rootDataModelProvider: RootDataModelProvider,
    selectedPackageSetter: SelectedPackageSetter,
    searchResultStateSetter: SearchResultStateSetter,
    targetModuleSetter: TargetModuleSetter,
    searchClient: SearchClient,
    operationExecutor: OperationExecutor,
    project: Project,
    programmaticSearchQueryInputsFlow: Flow<String>
) : PackageSearchPanelBase(PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.title")), CoroutineScope, Disposable {

    companion object {

        operator fun invoke(project: Project) = PackageManagementPanel(
            rootDataModelProvider = project.packageSearchDataService,
            selectedPackageSetter = project.packageSearchDataService,
            searchResultStateSetter = project.packageSearchDataService,
            targetModuleSetter = project.packageSearchDataService,
            searchClient = project.packageSearchDataService,
            operationExecutor = project.packageSearchDataService,
            project = project,
            programmaticSearchQueryInputsFlow = project.packageSearchDataService.programmaticSearchQueryStateFlow
        )
    }

    override val coroutineContext =
        project.lifecycleScope.newCoroutineContext(SupervisorJob() + CoroutineName("PackageManagementPanel"))

    private val operationFactory = PackageSearchOperationFactory()

    private val modulesTree = ModulesTree(targetModuleSetter)
    private val modulesScrollPanel = JBScrollPane(
        modulesTree,
        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    )

    private val project = rootDataModelProvider.project

    private val packagesListPanel = PackagesListPanel(
        project = project,
        searchClient = searchClient,
        operationExecutor = operationExecutor,
        operationFactory = operationFactory,
        onItemSelectionChanged = { launch { selectedPackageSetter.setSelectedPackage(it) } },
        onSearchResultStateChanged = { searchResult, version, scope ->
            launch { searchResultStateSetter.setSearchResultState(searchResult, version, scope) }
        },
        programmaticSearchQueryInputsFlow = programmaticSearchQueryInputsFlow
    )

    private val packageDetailsPanel = PackageDetailsPanel(operationFactory, operationExecutor)

    private val packagesSplitter = JBSplitter(
        "PackageSearch.PackageManagementPanel.DetailsSplitter",
        PackageSearchGeneralConfiguration.DefaultPackageDetailsSplitterProportion
    ).apply {
        firstComponent = packagesListPanel.content
        secondComponent = packageDetailsPanel.content
        orientation = false // Horizontal split
        dividerWidth = 2.scaled()
    }

    private val mainSplitter = JBSplitter("PackageSearch.PackageManagementPanel.Splitter", 0.1f).apply {
        firstComponent = modulesScrollPanel
        secondComponent = packagesSplitter
        orientation = false // Horizontal split
        dividerWidth = 2.scaled()
    }

    init {
        updatePackageDetailsVisible(PackageSearchGeneralConfiguration.getInstance(project).packageDetailsVisible)

        modulesScrollPanel.apply {
            border = BorderFactory.createEmptyBorder()
            minimumSize = Dimension(250.scaled(), 0)

            UIUtil.putClientProperty(verticalScrollBar, JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, true)
        }

        packagesListPanel.content.minimumSize = Dimension(250.scaled(), 0)

        packagesListPanel.selectedPackage.onEach { selectedPackage ->
            selectedPackageSetter.setSelectedPackage(selectedPackage)
        }.launchIn(this)

        rootDataModelProvider.dataStatusState.onEach { status ->
            packagesListPanel.setIsBusy(status.isBusy)
        }.launchIn(this)

        rootDataModelProvider.dataStatusState.map { it.isExecutingOperations }
            .onEach { isExecutingOperations ->
                content.isEnabled = !isExecutingOperations
                content.updateAndRepaint()
            }.launchIn(this)

        rootDataModelProvider.dataModelFlow.filter { it.moduleModels.isNotEmpty() }
            .onEach { data ->
                val (treeModel, selectionPath) = readAction {
                    computeModuleTreeModel(
                        modules = data.moduleModels,
                        currentTargetModules = data.targetModules,
                        traceInfo = data.traceInfo
                    )
                }
                modulesTree.display(
                    ModulesTree.ViewModel(
                        treeModel = treeModel,
                        traceInfo = data.traceInfo,
                        pendingSelectionPath = selectionPath
                    )
                )
            }
            .launchIn(this)

        rootDataModelProvider.dataModelFlow.onEach { data ->
            val tableItems = readAction {
                computePackagesTableItems(
                    project = project,
                    packages = data.packageModels,
                    selectedPackage = data.selectedPackage,
                    targetModules = data.targetModules,
                    traceInfo = data.traceInfo
                )
            }

            packagesListPanel.display(
                PackagesListPanel.ViewModel(
                    headerData = data.headerData,
                    targetModules = data.targetModules,
                    knownRepositoriesInTargetModules = data.knownRepositoriesInTargetModules,
                    allKnownRepositories = data.allKnownRepositories,
                    filterOptions = data.filterOptions,
                    tableItems = tableItems,
                    traceInfo = data.traceInfo
                )
            )

            packageDetailsPanel.display(
                PackageDetailsPanel.ViewModel(
                    selectedPackageModel = data.selectedPackage,
                    knownRepositoriesInTargetModules = data.knownRepositoriesInTargetModules,
                    allKnownRepositories = data.allKnownRepositories,
                    targetModules = data.targetModules,
                    onlyStable = data.filterOptions.onlyStable,
                    invokeLaterScope = this
                )
            )
        }
            .launchIn(this)
    }

    private fun updatePackageDetailsVisible(becomeVisible: Boolean) {
        val wasVisible = packagesSplitter.secondComponent.isVisible
        packagesSplitter.secondComponent.isVisible = becomeVisible

        if (!wasVisible && becomeVisible) {
            packagesSplitter.proportion =
                PackageSearchGeneralConfiguration.getInstance(project).packageDetailsSplitterProportion
        }

        if (!becomeVisible) {
            PackageSearchGeneralConfiguration.getInstance(project).packageDetailsSplitterProportion =
                packagesSplitter.proportion
            packagesSplitter.proportion = 1.0f
        }
    }

    private val togglePackageDetailsAction = TogglePackageDetailsAction(project, ::updatePackageDetailsVisible)

    override fun build() = mainSplitter

    override fun buildGearActions() = DefaultActionGroup(
        ShowSettingsAction(project),
        togglePackageDetailsAction
    )

    override fun buildTitleActions(): Array<AnAction> = arrayOf(togglePackageDetailsAction)

    override fun dispose() {
        logDebug("PackageManagementPanel#dispose()") { "Disposing PackageManagementPanel..." }
        cancel("Disposing PackageManagementPanel")
    }
}
