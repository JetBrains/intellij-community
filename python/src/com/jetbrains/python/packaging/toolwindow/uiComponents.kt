// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.SideBorder
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.hover.TableHoverListener
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.*
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.repository.PyPackageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer


internal class PyPackagesTable<T : DisplayablePackage>(project: Project,
                                                       model: ListTableModel<T>,
                                                       tablesView: PyPackagingTablesView,
                                                       controller: PyPackagingToolWindowPanel) : JBTable(model) {
  private var lastSelectedRow = -1
  internal var hoveredColumn = -1
  init {
    val service = project.service<PyPackagingToolWindowService>()
    setShowGrid(false)
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    val column = columnModel.getColumn(1)
    column.minWidth = 130
    column.maxWidth = 130
    column.resizable = false
    border = SideBorder(NamedColorUtil.getBoundsColor(), SideBorder.BOTTOM)
    rowHeight = 20

    initCrossNavigation(service, tablesView)

    val hoverListener = object : TableHoverListener() {
      override fun onHover(table: JTable, row: Int, column: Int) {
        hoveredColumn = column
        if (column == 1) {
          table.repaint(table.getCellRect(row, column, true))
          val currentPackage = items[row]
          if (currentPackage is InstallablePackage
              || (currentPackage is InstalledPackage && currentPackage.canBeUpdated)) {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            return
          }
        }
        cursor = Cursor.getDefaultCursor()
      }
    }
    hoverListener.addTo(this)

    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.clickCount != 1 || columnAtPoint(e.point) != 1) return // double click or click on package name column, nothing to be done
        val hoveredRow = TableHoverListener.getHoveredRow(this@PyPackagesTable)
        val selectedPackage = this@PyPackagesTable.items[hoveredRow]

        if (selectedPackage is InstallablePackage) {
          controller.packagingScope.launch(Dispatchers.IO) {
            val details = service.detailsForPackage(selectedPackage)
            withContext(Dispatchers.Main) {
              createAvailableVersionsPopup(selectedPackage, details, project, controller).show(RelativePoint(e))
            }
          }
        }
        else if (selectedPackage is InstalledPackage && selectedPackage.canBeUpdated) {
          controller.packagingScope.launch(Dispatchers.IO) {
            val specification = selectedPackage.repository.createPackageSpecification(selectedPackage.name,
                                                                                      selectedPackage.nextVersion!!.presentableText)
            project.service<PyPackagingToolWindowService>().updatePackage(specification)
          }
        }
      }
    })

    selectionModel.addListSelectionListener {
      if (selectedRow != -1 && selectedRow != lastSelectedRow) {
        lastSelectedRow = selectedRow
        tablesView.requestSelection(this)
        val pkg = model.items[selectedRow]
        if (pkg !is ExpandResultNode) controller.packageSelected(pkg)
      }
    }

    object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        val pkg = model.items[selectedRow]
        if (pkg is ExpandResultNode) loadMoreItems(service, pkg)
        return true
      }
    }.installOn(this)

    PopupHandler.installPopupMenu(this, DefaultActionGroup(object : DumbAwareAction({
                                                                                      val pkg = if (selectedRow >= 0) model.items[selectedRow] else null
                                                                                      if (pkg is InstalledPackage) {
                                                                                        message("python.toolwindow.packages.delete.package")
                                                                                      }
                                                                                      else {
                                                                                        message("python.toolwindow.packages.install.link")
                                                                                      }
                                                                                    }) {
      override fun actionPerformed(e: AnActionEvent) {
        controller.packagingScope.launch(Dispatchers.Main) {
          if (selectedRow == -1) return@launch
          val pkg = model.items[selectedRow]
          if (pkg is InstalledPackage) {
            withContext(Dispatchers.IO) {
              service.deletePackage(pkg)
            }
          }
          else if (pkg is InstallablePackage) {
            controller.packagingScope.launch(Dispatchers.IO) {
              val details = service.detailsForPackage(pkg)
              withContext(Dispatchers.Main) {
                createAvailableVersionsPopup(pkg as InstallablePackage, details, project, controller).show(RelativePoint(e.inputEvent as MouseEvent))
              }
            }
          }
        }
      }
    }, object : DumbAwareAction({
                                  val pkg = if (selectedRow >= 0) model.items[selectedRow] else null
                                  if (pkg is InstalledPackage && pkg.canBeUpdated) {
                                    message("python.toolwindow.packages.update.package")
                                  }
                                  else {
                                    ""
                                  }
                                }) {
      override fun actionPerformed(e: AnActionEvent) {
        controller.packagingScope.launch(Dispatchers.Main) {
          if (selectedRow == -1) return@launch
          val pkg = model.items[selectedRow]
          if (pkg is InstalledPackage && pkg.canBeUpdated) {
            controller.packagingScope.launch(Dispatchers.IO) {
              val specification = pkg.repository.createPackageSpecification(pkg.name, pkg.nextVersion!!.presentableText)
              service.updatePackage(specification)
            }
          }
          else if (pkg is InstallablePackage) {
            controller.packagingScope.launch(Dispatchers.IO) {
              val details = service.detailsForPackage(pkg)
              withContext(Dispatchers.Main) {
                createAvailableVersionsPopup(pkg as InstallablePackage, details, project, controller).show(RelativePoint(e.inputEvent as MouseEvent))
              }
            }
          }
        }
      }
    }), "PackagePopup")
  }

  private fun createAvailableVersionsPopup(selectedPackage: InstallablePackage, details: PythonPackageDetails, project: Project, controller: PyPackagingToolWindowPanel): ListPopup {
    return JBPopupFactory.getInstance().createListPopup(object : BaseListPopupStep<String>(null, details.availableVersions) {
      override fun onChosen(selectedValue: String?, finalChoice: Boolean): PopupStep<*>? {
        return doFinalStep {
          val specification = selectedPackage.repository.createPackageSpecification(selectedPackage.name, selectedValue)
          controller.packagingScope.launch(Dispatchers.IO) {
            project.service<PyPackagingToolWindowService>().installPackage(specification)
          }
        }
      }
    }, 8)
  }

  private fun initCrossNavigation(service: PyPackagingToolWindowService, tablesView: PyPackagingTablesView) {
    getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), ENTER_ACTION)
    actionMap.put(ENTER_ACTION, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        if (selectedRow == -1) return
        val index = selectedRow
        val selectedItem = items[selectedRow]

        if (selectedItem is ExpandResultNode) {
          loadMoreItems(service, selectedItem)
        }
        setRowSelectionInterval(index, index)
      }
    })

    val nextRowAction = actionMap[NEXT_ROW_ACTION]
    actionMap.put(NEXT_ROW_ACTION, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        if (selectedRow == -1) return

        if (selectedRow + 1 == items.size) {
          tablesView.selectNextFrom(this@PyPackagesTable)
        }
        else {
          nextRowAction.actionPerformed(e)
        }
      }
    })

    val prevRowAction = actionMap[PREVIOUS_ROW_ACTION]
    actionMap.put(PREVIOUS_ROW_ACTION, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        if (selectedRow == -1) return

        if (selectedRow == 0) {
          tablesView.selectPreviousOf(this@PyPackagesTable)
        }
        else {
          prevRowAction.actionPerformed(e)
        }
      }
    })
  }

  @Suppress("UNCHECKED_CAST")
  private fun loadMoreItems(service: PyPackagingToolWindowService, node: ExpandResultNode) {
    val result = service.getMoreResultsForRepo(node.repository, items.size - 1)
    items = items.dropLast(1) + (result.packages as List<T>)
    if (result.moreItems > 0) {
      node.more = result.moreItems
      items = items + listOf(node) as List<T>
    }
    this@PyPackagesTable.revalidate()
    this@PyPackagesTable.repaint()
  }

  override fun getCellRenderer(row: Int, column: Int): TableCellRenderer {
    return PyPaginationAwareRenderer()
  }

  override fun clearSelection() {
    lastSelectedRow = -1
    super.clearSelection()
  }

  internal fun addRows(rows: List<T>) = listModel.addRows(rows)
  internal fun removeRow(index: Int) = listModel.removeRow(index)
  internal fun insertRow(index: Int, pkg: T) = listModel.insertRow(index, pkg)

  @Suppress("UNCHECKED_CAST")
  private val listModel: ListTableModel<T>
    get() = model as ListTableModel<T>

  var items: List<T>
    get() = listModel.items
    set(value) { listModel.items = value.toMutableList() }

  companion object {
    private const val NEXT_ROW_ACTION = "selectNextRow"
    private const val PREVIOUS_ROW_ACTION = "selectPreviousRow"
    private const val ENTER_ACTION = "ENTER"
  }
}

