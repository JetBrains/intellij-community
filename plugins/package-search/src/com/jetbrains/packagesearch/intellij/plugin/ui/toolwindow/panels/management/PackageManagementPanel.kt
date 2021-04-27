package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.actions.ShowSettingsAction
import com.jetbrains.packagesearch.intellij.plugin.actions.TogglePackageDetailsAction
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.LifetimeProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.OperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.RootDataModelProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.SearchClient
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.SelectedPackageSetter
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModuleSetter
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.modules.ModulesTree
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packagedetails.PackageDetailsPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesListPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.updateAndRepaint
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.rd.util.reactive.map
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JScrollPane

@Suppress("MagicNumber") // Swing dimension constants
internal class PackageManagementPanel(
    private val rootDataModelProvider: RootDataModelProvider,
    selectedPackageSetter: SelectedPackageSetter,
    targetModuleSetter: TargetModuleSetter,
    searchClient: SearchClient,
    operationExecutor: OperationExecutor,
    lifetimeProvider: LifetimeProvider
) : PackageSearchPanelBase(PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.title")), Disposable {

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
        lifetimeProvider = lifetimeProvider,
        operationExecutor = operationExecutor,
        operationFactory = operationFactory
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
        packagesListPanel.selectedPackage.advise(lifetimeProvider.lifetime) { selectedPackage ->
            selectedPackageSetter.setSelectedPackage(selectedPackage)

            packageDetailsPanel.display(
                selectedPackageModel = selectedPackage,
                knownRepositoriesInTargetModules = rootDataModelProvider.dataModelProperty.value.knownRepositoriesInTargetModules,
                allKnownRepositories = rootDataModelProvider.dataModelProperty.value.allKnownRepositories,
                targetModules = rootDataModelProvider.dataModelProperty.value.targetModules,
                onlyStable = rootDataModelProvider.dataModelProperty.value.filterOptions.onlyStable
            )
        }

        rootDataModelProvider.statusProperty.advise(lifetimeProvider.lifetime) { status ->
            packagesListPanel.setIsBusy(status.isBusy)
        }

        rootDataModelProvider.statusProperty.map { it.isExecutingOperations }.advise(lifetimeProvider.lifetime) { isExecutingOperations ->
            content.isEnabled = !isExecutingOperations
            content.updateAndRepaint()
        }

        rootDataModelProvider.dataModelProperty.advise(lifetimeProvider.lifetime) { data ->
            modulesTree.display(
                projectModules = data.projectModules,
                targetModules = data.targetModules,
                traceInfo = data.traceInfo
            )
            packagesListPanel.display(
                headerData = data.headerData,
                packageModels = data.packageModels,
                targetModules = data.targetModules,
                knownRepositoriesInTargetModules = data.knownRepositoriesInTargetModules,
                allKnownRepositories = data.allKnownRepositories,
                filterOptions = data.filterOptions,
                traceInfo = data.traceInfo
            )
            packageDetailsPanel.display(
                selectedPackageModel = data.selectedPackage,
                knownRepositoriesInTargetModules = data.knownRepositoriesInTargetModules,
                allKnownRepositories = data.allKnownRepositories,
                targetModules = data.targetModules,
                onlyStable = data.filterOptions.onlyStable
            )
        }
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
        Disposer.dispose(modulesTree)
        Disposer.dispose(packagesListPanel)
    }
}
