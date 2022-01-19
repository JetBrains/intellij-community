package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.actions.ShowSettingsAction
import com.jetbrains.packagesearch.intellij.plugin.actions.TogglePackageDetailsAction
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.modules.ModulesTree
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packagedetails.PackageDetailsPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesListPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.computeModuleTreeModel
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.newCoroutineContext
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JScrollPane

@Suppress("MagicNumber") // Swing dimension constants
internal class PackageManagementPanel(
    val project: Project,
) : PackageSearchPanelBase(PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.title")), CoroutineScope by project.lifecycleScope {

    override val coroutineContext =
        project.lifecycleScope.newCoroutineContext(SupervisorJob() + CoroutineName("PackageManagementPanel"))

    private val operationFactory = PackageSearchOperationFactory()
    private val operationExecutor = NotifyingOperationExecutor(project)

    private val modulesTree = ModulesTree(project)
    private val modulesScrollPanel = JBScrollPane(
        modulesTree,
        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    )

    private val knownRepositoriesInTargetModulesFlow = combine(
        modulesTree.targetModulesFlow,
        project.packageSearchProjectService.allInstalledKnownRepositoriesFlow
    ) { targetModules, installedRepositories ->
        installedRepositories.filterOnlyThoseUsedIn(targetModules)
    }

    private val packagesListPanel = PackagesListPanel(
        project = project,
        operationExecutor = operationExecutor,
        operationFactory = operationFactory,
        viewModelFlow = combine(
            modulesTree.targetModulesFlow,
            project.packageSearchProjectService.installedPackagesStateFlow,
            project.packageSearchProjectService.packageUpgradesStateFlow,
            knownRepositoriesInTargetModulesFlow
        ) { targetModules, installedPackages,
            packageUpgrades, knownReposInModules ->
            PackagesListPanel.ViewModel(targetModules, installedPackages, packageUpgrades, knownReposInModules)
        },
        dataProvider = project.packageSearchProjectService.dataProvider
    )

    private val packageDetailsPanel = PackageDetailsPanel(project, operationExecutor)

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

        project.packageSearchProjectService.moduleModelsStateFlow
            .map { computeModuleTreeModel(it) }
            .flowOn(Dispatchers.Default)
            .onEach { modulesTree.display(it) }
            .flowOn(Dispatchers.EDT)
            .launchIn(this)

        packagesListPanel.selectedPackageStateFlow
            .filterNotNull()
            .onEach { PackageSearchEventsLogger.logPackageSelected(it is UiPackageModel.Installed) }
            .launchIn(this)

        combine(
            knownRepositoriesInTargetModulesFlow,
            packagesListPanel.selectedPackageStateFlow,
            modulesTree.targetModulesFlow,
            packagesListPanel.onlyStableStateFlow
        ) { knownRepositoriesInTargetModules, selectedUiPackageModel,
            targetModules, onlyStable ->
            PackageDetailsPanel.ViewModel(
                selectedPackageModel = selectedUiPackageModel,
                knownRepositoriesInTargetModules = knownRepositoriesInTargetModules,
                targetModules = targetModules,
                onlyStable = onlyStable,
                invokeLaterScope = this
            )
        }.flowOn(Dispatchers.Default)
            .onEach { packageDetailsPanel.display(it) }
            .flowOn(Dispatchers.EDT)
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
}