internal class PyPackagesTableModel<T : DisplayablePackage> : ListTableModel<T>() {
  override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
  override fun getColumnCount(): Int = 2
  override fun getColumnName(column: Int): String = column.toString()
  override fun getColumnClass(columnIndex: Int): Class<*> = DisplayablePackage::class.java

  override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = items[rowIndex]
}

fun boxPanel(init: JPanel.() -> Unit) = object : JPanel() {
  init {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    alignmentX = LEFT_ALIGNMENT
    init()
  }
}

fun borderPanel(init: JPanel.() -> Unit) = object : JPanel() {
  init {
    layout = BorderLayout(0, 0)
    init()
  }
}

fun headerPanel(label: JLabel, component: JComponent?) = object : JPanel() {
  init {
    background = UIUtil.getLabelBackground()
    layout = BorderLayout()
    border = BorderFactory.createCompoundBorder(SideBorder(NamedColorUtil.getBoundsColor(), SideBorder.BOTTOM), JBUI.Borders.empty(0, 5))
    preferredSize = Dimension(preferredSize.width, 25)
    minimumSize = Dimension(minimumSize.width, 25)
    maximumSize = Dimension(maximumSize.width, 25)

    add(label, BorderLayout.WEST)
    if (component != null) {
      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent?) {
          component.isVisible = !component.isVisible
          label.icon = if (component.isVisible) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
        }
      })
    }
  }
}

