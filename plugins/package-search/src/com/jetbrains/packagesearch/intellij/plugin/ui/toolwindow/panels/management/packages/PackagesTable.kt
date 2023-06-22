/**
 * ****************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations
 * under the License.
 * ****************************************************************************
 */

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.TableUtil
import com.intellij.ui.hover.TableHoverListener
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.*
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.PackageManagementOperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.PackageManagementPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.changePackage
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.ActionsColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.NameColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.ScopeColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.ScoreColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.VersionColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.updateAndRepaint
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.modifyPackages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.idea.packagesearch.SortMetric
import java.awt.Color
import java.awt.Cursor
import java.awt.KeyboardFocusManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JTable
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
    private val onSearchResultStateChanged: SearchResultStateChangeListener
) : JBTable(), CopyProvider, DataProvider {

    private var lastSelectedDependency: UnifiedDependency? = null

    private val tableModel: PackagesTableModel
        get() = model as PackagesTableModel

    var transferFocusUp: () -> Unit = { transferFocusBackward() }

    private val columnWeights = listOf(
        .45f, // Name column
        .15f, // Scope column
        .15f, // Version column
        .15f, // Score column
        .10f // Actions column
    )

    private val nameColumn = NameColumn()

    private val scopeColumn = ScopeColumn { packageModel, newScope -> updatePackageScope(packageModel, newScope) }

    private val scoreColumn = ScoreColumn()

    private val versionColumn = VersionColumn { packageModel, newVersion ->
        updatePackageVersion(packageModel, newVersion)
    }

    private val actionsColumn = ActionsColumn(operationExecutor = ::executeActionColumnOperations)

    private val actionsColumnIndex: Int

    private val autosizingColumnsIndices: List<Int>

    private var targetModules: TargetModules = TargetModules.None

    private var knownRepositoriesInTargetModules: Map<PackageSearchModule, List<RepositoryModel>> = emptyMap()
    private var allKnownRepositories: List<RepositoryModel> = emptyList()

    private val listSelectionListener = ListSelectionListener {
        project.lifecycleScope.launch {
            val item = getSelectedTableItem()
            val packageModel = if (selectedIndex >= 0 && item != null) {
                withContext(Dispatchers.EDT) {
                    TableUtil.scrollSelectionToVisible(this@PackagesTable)
                    updateAndRepaint()
                }
                item.uiPackageModel
            } else null
            project.service<PackageManagementPanel.UIState>().packagesListPanel.table.selectedPackageStateFlow.emit(packageModel)
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
            scoreColumn = scoreColumn,
            actionsColumn = actionsColumn
        )

        val columnInfos = tableModel.columnInfos
        actionsColumnIndex = columnInfos.indexOf(actionsColumn)
        autosizingColumnsIndices = listOf(
            columnInfos.indexOf(scopeColumn),
            columnInfos.indexOf(versionColumn),
            columnInfos.indexOf(scoreColumn),
            actionsColumnIndex
        )

        setTableHeader(InvisibleResizableHeader())
        getTableHeader().apply {
            reorderingAllowed = false
            resizingAllowed = true
        }

        columnSelectionAllowed = false

        setShowGrid(false)
        rowHeight = JBUI.getInt(
            "PackageSearch.PackagesList.rowHeight",
            JBUI.getInt("List.rowHeight", if (PackageSearchUI.isNewUI) 24.scaled() else 20.scaled())
        )

        setExpandableItemsEnabled(false)
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        putClientProperty("terminateEditOnFocusLost", true)

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

        TableSpeedSearch.installOn(this) { item, _ ->
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

        val hoverListener = object : TableHoverListener() {
            override fun onHover(table: JTable, row: Int, column: Int) {
                val currentCursor = cursor
                if (tableModel.items.isEmpty() || row < 0) {
                    if (currentCursor != Cursor.getDefaultCursor()) cursor = Cursor.getDefaultCursor()
                    return
                }

                val isHoveringActionsColumn = column == actionsColumnIndex
                val desiredCursor = if (isHoveringActionsColumn) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
                if (currentCursor != desiredCursor) cursor = desiredCursor
            }
        }
        hoverListener.addTo(this)
    }

    internal fun setSelection(lastSelectedDependencyCopy: UnifiedDependency): Boolean {
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
        val sortMetric: SortMetric,
        val targetModules: TargetModules,
        val knownRepositoriesInTargetModules: Map<PackageSearchModule, List<RepositoryModel>>,
        val allKnownRepositories: List<RepositoryModel>,
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
        scoreColumn.updateData(viewModel.sortMetric)
        actionsColumn.updateData(viewModel)

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

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

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
                project.modifyPackages {
                    targetModules.modules.forEach { module ->
                        uiPackageModel.filterUsagesInfoByModuleAndScope(module)
                            .forEach { usage ->
                                changePackage(
                                    groupId = uiPackageModel.packageModel.groupId,
                                    artifactId = uiPackageModel.packageModel.artifactId,
                                    version = usage.declaredVersion.originalVersion,
                                    scope = usage.scope,
                                    packageSearchModule = module,
                                    newScope = newScope,
                                )
                            }
                    }
                }
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
                project.modifyPackages {
                    val repoToInstallByModule = uiPackageModel.packageModel
                        .repositoryToAddWhenInstallingOrUpgrading(targetModules, knownRepositoriesInTargetModules, allKnownRepositories)
                    targetModules.modules.forEach { module ->
                        var operationsCount = 0
                        repoToInstallByModule[module]?.let { repoToInstall ->
                            installRepository(UnifiedDependencyRepository(repoToInstall.id, repoToInstall.name, repoToInstall.url), module)
                        }
                        uiPackageModel.filterUsagesInfoByModuleAndScope(module)
                            .forEach { usage ->
                                operationsCount++
                                changePackage(
                                    groupId = uiPackageModel.packageModel.groupId,
                                    artifactId = uiPackageModel.packageModel.artifactId,
                                    version = usage.declaredVersion.originalVersion,
                                    scope = usage.scope,
                                    packageSearchModule = module,
                                    newVersion = newVersion.originalVersion,
                                )
                            }
                        logDebug("PackagesTable#updatePackageVersion()") {
                            "The user has selected a new version for ${uiPackageModel.identifier}: '$newVersion'. " +
                                "This resulted in operationsCount operation(s)."
                        }
                    }
                }
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

    private fun UiPackageModel<PackageModel.Installed>.filterUsagesInfoByModuleAndScope(module: PackageSearchModule) =
        packageModel.usagesByModule
            .getOrDefault(module.nativeModule, emptyList())
            .asSequence()
            .filter { it.scope == selectedScope }

    private fun executeActionColumnOperations(operations: PackageManagementOperationExecutor.() -> Unit) {
        logDebug("PackagesTable#executeActionColumnOperations()") {
            "The user has clicked the action for a package. This resulted in many operation(s)."
        }
        project.modifyPackages(operations)
    }

    private fun applyColumnSizes(tW: Int, columns: List<TableColumn>, weights: List<Float>) {
        require(columnWeights.size == columns.size) {
            "Column weights count != columns count! We have ${columns.size} columns, ${columnWeights.size} weights"
        }

        for (column in columns) {
            column.preferredWidth = (weights[column.modelIndex] * tW).roundToInt()
        }
    }

    override fun getBackground(): Color =
        if (PackageSearchUI.isNewUI) PackageSearchUI.Colors.panelBackground else UIUtil.getTableBackground()

    override fun getForeground(): Color =
        if (PackageSearchUI.isNewUI) UIUtil.getListForeground() else UIUtil.getTableForeground()

    override fun getSelectionBackground(): Color =
        if (PackageSearchUI.isNewUI) UIUtil.getListSelectionBackground(true) else UIUtil.getTableSelectionBackground(true)

    override fun getSelectionForeground(): Color =
        if (PackageSearchUI.isNewUI) NamedColorUtil.getListSelectionForeground(true) else UIUtil.getTableSelectionForeground(true)

    override fun getHoveredRowBackground() = null // Renderers will take care of it
}
