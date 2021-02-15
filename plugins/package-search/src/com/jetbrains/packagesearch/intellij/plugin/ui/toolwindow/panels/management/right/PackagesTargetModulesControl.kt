package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.right

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.PopupHandler
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.table.IconTableCellRenderer
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.looksLikeGradleVariable
import com.jetbrains.packagesearch.intellij.plugin.ui.ButtonColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.ComboBoxColumn
import com.jetbrains.packagesearch.intellij.plugin.ui.RiderUI
import com.jetbrains.packagesearch.intellij.plugin.ui.colored
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageOperationTarget
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageOperationTargetScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageOperationUtility
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchToolWindowModel
import com.jetbrains.rd.util.reactive.Property
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.ArrayList
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.border.Border
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellRenderer
import kotlin.math.max

class PackagesTargetModulesControl(private val viewModel: PackageSearchToolWindowModel) : JPanel(BorderLayout()) {

    companion object {
        private val columnClass = arrayOf(
            Icon::class.java,
            String::class.java,
            String::class.java,
            PackageOperationTargetScope::class.java,
            Boolean::class.java,
            Boolean::class.java
        )
        private const val titleColumnPreferredWidth = 350
        private const val scopeComboSize = 180
        private const val versionColumnPreferredWidth = 105
        private const val buttonSize = 24
        private const val tableRowHeight = 24
    }

    private object Index {
        const val moduleIcon = 0
        const val title = 1
        const val scope = 2
        const val version = 3
        const val apply = 4
        const val remove = 5
    }

    private val packageOperationUtility = PackageOperationUtility(viewModel)

    val items = ArrayList<PackageOperationTarget>()
    val targetVersion = Property("")

    private var installationSummary = ""

    private fun getOperation(row: Int, column: Int): PackageOperation? = when (column) {
        Index.apply -> items[row].getApplyOperation(targetVersion.value)
        Index.remove -> items[row].getRemoveOperation()
        else -> null
    }

    private class ProjectIconTableCellRenderer : IconTableCellRenderer<Icon>() {
        override fun getIcon(value: Icon, table: JTable, row: Int) = value

        override fun getTableCellRendererComponent(table: JTable, value: Any, selected: Boolean, focus: Boolean, row: Int, column: Int): Component {
            super.getTableCellRendererComponent(table, value, selected, false, row, column)
            text = ""
            return colored(table, selected)
        }

        override fun isCenterAlignment() = true
    }

    private class SimpleTableCellRenderer(
        private val highlight: Boolean,
        private val cellBorder: Border = JBEmptyBorder(0)
    ) : ColoredTableCellRenderer() {

        override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
            if (value !is String) return

            colored(table, selected)
            border = cellBorder
            preferredSize = Dimension(preferredSize.width, max(preferredSize.height, JBUI.scale(buttonSize + 2)))
            append(value, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            if (highlight) {
                SpeedSearchUtil.applySpeedSearchHighlighting(table, this, true, selected)
            }
        }
    }

    inner class TargetScopeComboBoxColumn(table: JTable, comboBoxSize: Int) : ComboBoxColumn(table, comboBoxSize) {

        override fun customizeComboBox(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
            comboBox: ComboBox<*>
        ): ComboBox<*> {
            if (value is PackageOperationTargetScope) {
                comboBox.isEditable = true
                comboBox.model = value.scopesModel
                comboBox.selectedItem = value.getSelectedScope()
            }

            return comboBox
        }

        override fun actionPerformed(row: Int, column: Int, selectedValue: Any?) {
            if (cellEditorValue is PackageOperationTargetScope) {
                items[row].targetScope.scopesModel.selectedItem = selectedValue
            }
        }
    }

    inner class TargetProjectsButtonColumn(table: JTable, buttonSize: Int, viewModel: PackageSearchToolWindowModel) : ButtonColumn(
        table,
        buttonSize
    ) {

        override fun actionPerformed(row: Int, column: Int) =
            packageOperationUtility.doOperation(getOperation(row, column), listOf(items[row]), targetVersion.value)

        override fun getIcon(row: Int, column: Int) =
            getOperation(row, column)?.icon

        init {
            viewModel.isBusy.advise(viewModel.lifetime) {
                renderButton.isEnabled = !it
                editButton.isEnabled = !it
            }
        }
    }

