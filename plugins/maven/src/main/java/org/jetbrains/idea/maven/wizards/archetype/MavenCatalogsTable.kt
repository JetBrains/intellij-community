// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards.archetype

import com.intellij.execution.util.ListTableWithButtons
import com.intellij.execution.util.setEmptyState
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.search.SearchUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.*
import com.intellij.ui.components.JBViewport
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.maven.indices.arhetype.MavenCatalog
import org.jetbrains.idea.maven.wizards.MavenWizardBundle
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*
import javax.swing.table.TableCellRenderer

class MavenCatalogsTable(private val project: Project) : ListTableWithButtons<MavenCatalog>() {

  var catalogs: List<MavenCatalog>
    get() = elements
    set(catalogs) = setValues(catalogs)

  init {
    val nameColumn = tableView.columnModel.getColumn(0)
    val typeColumn = tableView.columnModel.getColumn(1)
    val locationColumn = tableView.columnModel.getColumn(2)

    val search = TableSpeedSearch(tableView)
    nameColumn.cellRenderer = CatalogNameRenderer(search) { it is MavenCatalog.System }
    typeColumn.cellRenderer = Renderer(search)
    locationColumn.cellRenderer = Renderer(search)

    tableView.visibleRowCount = 4
    tableView.putClientProperty(JBViewport.FORCE_VISIBLE_ROW_COUNT_KEY, true)
    tableView.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
    tableView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    tableView.rowSelectionAllowed = true
    tableView.columnSelectionAllowed = true
    tableView.setShowGrid(false)
    nameColumn.preferredWidth = JBUIScale.scale(140)
    typeColumn.preferredWidth = JBUIScale.scale(72)
    locationColumn.preferredWidth = JBUIScale.scale(356)

    setEmptyState(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.table.empty"))
  }

  override fun createListModel(): ListTableModel<MavenCatalog> {
    val nameColumnInfo = column(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.table.name")) { name }
    val locationColumnInfo = column(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.table.location")) { location }
    val typeColumnInfo = column(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.table.type")) {
      when (this) {
        is MavenCatalog.System -> MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.table.type.system")
        is MavenCatalog.Local -> MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.table.type.local")
        is MavenCatalog.Remote -> MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.table.type.remote")
      }
    }
    return ListTableModel(nameColumnInfo, typeColumnInfo, locationColumnInfo)
  }

  private fun column(name: @NlsContexts.ColumnName String, getter: MavenCatalog.() -> @Nls String) =
    object : ColumnInfo<MavenCatalog, @org.jetbrains.annotations.Nls String>(name) {
      override fun valueOf(item: MavenCatalog) = item.getter()
    }

  override fun createAddAction(): AnActionButtonRunnable {
    return AnActionButtonRunnable {
      val dialog = MavenAddCatalogDialog(project)
      if (dialog.showAndGet()) {
        val catalog = dialog.getCatalog()
        if (catalog != null) {
          addNewElement(catalog)
        }
      }
    }
  }

  override fun createEditAction(): AnActionButtonRunnable {
    return AnActionButtonRunnable {
      val selectedCatalog = selection.firstOrNull() ?: return@AnActionButtonRunnable
      val dialog = MavenEditCatalogDialog(project, selectedCatalog)
      if (dialog.showAndGet()) {
        val catalog = dialog.getCatalog() ?: return@AnActionButtonRunnable
        removeSelected()
        addNewElement(catalog)
      }
    }
  }

  override fun createElement() = throw UnsupportedOperationException()

  override fun isEmpty(element: MavenCatalog): Boolean = element.name.isEmpty()

  override fun cloneElement(variable: MavenCatalog) = variable

  override fun canDeleteElement(selection: MavenCatalog): Boolean = selection !is MavenCatalog.System

  override fun configureToolbarButtons(panel: JPanel) {
    super.configureToolbarButtons(panel)
    ToolbarDecorator.findEditButton(panel)?.addCustomUpdater {
      selection.let { it.isNotEmpty() && it.all(::canDeleteElement) }
    }
  }

  private inner class CatalogNameRenderer(
    search: TableSpeedSearch,
    private val isShowLock: (MavenCatalog) -> Boolean
  ) : TableCellRenderer {

    private val coloredRenderer = Renderer(search)

    override fun getTableCellRendererComponent(
      table: JTable?,
      value: Any?,
      isSelected: Boolean,
      hasFocus: Boolean,
      row: Int,
      column: Int
    ): Component {
      val coloredComponent = coloredRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
      return JPanel().apply {
        layout = BorderLayout()
        background = coloredComponent.background
        add(coloredComponent, BorderLayout.WEST)
        if (isShowLockIcon(row)) {
          add(JLabel(AllIcons.Nodes.Padlock), BorderLayout.EAST)
        }
      }
    }

    private fun isShowLockIcon(row: Int): Boolean {
      val catalog = elements.getOrNull(row)
      return catalog != null && isShowLock(catalog)
    }
  }

  private class Renderer(private val search: TableSpeedSearch) : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
      val text = value as? String ?: return
      SearchUtil.appendFragments(search.enteredPrefix, text, SimpleTextAttributes.STYLE_PLAIN, null, null, this)
    }
  }
}