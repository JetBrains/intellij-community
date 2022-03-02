package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.TableUtil
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.KnownRepositories
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.OperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.ActionsColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.NameColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.ScopeColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.VersionColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.updateAndRepaint
import com.jetbrains.packagesearch.intellij.plugin.ui.util.onMouseMotion
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.uiStateSource
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.awt.Cursor
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

internal typealias SearchResultStateChangeListener =
    (PackageModel.SearchResult, NormalizedPackageVersion<*>?, PackageScope?) -> Unit

@Suppress("MagicNumber") // Swing dimension constants
internal class PackagesTable(
    private val project: Project,
    private val operationExecutor: OperationExecutor,
    private val onSearchResultStateChanged: SearchResultStateChangeListener
) : JBTable(), CopyProvider, DataProvider {

    private var lastSelectedDependency: UnifiedDependency? = null

    private val operationFactory = PackageSearchOperationFactory()

    private val tableModel: PackagesTableModel
        get() = model as PackagesTableModel

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

    private val actionsColumn = ActionsColumn(project, operationExecutor = ::executeActionColumnOperations)

    private val actionsColumnIndex: Int

    private val autosizingColumnsIndices: List<Int>

    private var targetModules: TargetModules = TargetModules.None

    private var knownRepositoriesInTargetModules = KnownRepositories.InTargetModules.EMPTY

    val selectedPackageStateFlow = MutableStateFlow<UiPackageModel<*>?>(null)

    private val listSelectionListener = ListSelectionListener {
        val item = getSelectedTableItem()
        if (selectedIndex >= 0 && item != null) {
            TableUtil.scrollSelectionToVisible(this)
            updateAndRepaint()
            selectedPackageStateFlow.tryEmit(item.uiPackageModel)
        } else {
            selectedPackageStateFlow.tryEmit(null)
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
            nameColumn = nameColumn,
            scopeColumn = scopeColumn,
            versionColumn = versionColumn,
            actionsColumn = actionsColumn
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
            if (item is PackagesTableItem<*>) {
                val rawIdentifier = item.packageModel.identifier.rawValue
                val name = item.packageModel.remoteInfo?.name?.takeIf { !it.equals(rawIdentifier, ignoreCase = true) }
                if (name != null) rawIdentifier + name else rawIdentifier
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
            onMouseMoved = { mouseEvent ->
                val point = mouseEvent.point
                val hoverColumn = columnAtPoint(point)
                val hoverRow = rowAtPoint(point)

                if (tableModel.items.isEmpty() || hoverRow < 0) {
                    cursor = Cursor.getDefaultCursor()
                    return@onMouseMotion
                }

                val isHoveringActionsColumn = hoverColumn == actionsColumnIndex
                cursor = if (isHoveringActionsColumn) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
            }
        )
        project.uiStateSource.selectedDependencyFlow.onEach { lastSelectedDependency = it }
            .onEach { setSelection(it) }
            .flowOn(Dispatchers.EDT)
            .launchIn(project.lifecycleScope)
    }

    private fun setSelection(lastSelectedDependencyCopy: UnifiedDependency): Boolean {
        val index = tableModel.items.map { it.uiPackageModel }
            .indexOfFirst {
                it is UiPackageModel.Installed &&
                    it.selectedVersion.displayName == lastSelectedDependencyCopy.coordinates.version &&
                    it.packageModel.artifactId == lastSelectedDependencyCopy.coordinates.artifactId &&
                    it.packageModel.groupId == lastSelectedDependencyCopy.coordinates.groupId &&
                    (it.selectedScope.displayName == lastSelectedDependencyCopy.scope ||
                        (it.selectedScope.displayName == "[default]" && lastSelectedDependencyCopy.scope == null))
            }
        val indexFound = index >= 0
        if (indexFound) setRowSelectionInterval(index, index)
        return indexFound
    }

    override fun getCellRenderer(row: Int, column: Int): TableCellRenderer =
        tableModel.columns.getOrNull(column)?.getRenderer(tableModel.items[row]) ?: DefaultTableCellRenderer()

    override fun getCellEditor(row: Int, column: Int): TableCellEditor? =
        tableModel.columns.getOrNull(column)?.getEditor(tableModel.items[row])

    internal data class ViewModel(
        val items: TableItems,
        val onlyStable: Boolean,
        val targetModules: TargetModules,
        val knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
    ) {

        data class TableItems(
            val items: List<PackagesTableItem<*>>,
        ) : List<PackagesTableItem<*>> by items {

            companion object {

                val EMPTY = TableItems(items = emptyList())
            }
        }
    }

    fun display(viewModel: ViewModel) {
        knownRepositoriesInTargetModules = viewModel.knownRepositoriesInTargetModules
        targetModules = viewModel.targetModules

        // We need to update those immediately before setting the items, on EDT, to avoid timing issues
        // where the target modules or only stable flags get updated after the items data change, thus
        // causing issues when Swing tries to render things (e.g., targetModules doesn't match packages' usages)
        versionColumn.updateData(viewModel.onlyStable, viewModel.targetModules)
        actionsColumn.updateData(
            viewModel.onlyStable,
            viewModel.targetModules,
            viewModel.knownRepositoriesInTargetModules
        )

        selectionModel.removeListSelectionListener(listSelectionListener)
        tableModel.items = viewModel.items

        // TODO size columns

        selectionModel.addListSelectionListener(listSelectionListener)

        lastSelectedDependency?.let { lastSelectedDependencyCopy ->
            if (setSelection(lastSelectedDependencyCopy)) {
                lastSelectedDependency = null
            }
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

    private fun updatePackageScope(uiPackageModel: UiPackageModel<*>, newScope: PackageScope) {
        when (uiPackageModel) {
            is UiPackageModel.Installed -> {
                val operations = operationFactory.createChangePackageScopeOperations(
                    packageModel = uiPackageModel.packageModel,
                    newScope = newScope,
                    targetModules = targetModules,
                    repoToInstall = null
                )

                logDebug("PackagesTable#updatePackageScope()") {
                    "The user has selected a new scope for ${uiPackageModel.identifier}: '$newScope'. This resulted in ${operations.size} operation(s)."
                }
                operationExecutor.executeOperations(operations)
            }
            is UiPackageModel.SearchResult -> {
                val selectedVersion = uiPackageModel.selectedVersion
                onSearchResultStateChanged(uiPackageModel.packageModel, selectedVersion, newScope)

                logDebug("PackagesTable#updatePackageScope()") {
                    "The user has selected a new scope for search result ${uiPackageModel.identifier}: '$newScope'."
                }
                updateAndRepaint()
            }
        }
    }

    private fun updatePackageVersion(uiPackageModel: UiPackageModel<*>, newVersion: NormalizedPackageVersion<*>) {
        when (uiPackageModel) {
            is UiPackageModel.Installed -> {
                val operations = uiPackageModel.packageModel.usageInfo.flatMap {
                    val repoToInstall = knownRepositoriesInTargetModules.repositoryToAddWhenInstallingOrUpgrading(
                        project = project,
                        packageModel = uiPackageModel.packageModel,
                        selectedVersion = newVersion.originalVersion
                    )

                    operationFactory.createChangePackageVersionOperations(
                        packageModel = uiPackageModel.packageModel,
                        newVersion = newVersion.originalVersion,
                        targetModules = targetModules,
                        repoToInstall = repoToInstall
                    )
                }

                logDebug("PackagesTable#updatePackageVersion()") {
                    "The user has selected a new version for ${uiPackageModel.identifier}: '$newVersion'. " +
                        "This resulted in ${operations.size} operation(s)."
                }
                operationExecutor.executeOperations(operations)
            }
            is UiPackageModel.SearchResult -> {
                onSearchResultStateChanged(uiPackageModel.packageModel, newVersion, uiPackageModel.selectedScope)

                logDebug("PackagesTable#updatePackageVersion()") {
                    "The user has selected a new version for search result ${uiPackageModel.identifier}: '$newVersion'."
                }
                updateAndRepaint()
            }
        }
    }

    private fun executeActionColumnOperations(operations: Deferred<List<PackageSearchOperation<*>>>) {
        logDebug("PackagesTable#executeActionColumnOperations()") {
            "The user has clicked the action for a package. This resulted in many operation(s)."
        }
        operationExecutor.executeOperations(operations)
    }

    private fun applyColumnSizes(tW: Int, columns: List<TableColumn>, weights: List<Float>) {
        require(columnWeights.size == columns.size) {
            "Column weights count != columns count! We have ${columns.size} columns, ${columnWeights.size} weights"
        }

        for (column in columns) {
            column.preferredWidth = (weights[column.modelIndex] * tW).roundToInt()
        }
    }
}