private class PyPaginationAwareRenderer : DefaultTableCellRenderer() {
  private val nameLabel = JLabel().apply { border = JBUI.Borders.empty(0, 12) }
  private val versionLabel = JLabel().apply { border = JBUI.Borders.emptyRight(12) }
  private val linkLabel = JLabel(message("python.toolwindow.packages.install.link")).apply {
    border = JBUI.Borders.emptyRight(12)
    foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
  }

  private val namePanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    border = JBUI.Borders.empty()
    add(nameLabel)
  }

  private val versionPanel = boxPanel {
    border = JBUI.Borders.emptyRight(12)
    add(versionLabel)
  }

  override fun getTableCellRendererComponent(table: JTable,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    val rowSelected = table.selectedRow == row
    val tableFocused = table.hasFocus()

    if (value is ExpandResultNode) {
      if (column == 1) {
        versionPanel.removeAll()
        return versionPanel
      }
      else {
        nameLabel.text = message("python.toolwindow.packages.load.more", value.more)
        nameLabel.foreground = UIUtil.getContextHelpForeground()
        return namePanel
      }
    }

    // version column
    if (column == 1) {
      versionPanel.background = JBUI.CurrentTheme.Table.background(rowSelected, tableFocused)
      versionPanel.foreground = JBUI.CurrentTheme.Table.foreground(rowSelected, tableFocused)
      versionPanel.removeAll()

      if (value is InstallablePackage) {
        linkLabel.text = message("python.toolwindow.packages.install.link")
        linkLabel.updateUnderline(table, row)
        if (rowSelected || TableHoverListener.getHoveredRow(table) == row) {
          versionPanel.add(linkLabel)
        }
      }
      else if (value is InstalledPackage && value.nextVersion != null && value.canBeUpdated) {
        @NlsSafe val updateLink = value.instance.version + " -> " + value.nextVersion.presentableText
        linkLabel.text = updateLink
        linkLabel.updateUnderline(table, row)
        versionPanel.add(linkLabel)
      }
      else {
        @NlsSafe val version = (value as InstalledPackage).instance.version
        versionLabel.text = version
        versionPanel.add(versionLabel)
      }
      return versionPanel
    }

    // package name column
    val currentPackage = value as DisplayablePackage

    namePanel.background = JBUI.CurrentTheme.Table.background(rowSelected, tableFocused)
    namePanel.foreground = JBUI.CurrentTheme.Table.foreground(rowSelected, tableFocused)

    nameLabel.text = currentPackage.name
    nameLabel.foreground = JBUI.CurrentTheme.Label.foreground()
    return namePanel
  }

  @Suppress("UNCHECKED_CAST")
  private fun JLabel.updateUnderline(table: JTable, currentRow: Int) {
    val hoveredRow = TableHoverListener.getHoveredRow(table)
    val hoveredColumn = (table as PyPackagesTable<*>).hoveredColumn
    val underline = if (hoveredRow == currentRow && hoveredColumn == 1) TextAttribute.UNDERLINE_ON else -1

    val attributes = font.attributes as MutableMap<TextAttribute, Any>
    attributes[TextAttribute.UNDERLINE] = underline
    attributes[TextAttribute.LIGATURES] = TextAttribute.LIGATURES_ON
    font = font.deriveFont(attributes)
  }
}


internal class PyPackagingTableGroup<T: DisplayablePackage>(val repository: PyPackageRepository, val table: PyPackagesTable<T>) {
  @NlsSafe val name: String = repository.name!!

  private var expanded = false
  private val label = JLabel(name).apply { icon = AllIcons.General.ArrowDown }
  private val header: JPanel = headerPanel(label, table)
  private var itemsCount: Int? = null


  internal var items: List<T>
    get() = table.items
    set(value) {
      table.items = value
    }

  fun collapse() {
    expanded = false
    table.isVisible = false
    label.icon = if (table.isVisible) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
  }

  fun expand() {
    expanded = true
    table.isVisible = true
    label.icon = if (table.isVisible) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
  }

  fun updateHeaderText(newItemCount: Int?) {
    itemsCount = newItemCount
    label.text = if (itemsCount == null) name else message("python.toolwindow.packages.custom.repo.searched", name, itemsCount)
  }

  fun addTo(panel: JPanel) {
    panel.add(header)
    panel.add(table)
  }

  fun replace(row: Int, pkg: T) {
    table.removeRow(row)
    table.insertRow(row, pkg)
  }

  fun removeFrom(panel: JPanel) {
    panel.remove(header)
    panel.remove(table)
  }

  fun repaint() {
    table.invalidate()
    table.repaint()
  }
}