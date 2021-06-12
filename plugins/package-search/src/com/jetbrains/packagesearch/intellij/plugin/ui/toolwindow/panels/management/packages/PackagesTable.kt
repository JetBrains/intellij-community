package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.ide.CopyProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.TableUtil
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.KnownRepositories
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ModuleModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.OperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.SelectedPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.ActionsColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.NameColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.ScopeColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.VersionColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.updateAndRepaint
import com.jetbrains.packagesearch.intellij.plugin.ui.util.AppUI
import com.jetbrains.packagesearch.intellij.plugin.ui.util.autosizeColumnsAt
import com.jetbrains.packagesearch.intellij.plugin.ui.util.debounce
import com.jetbrains.packagesearch.intellij.plugin.ui.util.onMouseMotion
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.rd.util.reactive.IPropertyView
import com.jetbrains.rd.util.reactive.Property
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.concurrent.CancellationException
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumn
import kotlin.math.roundToInt

@Suppress("MagicNumber") // Swing dimension constants
internal class PackagesTable(
    private val project: Project,
    private val operationExecutor: OperationExecutor,
    operationFactory: PackageSearchOperationFactory
) : JBTable(), DataProvider, CopyProvider, Disposable, CoroutineScope {

    override val coroutineContext = SupervisorJob() + CoroutineName("PackagesTable")

    private val operationFactory = PackageSearchOperationFactory()

    private val dataChangedChannel = Channel<DisplayDataModel>()

    private val tableModel: PackagesTableModel
        get() = model as PackagesTableModel

    private val _selectedPackage = Property<SelectedPackageModel<*>?>(null)
    val selectedPackage: IPropertyView<SelectedPackageModel<*>?> = _selectedPackage

    var transferFocusUp: () -> Unit = { transferFocusBackward() }

    private val columnWeights = listOf(
        .5f, // Name column
        .2f, // Scope column
        .2f, // Version column
        .1f // Actions column
    )

    private val nameColumn = NameColumn()

    private val scopeColumn = ScopeColumn { packageModel, newScope -> updatePackageScope(packageModel, newScope) }

    private val versionColumn = VersionColumn { packageModel, newVersion ->
        updatePackageVersion(packageModel, newVersion)
    }

    private val actionsColumn = ActionsColumn(
        operationExecutor = ::executeUpdateActionColumnOperations,
        operationFactory = operationFactory,
        table = this
    )

    private val actionsColumnIndex: Int

    private val autosizingColumnsIndices: List<Int>

    private var latestTargetModules: TargetModules = TargetModules.None
    private var knownRepositoriesInTargetModules = KnownRepositories.InTargetModules.EMPTY
    private var allKnownRepositories = KnownRepositories.All.EMPTY

    init {
        require(columnWeights.sum() == 1.0f) { "The column weights must sum to 1.0" }

        model = PackagesTableModel(
            onlyStable = PackageSearchGeneralConfiguration.getInstance(project).onlyStable,
            columns = arrayOf(nameColumn, scopeColumn, versionColumn, actionsColumn)
        )

        val columnInfos = tableModel.columnInfos
        actionsColumnIndex = columnInfos.indexOf(actionsColumn)
        autosizingColumnsIndices = listOf(
            columnInfos.indexOf(scopeColumn),
            columnInfos.indexOf(versionColumn),
            actionsColumnIndex
        )

        setTableHeader(InvisibleResizableHeader())
        getTableHeader().apply {
            reorderingAllowed = false
            resizingAllowed = true
        }

        columnSelectionAllowed = false

        setShowGrid(false)
        rowHeight = 20.scaled()

        background = UIUtil.getTableBackground()
        foreground = UIUtil.getTableForeground()
        selectionBackground = UIUtil.getTableSelectionBackground(true)
        selectionForeground = UIUtil.getTableSelectionForeground(true)

        setExpandableItemsEnabled(false)
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        putClientProperty("terminateEditOnFocusLost", true)

        intercellSpacing = Dimension(0, 2)

        // By default, JTable uses Tab/Shift-Tab for navigation between cells; Ctrl-Tab/Ctrl-Shift-Tab allows to break out of the JTable.
        // In this table, we don't want to navigate between cells - so override the traversal keys by default values.
        setFocusTraversalKeys(
            KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
            KeyboardFocusManager.getCurrentKeyboardFocusManager().getDefaultFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)
        )
        setFocusTraversalKeys(
            KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
            KeyboardFocusManager.getCurrentKeyboardFocusManager().getDefaultFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS)
        )

        addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) {
                if (tableModel.rowCount > 0 && selectedRows.isEmpty()) {
                    setRowSelectionInterval(0, 0)
                }
            }
        })

        PackageSearchUI.overrideKeyStroke(this, "jtable:RIGHT", "RIGHT") { transferFocus() }
        PackageSearchUI.overrideKeyStroke(this, "jtable:ENTER", "ENTER") { transferFocus() }
        PackageSearchUI.overrideKeyStroke(this, "shift ENTER") {
            clearSelection()
            transferFocusUp()
        }

        selectionModel.addListSelectionListener {
            val item = getSelectedTableItem()
            if (selectedIndex >= 0 && item != null) {
                TableUtil.scrollSelectionToVisible(this)
                updateAndRepaint()
                _selectedPackage.set(item.toSelectedPackageModule())
            } else {
                _selectedPackage.set(null)
            }
        }

        addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) {
                if (selectedIndex == -1) {
                    selectedIndex = firstPackageIndex
                }
            }
        })

        TableSpeedSearch(this) { item, _ ->
            if (item is PackagesTableItem.InstalledPackage) {
                item.packageModel.identifier
            } else {
                ""
            }
        }.apply {
            comparator = SpeedSearchComparator(false)
        }

        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                applyColumnSizes(columnModel.totalColumnWidth, columnModel.columns.toList(), columnWeights)
                removeComponentListener(this)
            }
        })

        onMouseMotion(
            onMouseMoved = {
                val point = it.point
                val hoverColumn = columnAtPoint(point)
                val hoverRow = rowAtPoint(point)

                if (tableModel.items.isEmpty() || !(0 until tableModel.items.count()).contains(hoverColumn)) {
                    actionsColumn.hoverItem = null
                    return@onMouseMotion
                }

                val item = tableModel.items[hoverRow]
                if (actionsColumn.hoverItem != item && hoverColumn == actionsColumnIndex) {
                    actionsColumn.hoverItem = item
                    updateAndRepaint()
                } else {
                    actionsColumn.hoverItem = null
                }
            }
        )

        dataChangedChannel.consumeAsFlow()
            .debounce(100)
            .onEach { displayData(it.packages, it.onlyStable, it.targetModules, it.traceInfo) }
            .launchIn(this)

    }

    override fun getCellRenderer(row: Int, column: Int): TableCellRenderer =
        tableModel.columns[column].getRenderer(tableModel.items[row]) ?: DefaultTableCellRenderer()

    override fun getCellEditor(row: Int, column: Int): TableCellEditor? =
        tableModel.columns[column].getEditor(tableModel.items[row])

    private data class DisplayDataModel(
        val packages: List<PackageModel>,
        val onlyStable: Boolean,
        val targetModules: TargetModules,
        val traceInfo: TraceInfo
    )

    fun display(
        packages: List<PackageModel>,
        onlyStable: Boolean,
        targetModules: TargetModules,
        knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
        allKnownRepositories: KnownRepositories.All,
        traceInfo: TraceInfo
    ) {
        logDebug(traceInfo, "PackagesTable#display()") { "Got data, ${packages.size} package(s)" }

        this.knownRepositoriesInTargetModules = knownRepositoriesInTargetModules
        this.allKnownRepositories = allKnownRepositories

        dataChangedChannel.offer(DisplayDataModel(packages, onlyStable, targetModules, traceInfo))

    }

    private suspend fun displayData(
        packageModels: List<PackageModel>,
        onlyStable: Boolean,
        targetModules: TargetModules,
        traceInfo: TraceInfo
    ) {
        val displayItems = computeDisplayItems(packageModels, onlyStable, targetModules, traceInfo)

        logDebug(traceInfo, "PackagesTable#displayData()") { "Displaying ${displayItems.size} item(s)" }

        withContext(Dispatchers.AppUI) {
            // We need to update those immediately before setting the items, on EDT, to avoid timing issues
            // where the target modules or only stable flags get updated after the items data change, thus
            // causing issues when Swing tries to render things (e.g., targetModules doesn't match packages' usages)
            versionColumn.updateData(onlyStable, targetModules)
            actionsColumn.updateData(onlyStable, targetModules, knownRepositoriesInTargetModules, allKnownRepositories)
            latestTargetModules = targetModules

            val currentSelectedPackage = _selectedPackage.value
            tableModel.items = displayItems
            if (displayItems.isEmpty()) {
                clearSelection()
                return@withContext
            }

            autosizeColumnsAt(autosizingColumnsIndices)

            if (currentSelectedPackage == null) return@withContext

            val indexToSelect = withContext(Dispatchers.Default) {
                var indexToSelect: Int? = null
                for ((index, item) in displayItems.withIndex()) {
                    if (item.packageModel.identifier == currentSelectedPackage.packageModel.identifier) {
                        logDebug(traceInfo, "PackagesTable#displayData()") { "Found previously selected package at index $index" }
                        indexToSelect = index
                    }
                }
                indexToSelect
            }

            if (indexToSelect != null) {
                selectedIndex = indexToSelect
            } else {
                logDebug(traceInfo, "PackagesTable#displayData()") { "Previous selection not available anymore, clearing..." }
                clearSelection()
            }

            updateAndRepaint()
        }
    }

    private fun computeDisplayItems(
        packages: List<PackageModel>,
        onlyStable: Boolean,
        targetModules: TargetModules,
        traceInfo: TraceInfo
    ): List<PackagesTableItem<*>> {
        logDebug(traceInfo, "PackagesTable#computeDisplayItems()") { "Creating item models for ${packages.size} item(s)" }

        if (targetModules is TargetModules.None) {
            logDebug(traceInfo, "PackagesTable#computeDisplayItems()") {
                "Current target modules is None, no items models to compute"
            }
            return emptyList()
        }
        logDebug(traceInfo, "PackagesTable#computeDisplayItems()") {
            "Current target modules value: ${targetModules.javaClass.simpleName} " +
                "${targetModules.modules.map { it.projectModule.name }}"
        }

        val modules = targetModules.modules

        val availableScopes = modules.flatMap { it.projectModule.moduleType.scopes(project) }
            .map { rawScope -> PackageScope.from(rawScope) }

        val mixedBuildSystems = targetModules.isMixedBuildSystems
        val defaultScope = if (!mixedBuildSystems) {
            PackageScope.from(modules.first().projectModule.moduleType.defaultScope(project))
        } else {
            PackageScope.Missing
        }

        return packages.map { packageModel ->
            when (packageModel) {
                is PackageModel.Installed -> {
                    val installedScopes = packageModel.declaredScopes(modules)
                    val selectedPackageModel = packageModel.toSelectedPackageModel(installedScopes, defaultScope, mixedBuildSystems)
                    PackagesTableItem.InstalledPackage(selectedPackageModel, installedScopes, defaultScope)
                }
                is PackageModel.SearchResult -> {
                    val selectedPackageModel = packageModel.toSelectedPackageModel(onlyStable, defaultScope, mixedBuildSystems)
                    PackagesTableItem.InstallablePackage(selectedPackageModel, availableScopes, defaultScope)
                }
            }
        }
    }

    private fun PackageModel.Installed.declaredScopes(modules: List<ModuleModel>): List<PackageScope> =
        if (modules.isNotEmpty()) {
            findUsagesIn(modules).map { it.scope }
        } else {
            usageInfo.map { it.scope }
        }
            .distinct()
            .sorted()

    private fun PackageModel.Installed.toSelectedPackageModel(
        installedScopes: List<PackageScope>,
        defaultScope: PackageScope,
        mixedBuildSystems: Boolean
    ): SelectedPackageModel<PackageModel.Installed> =
        SelectedPackageModel(
            packageModel = this,
            selectedVersion = getLatestInstalledVersion(),
            selectedScope = installedScopes.firstOrNull() ?: defaultScope,
            mixedBuildSystemTargets = mixedBuildSystems
        )

    private fun PackageModel.SearchResult.toSelectedPackageModel(
        onlyStable: Boolean,
        defaultScope: PackageScope,
        mixedBuildSystems: Boolean
    ): SelectedPackageModel<PackageModel.SearchResult> =
        SelectedPackageModel(
            packageModel = this,
            selectedVersion = getLatestAvailableVersion(onlyStable) ?: PackageVersion.Missing,
            selectedScope = defaultScope,
            mixedBuildSystemTargets = mixedBuildSystems
        )

    val hasInstalledItems: Boolean
        get() = tableModel.items.any { it is PackagesTableItem.InstalledPackage }

    val firstPackageIndex: Int
        get() = tableModel.items.indexOfFirst { it is PackagesTableItem.InstalledPackage }

    var selectedIndex: Int
        get() = selectedRow
        set(value) {
            if (tableModel.items.isNotEmpty() && (0 until tableModel.items.count()).contains(value)) {
                setRowSelectionInterval(value, value)
            } else {
                clearSelection()
            }
        }

    override fun getData(dataId: String): Any? {
        val item = getSelectedTableItem() ?: return null

        return when {
            !item.canProvideDataFor(dataId) -> null
            item is PackagesTableItem.InstalledPackage -> {
                val targetModules = latestTargetModules
                if (targetModules is TargetModules.One) {
                    item.getData(dataId, targetModules.module.projectModule)
                } else {
                    item.getData(dataId) // Fallback strategy
                }
            }
            item is PackagesTableItem.InstallablePackage -> item.getData(dataId)
            else -> null
        }
    }

    override fun performCopy(dataContext: DataContext) {
        getSelectedTableItem()?.performCopy(dataContext)
    }

    override fun isCopyEnabled(dataContext: DataContext) = getSelectedTableItem()?.isCopyEnabled(dataContext) ?: false

    override fun isCopyVisible(dataContext: DataContext) = getSelectedTableItem()?.isCopyVisible(dataContext) ?: false

    private fun getSelectedTableItem(): PackagesTableItem<*>? {
        if (selectedIndex == -1) {
            return null
        }
        return tableModel.getValueAt(selectedIndex, 0) as? PackagesTableItem<*>
    }

    private fun updatePackageScope(packageModel: PackageModel, newScope: PackageScope) {
        if (packageModel is PackageModel.Installed) {
            val operations = operationFactory.createChangePackageScopeOperations(packageModel, newScope, latestTargetModules, repoToInstall = null)

            logDebug("PackagesTable#updatePackageScope()") {
                "The user has selected a new scope for ${packageModel.identifier}: '$newScope'. This resulted in ${operations.size} operation(s)."
            }
            operationExecutor.executeOperations(operations)
        } else if (packageModel is PackageModel.SearchResult) {
            tableModel.replaceItemMatching(packageModel) { item ->
                when (item) {
                    is PackagesTableItem.InstalledPackage -> {
                        throw IllegalStateException("Expecting a search result item model, got an installed item model")
                    }
                    is PackagesTableItem.InstallablePackage -> item.copy(item.selectedPackageModel.copy(selectedScope = newScope))
                }
            }

            logDebug("PackagesTable#updatePackageScope()") {
                "The user has selected a new scope for search result ${packageModel.identifier}: '$newScope'."
            }
            updateAndRepaint()
        }
    }

    private fun updatePackageVersion(packageModel: PackageModel, newVersion: PackageVersion) {
        when (packageModel) {
            is PackageModel.Installed -> {
                val operations = packageModel.usageInfo.flatMap {
                    val repoToInstall = knownRepositoriesInTargetModules.repositoryToAddWhenInstallingOrUpgrading(
                        packageModel,
                        newVersion,
                        allKnownRepositories
                    )

                    operationFactory.createChangePackageVersionOperations(
                        packageModel = packageModel,
                        newVersion = newVersion,
                        targetModules = latestTargetModules,
                        repoToInstall = repoToInstall
                    )
                }

                logDebug("PackagesTable#updatePackageVersion()") {
                    "The user has selected a new version for ${packageModel.identifier}: '$newVersion'. " +
                        "This resulted in ${operations.size} operation(s)."
                }
                operationExecutor.executeOperations(operations)
            }
            is PackageModel.SearchResult -> {
                tableModel.replaceItemMatching(packageModel) { item ->
                    when (item) {
                        is PackagesTableItem.InstalledPackage -> item.copy(item.selectedPackageModel.copy(selectedVersion = newVersion))
                        is PackagesTableItem.InstallablePackage -> item.copy(item.selectedPackageModel.copy(selectedVersion = newVersion))
                    }
                }

                logDebug("PackagesTable#updatePackageVersion()") {
                    "The user has selected a new version for search result ${packageModel.identifier}: '$newVersion'."
                }
                updateAndRepaint()
            }
        }
    }

    private fun executeUpdateActionColumnOperations(operations: List<PackageSearchOperation<*>>) {
        logDebug("PackagesTable#updatePackageVersion()") {
            "The user has clicked the update action for a package. This resulted in ${operations.size} operation(s)."
        }
        operationExecutor.executeOperations(operations)
    }

    private fun PackagesTableItem<*>.toSelectedPackageModule() = SelectedPackageModel(
        packageModel = packageModel,
        selectedVersion = (tableModel.columns[2] as VersionColumn).valueOf(this).selectedVersion,
        selectedScope = (tableModel.columns[1] as ScopeColumn).valueOf(this).selectedScope,
        mixedBuildSystemTargets = latestTargetModules.isMixedBuildSystems
    )

    private fun applyColumnSizes(tW: Int, columns: List<TableColumn>, weights: List<Float>) {
        require(columnWeights.size == columns.size) {
            "Column weights count != columns count! We have ${columns.size} columns, ${columnWeights.size} weights"
        }

        for (column in columns) {
            column.preferredWidth = (weights[column.modelIndex] * tW).roundToInt()
        }
    }

    override fun dispose() {
        logDebug("PackagesTable#dispose()") { "Disposing PackagesTable..." }
        coroutineContext.cancel(CancellationException("Disposing"))
    }

}
