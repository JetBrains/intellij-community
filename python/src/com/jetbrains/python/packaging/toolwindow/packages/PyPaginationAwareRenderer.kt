// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBLabel
import com.intellij.ui.hover.TableHoverListener
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.ExpandResultNode
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.packages.table.PyPackagesTable
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents
import java.awt.Component
import java.awt.font.TextAttribute
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

internal class PyPaginationAwareRenderer : DefaultTableCellRenderer() {
  private val nameLabel = JBLabel().apply { border = JBUI.Borders.empty(0, 12) }

  private val versionLabel = JLabel().apply { border = JBUI.Borders.emptyRight(12) }

  private val linkLabel = JLabel(PyBundle.message("action.python.packages.install.text")).apply {
    border = JBUI.Borders.emptyRight(12)
    foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
  }

  private val namePanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    border = JBUI.Borders.empty()
    add(nameLabel)
  }

  private val versionPanel = PyPackagesUiComponents.boxPanel {
    border = JBUI.Borders.emptyRight(12)
    add(versionLabel)
  }

  override fun getTableCellRendererComponent(
    table: JTable,
    value: Any?,
    isSelected: Boolean,
    hasFocus: Boolean,
    row: Int,
    column: Int,
  ): Component {
    val rowSelected = row in table.selectedRows
    val tableFocused = table.hasFocus()

    if (value is ExpandResultNode) {
      if (column == 1) {
        versionPanel.removeAll()
        return versionPanel
      }
      else {
        nameLabel.text = PyBundle.message("python.toolwindow.packages.load.more", value.more)
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
        linkLabel.text = PyBundle.message("action.python.packages.install.text")
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
      versionLabel.icon = (value as? InstalledPackage)?.sourceRepoIcon
      return versionPanel
    }

    // package name column
    val currentPackage = value as DisplayablePackage

    namePanel.background = JBUI.CurrentTheme.Table.background(rowSelected, tableFocused)
    namePanel.foreground = JBUI.CurrentTheme.Table.foreground(rowSelected, tableFocused)

    nameLabel.text = currentPackage.name
    val isEditMode = (currentPackage as? InstalledPackage)?.isEditMode == true
    nameLabel.icon = if (isEditMode) AllIcons.Actions.Edit else null
    nameLabel.foreground = JBUI.CurrentTheme.Label.foreground()

    return namePanel
  }

  @Suppress("UNCHECKED_CAST")
  private fun JLabel.updateUnderline(table: JTable, currentRow: Int) {
    val hoveredRow = TableHoverListener.getHoveredRow(table)
    val hoveredColumn = (table as PyPackagesTable).hoveredColumn
    val underline = if (hoveredRow == currentRow && hoveredColumn == 1) TextAttribute.UNDERLINE_ON else -1

    val attributes = font.attributes as MutableMap<TextAttribute, Any>
    attributes[TextAttribute.UNDERLINE] = underline
    attributes[TextAttribute.LIGATURES] = TextAttribute.LIGATURES_ON
    font = font.deriveFont(attributes)
  }
}