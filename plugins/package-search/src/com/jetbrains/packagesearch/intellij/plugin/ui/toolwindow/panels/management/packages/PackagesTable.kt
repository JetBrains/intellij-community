package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.TableUtil
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.KnownRepositories
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
import com.jetbrains.packagesearch.intellij.plugin.ui.util.Displayable
import com.jetbrains.packagesearch.intellij.plugin.ui.util.autosizeColumnsAt
import com.jetbrains.packagesearch.intellij.plugin.ui.util.onMouseMotion
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import com.jetbrains.packagesearch.intellij.plugin.util.AppUI
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionListener
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumn
import kotlin.math.roundToInt

internal typealias SelectedPackageModelListener = (SelectedPackageModel<*>?) -> Unit

@Suppress("MagicNumber") // Swing dimension constants
internal class PackagesTable(
    project: Project,
    private val operationExecutor: OperationExecutor,
    operationFactory: PackageSearchOperationFactory,
    private val onItemSelectionChanged: SelectedPackageModelListener
) : JBTable(), CopyProvider, DataProvider, Displayable<PackagesTable.ViewModel> {

    private val operationFactory = PackageSearchOperationFactory()

    private val tableModel: PackagesTableModel
        get() = model as PackagesTableModel

    private var selectedPackage: SelectedPackageModel<*>? = null

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
        operationFactory = operationFactory
    )

    private val actionsColumnIndex: Int

    private val autosizingColumnsIndices: List<Int>

    private var targetModules: TargetModules = TargetModules.None
    private var knownRepositoriesInTargetModules = KnownRepositories.InTargetModules.EMPTY
    private var allKnownRepositories = KnownRepositories.All.EMPTY

    private val listSelectionListener = ListSelectionListener {
        val item = getSelectedTableItem()
        if (selectedIndex >= 0 && item != null) {
            TableUtil.scrollSelectionToVisible(this)
            updateAndRepaint()
            selectedPackage = item.toSelectedPackageModule()
            onItemSelectionChanged(selectedPackage)
            PackageSearchEventsLogger.logPackageSelected(item is PackagesTableItem.InstalledPackage)
        } else {
            selectedPackage = null
        }
    }

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

        selectionModel.addListSelectionListener(listSelectionListener)

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
    }

    override fun getCellRenderer(row: Int, column: Int): TableCellRenderer =
        tableModel.columns[column].getRenderer(tableModel.items[row]) ?: DefaultTableCellRenderer()

    override fun getCellEditor(row: Int, column: Int): TableCellEditor? =
        tableModel.columns[column].getEditor(tableModel.items[row])

    internal data class ViewModel(
        val displayItems: List<PackagesTableItem<*>>,
        val onlyStable: Boolean,
        val targetModules: TargetModules,
        val knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
        val allKnownRepositories: KnownRepositories.All,
        val traceInfo: TraceInfo
    )

    override suspend fun display(viewModel: ViewModel) = withContext(Dispatchers.AppUI) {
        knownRepositoriesInTargetModules = viewModel.knownRepositoriesInTargetModules
        allKnownRepositories = viewModel.allKnownRepositories
        targetModules = viewModel.targetModules

        logDebug(viewModel.traceInfo, "PackagesTable#displayData()") { "Displaying ${viewModel.displayItems.size} item(s)" }

        // We need to update those immediately before setting the items, on EDT, to avoid timing issues
        // where the target modules or only stable flags get updated after the items data change, thus
        // causing issues when Swing tries to render things (e.g., targetModules doesn't match packages' usages)
        versionColumn.updateData(viewModel.onlyStable, viewModel.targetModules)
        actionsColumn.updateData(viewModel.onlyStable, viewModel.targetModules, knownRepositoriesInTargetModules, allKnownRepositories)

        val previouslySelectedIdentifier = selectedPackage?.packageModel?.identifier
        selectionModel.removeListSelectionListener(listSelectionListener)
        tableModel.items = viewModel.displayItems

        if (viewModel.displayItems.isEmpty() || previouslySelectedIdentifier == null) {
            selectionModel.addListSelectionListener(listSelectionListener)
            onItemSelectionChanged(null)
            return@withContext
        }

        autosizeColumnsAt(autosizingColumnsIndices)

        var indexToSelect: Int? = null

        // TODO factor out with a lambda
        for ((index, item) in viewModel.displayItems.withIndex()) {
            if (item.packageModel.identifier == previouslySelectedIdentifier) {
                logDebug(viewModel.traceInfo, "PackagesTable#displayData()") { "Found previously selected package at index $index" }
                indexToSelect = index
            }
        }

        selectionModel.addListSelectionListener(listSelectionListener)
        if (indexToSelect != null) {
            selectedIndex = indexToSelect
        } else {
            logDebug(viewModel.traceInfo, "PackagesTable#displayData()") { "Previous selection not available anymore, clearing..." }
        }
        updateAndRepaint()
    }

    override fun getData(dataId: String): Any? = when {
        PlatformDataKeys.COPY_PROVIDER.`is`(dataId) -> this
        else -> null
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
            val operations = operationFactory.createChangePackageScopeOperations(packageModel, newScope, targetModules, repoToInstall = null)

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
                        targetModules = targetModules,
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
        mixedBuildSystemTargets = targetModules.isMixedBuildSystems
    )

    private fun applyColumnSizes(tW: Int, columns: List<TableColumn>, weights: List<Float>) {
        require(columnWeights.size == columns.size) {
            "Column weights count != columns count! We have ${columns.size} columns, ${columnWeights.size} weights"
        }

        for (column in columns) {
            column.preferredWidth = (weights[column.modelIndex] * tW).roundToInt()
        }
    }
}
