// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages.tree.renderers

import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.hover.TableHoverListener
import com.intellij.ui.hover.TreeHoverListener
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.model.RequirementPackage
import com.jetbrains.python.packaging.toolwindow.packages.tree.PyPackagesTreeTable
import java.awt.BorderLayout
import java.awt.font.TextAttribute
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable

internal fun installablePackageVersionStrategy(
  versionPanel: JPanel,
  tableTree: PyPackagesTreeTable,
  row: Int,
  linkLabel: JLabel,
) {
  val hoveredRow = TreeHoverListener.getHoveredRow(tableTree.tree)
  val isRowHovered = hoveredRow == row
  val isSelected = tableTree.tree.isRowSelected(row)


  if (!tableTree.isReadOnly && (isRowHovered || isSelected)) {
    linkLabel.text = PyBundle.message("action.python.packages.install.text")
    linkLabel.updateUnderline(tableTree, tableTree.table, row)
    versionPanel.add(linkLabel, BorderLayout.WEST)
  }
}

internal fun updatableInstalledPackageStrategy(
  versionPanel: JPanel,
  pkg: InstalledPackage,
  version: PyPackageVersion,
  tableTree: PyPackagesTreeTable,
  row: Int,
  linkLabel: JLabel,
) {
  @NlsSafe val updateLink = "${pkg.instance.version} -> ${version.presentableText}"
  linkLabel.text = updateLink
  linkLabel.updateUnderline(tableTree, tableTree.table, row)
  versionPanel.add(linkLabel, BorderLayout.WEST)
}

internal fun requirementPackageStrategy(
  versionPanel: JPanel,
  pkg: RequirementPackage,
  versionLabel: JLabel,
) {
  @NlsSafe val version = pkg.instance.version
  versionLabel.text = version
  versionLabel.icon = pkg.sourceRepoIcon
  versionPanel.add(versionLabel, BorderLayout.WEST)
}

internal fun defaultPackageStrategy(
  versionPanel: JPanel,
  pkg: InstalledPackage,
  versionLabel: JLabel,
) {
  @NlsSafe val version = pkg.instance.version
  versionLabel.text = version
  versionLabel.icon = pkg.sourceRepoIcon
  versionPanel.add(versionLabel, BorderLayout.WEST)
}

private fun JLabel.updateUnderline(tableTree: PyPackagesTreeTable, table: JTable, currentRow: Int) {
  val hoveredRow = TableHoverListener.getHoveredRow(table)
  val hoveredColumn = tableTree.hoveredColumn
  val underline = if (hoveredRow == currentRow && hoveredColumn == 0) TextAttribute.UNDERLINE_ON else -1

  @Suppress("UNCHECKED_CAST")
  val attributes = font.attributes as MutableMap<TextAttribute, Any>
  attributes[TextAttribute.UNDERLINE] = underline
  attributes[TextAttribute.LIGATURES] = TextAttribute.LIGATURES_ON
  font = font.deriveFont(attributes)
}