    private val table = object : JBTable(object : AbstractTableModel() {
        override fun getRowCount() = items.size
        override fun getColumnCount() = columnClass.size

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = when (columnIndex) {
            Index.moduleIcon -> items[rowIndex].projectModule.moduleType.packageIcon
            Index.title -> items[rowIndex].projectModule.getFullName()
            Index.scope -> items[rowIndex].targetScope
            Index.version -> items[rowIndex].version
            Index.apply, Index.remove -> false
            else -> throw IndexOutOfBoundsException("columnIndex")
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int) = when (columnIndex) {
            Index.scope, Index.apply, Index.remove -> true
            else -> false
        }
    }) {
        override fun prepareRenderer(renderer: TableCellRenderer, row: Int, column: Int): Component {
            val localRenderer = super.prepareRenderer(renderer, row, column)
            val op = getOperation(row, column)
            if (localRenderer is JComponent && op != null) {
                localRenderer.toolTipText = op.description
            } else if (localRenderer is SimpleTableCellRenderer && column == Index.title) {
                localRenderer.toolTipText = localRenderer.getCharSequence(false).toString()
            }
            return localRenderer
        }
    }

    override fun getBackground() = RiderUI.UsualBackgroundColor

    fun clearSelection() = table.clearSelection()

    private val selectedItems: List<PackageOperationTarget> get() = table.selectedRows.map { items[it] }

