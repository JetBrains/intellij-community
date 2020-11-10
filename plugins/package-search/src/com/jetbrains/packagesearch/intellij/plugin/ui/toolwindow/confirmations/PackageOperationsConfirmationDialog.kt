package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.confirmations

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InstallationInformation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageOperationTarget
import com.jetbrains.packagesearch.intellij.plugin.ui.updateAndRepaint
import org.jetbrains.annotations.Nls
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel
import javax.swing.table.JTableHeader

object PackageOperationsConfirmationDialog {
    private val columnClass =
        arrayOf(Boolean::class.java, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java)
    private val columnNames = arrayOf(
        "",
        PackageSearchBundle.message("packagesearch.ui.toolwindow.actions.upgradeAll.column.module"),
        PackageSearchBundle.message("packagesearch.ui.toolwindow.actions.upgradeAll.column.package"),
        "",
        PackageSearchBundle.message("packagesearch.ui.toolwindow.actions.upgradeAll.column.oldversion"),
        PackageSearchBundle.message("packagesearch.ui.toolwindow.actions.upgradeAll.column.newversion")
    )

    private val columnIsEditable = arrayOf(true, false, false, false, false, false)
    private val checkColumnWidth = JBUI.scale(30)

    private object Index {
        const val check = 0
        const val moduleName = 1
        const val packageIdentifier = 2
        const val scope = 3
        const val packageVersionOld = 4
        const val packageVersionNew = 5
    }

    fun show(
        project: Project,
        @Nls title: String,
        @Nls actionTitle: String,
        onlyStable: Boolean,
        repositoryIds: List<String>,
        requests: List<PackageOperationTarget>
    ): List<PackageOperationTarget> {
        val isChecked = Array(requests.size) { true }

        val tableModel = object : AbstractTableModel() {
            override fun getRowCount() = requests.size
            override fun getColumnCount() = columnClass.size
            override fun getColumnName(column: Int) = columnNames[column]
            override fun isCellEditable(rowIndex: Int, columnIndex: Int) = columnIsEditable[columnIndex]
            override fun getColumnClass(columnIndex: Int): Class<out Any> = columnClass[columnIndex]

            override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = when (columnIndex) {
                Index.check -> isChecked[rowIndex]
                Index.moduleName -> requests[rowIndex].projectModule.getFullName()
                Index.packageIdentifier -> requests[rowIndex].packageSearchDependency.identifier
                Index.scope -> requests[rowIndex].installedScope ?: InstallationInformation.DEFAULT_SCOPE
                Index.packageVersionOld -> requests[rowIndex].version
                Index.packageVersionNew -> requests[rowIndex].packageSearchDependency.getLatestAvailableVersion(onlyStable, repositoryIds) ?: ""
                else -> throw IndexOutOfBoundsException("columnIndex")
            }

            override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
                when (columnIndex) {
                    Index.check -> isChecked[rowIndex] = aValue as Boolean
                }
            }
        }

        val table: JBTable = object : JBTable(tableModel) {
            override fun getCellEditor(row: Int, column: Int) = when (column) {
                Index.check -> BooleanTableCellEditor()
                else -> super.getCellEditor(row, column)
            }

            override fun getCellRenderer(row: Int, column: Int) = when (column) {
                Index.check -> BooleanTableCellRenderer().apply { toolTipText = "" }
                Index.moduleName -> super.getCellRenderer(row, column).apply { toolTipText = requests[row].projectModule.getFullName() }
                Index.packageIdentifier -> super.getCellRenderer(row, column).apply { toolTipText = requests[row].packageSearchDependency.identifier }
                else -> super.getCellRenderer(row, column).apply { toolTipText = "" }
            }
        }.apply {
            val self = this
            createDefaultColumnsFromModel()
            columnModel.apply {
                getColumn(Index.check).apply {
                    resizable = false
                    minWidth = checkColumnWidth
                    maxWidth = checkColumnWidth
                    preferredWidth = checkColumnWidth
                }
            }
            tableHeader = JTableHeader(columnModel)
            selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent?) {
                    val selectedRows = self.selectedRows.filter { it >= 0 && it < requests.size }
                    fun handle(action: () -> Unit) {
                        action()
                        self.updateAndRepaint()
                        e?.consume()
                    }

                    if (e != null && e.modifiers == 0) {
                        when (e.keyCode) {
                            KeyEvent.VK_SPACE, KeyEvent.VK_INSERT, KeyEvent.VK_ENTER -> handle {
                                selectedRows.forEach { isChecked[it] = !isChecked[it] }
                            }
                            KeyEvent.VK_DELETE -> handle { selectedRows.forEach { isChecked[it] = false } }
                            KeyEvent.VK_HOME -> handle { self.setRowSelectionInterval(0, 0) }
                            KeyEvent.VK_END -> handle { self.setRowSelectionInterval(requests.size - 1, requests.size - 1) }
                        }
                    }
                }

                override fun keyTyped(e: KeyEvent) {
                    if (e.keyChar == ' ') {
                        e.keyChar = '\u0000'
                    }
                }
            })
            if (model.rowCount > 0) {
                setRowSelectionInterval(0, 0)
            }
            TableSpeedSearch(this).apply { comparator = SpeedSearchComparator(false) }
        }

        val scrollPane = JBScrollPane(table).apply { preferredSize = Dimension(JBUI.scale(800), preferredSize.height) }
        val panel = BorderLayoutPanel().apply { addToCenter(scrollPane) }

        val builder = DialogBuilder(project).apply {
            setTitle(title)
            setCenterPanel(panel)
            setPreferredFocusComponent(table)
            addOkAction()
            addCancelAction()
            okAction.setText(actionTitle)
        }

        return when (builder.show()) {
            DialogWrapper.OK_EXIT_CODE -> requests.filterIndexed { index, _ -> isChecked[index] }
            else -> emptyList()
        }
    }
}