    init {
        table.apply {
            createDefaultColumnsFromModel()
            setExpandableItemsEnabled(false)
            intercellSpacing = Dimension(0, 2)
            rowHeight = tableRowHeight
            columnModel.getColumn(Index.moduleIcon).apply {
                cellRenderer = ProjectIconTableCellRenderer()
                resizable = false
                minWidth = JBUI.scale(buttonSize)
                maxWidth = JBUI.scale(buttonSize)
                preferredWidth = JBUI.scale(buttonSize)
            }
            columnModel.getColumn(Index.title).apply {
                cellRenderer = SimpleTableCellRenderer(highlight = true)
                preferredWidth = JBUI.scale(titleColumnPreferredWidth)
            }
            TargetScopeComboBoxColumn(table, scopeComboSize).install(Index.scope)
            columnModel.getColumn(Index.version).apply {
                @Suppress("MagicNumber") // Gotta love Swing APIs
                cellRenderer = SimpleTableCellRenderer(highlight = false, cellBorder = JBEmptyBorder(0, 4, 0, 0))
                preferredWidth = JBUI.scale(versionColumnPreferredWidth)
            }
            TargetProjectsButtonColumn(table, buttonSize, viewModel).install(Index.apply, Index.remove)
            selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
            putClientProperty("terminateEditOnFocusLost", true)
            setShowGrid(false)

            // By default, JTable uses Tab/Shift-Tab for navigation between cells; Ctrl-Tab/Ctrl-Shift-Tab allows to break out of the JTable.
            // In this table, we don't want to navigate between cells; moreover we can't break out with Ctrl-Tab because of Switcher.
            // So, we override the traversal keys by default values.
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
                    if (items.size > 0 && selectedRows.isEmpty()) {
                        table.setRowSelectionInterval(0, 0)
                    }
                }
            })

            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent?) {
                    if (e != null) {
                        if (e.keyCode == KeyEvent.VK_SPACE || e.keyCode == KeyEvent.VK_ENTER) {
                            showPopupForSelection()
                        }
                        if (e.keyCode == KeyEvent.VK_DELETE) {
                            packageOperationUtility.doOperation(
                                packageOperationUtility.getRemoveOperation(selectedItems),
                                selectedItems,
                                targetVersion.value
                            )
                        }
                    }
                }

                override fun keyTyped(e: KeyEvent) {
                    if (e.keyChar == ' ') {
                        e.keyChar = '\u0000'
                    }
                }
            })
            addMouseListener(object : PopupHandler() {
                override fun invokePopup(comp: Component?, x: Int, y: Int) = showPopupForSelection(Point(x, y))
            })

            TableSpeedSearch(this) { o, cell ->
                if (cell.column == Index.title) o.toString() else null
            }.apply {
                comparator = SpeedSearchComparator(false)
            }
        }

        viewModel.isBusy.advise(viewModel.lifetime) { refreshUI() }
        targetVersion.advise(viewModel.lifetime) { refreshUI() }
        add(table, BorderLayout.CENTER)
    }

    private fun showPopupForSelection(p: Point? = null) {
        val popup = createPopup(selectedItems)
        if (popup.subElements.any()) {
            val location = p ?: JBPopupFactory.getInstance().guessBestPopupLocation(table).getPoint(table)
            popup.show(table, location.x, location.y)
        }
    }

    private fun createPopup(items: List<PackageOperationTarget>) = JPopupMenu().apply {
        val operationTargetVersion = targetVersion.value
        val operations = listOfNotNull(
            packageOperationUtility.getApplyOperation(items, operationTargetVersion),
            packageOperationUtility.getRemoveOperation(items)
        )
        operations.forEach {
            val projectsMessage = when (items.size) {
                1 -> items.first().projectModuleTitle
                else -> PackageSearchBundle.message("packagesearch.ui.toolwindow.selectedModules").toLowerCase()
            }

            @Suppress("HardCodedStringLiteral") // Formatting into a non-locale-specific format
            val message = it.htmlDescription.replace("</html>", " - <b>$projectsMessage</b></html>")
            add(RiderUI.menuItem(message, it.icon) { packageOperationUtility.doOperation(it, items, operationTargetVersion) })
        }
    }

    fun getApplyPackageOperation() = packageOperationUtility.getApplyOperation(getPackageOperationTargets(), targetVersion.value)

    fun getRemovePackageOperation() = packageOperationUtility.getRemoveOperation(getPackageOperationTargets())

    fun getPackageOperationTargets(hasPackage: Boolean = true) = items.filter {
        val version = it.version
        val hasPackageAndValidLookingVersion = hasPackage && version.isNotBlank() && !looksLikeGradleVariable(version)
        val hasBlankVersionAndNoPackage = !hasPackage && version.isBlank()

        hasPackageAndValidLookingVersion || hasBlankVersionAndNoPackage
    }

    fun refreshUI() {
        val selectedDependency = viewModel.searchResults.value[viewModel.selectedPackage.value]
        val selectedDependencyRemoteInfo = selectedDependency?.remoteInfo
        val selectedProjectModule = viewModel.selectedProjectModule.value
        val selectedRemoteRepository = viewModel.selectedRemoteRepository.value
        val projectModules = if (selectedProjectModule != null) {
            // Only show selected module
            listOf(selectedProjectModule)
        } else {
            // Show all modules
            viewModel.projectModules.value
        }.filter {
            // Make sure the dependency is supported by the available project module(s)
            if (selectedDependencyRemoteInfo != null) {
                it.moduleType.providesSupportFor(selectedDependencyRemoteInfo)
            } else {
                true
            }
        }

        val packageOperationTargets = if (selectedDependency != null) {
            viewModel.preparePackageOperationTargetsFor(projectModules, selectedDependency, selectedRemoteRepository)
        } else {
            emptyList()
        }

        // Compare previous packageOperationTargets with current.
        // If the underlying dependencies have not changed, there is no need to update them.
        val shouldUpdateItems = items.size == 0 ||
            items.size != projectModules.size ||
            items.any { it.packageSearchDependency != selectedDependency } ||
            installationSummary != selectedDependency?.buildInstallationSummary()

        // Update UI
        val selectedTitles = selectedItems.map { "${it.projectModule.getFullName()}${it.version}${it.installedScope}" }.toHashSet()
        if (shouldUpdateItems) {
            installationSummary = selectedDependency?.buildInstallationSummary() ?: ""
            items.clear()
            items.addAll(packageOperationTargets.sortedBy { it.projectModule.getFullName() })
        }
        refreshSelectedProjects()

        table.clearSelection()
        for ((index, item) in items.withIndex()) {
            if (selectedTitles.contains("${item.projectModule.getFullName()}${item.version}${item.installedScope}")) {
                table.addRowSelectionInterval(index, index)
            }
        }
    }

    private fun refreshSelectedProjects() {
        invalidate()
        repaint()
    }
}
