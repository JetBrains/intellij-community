// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages.tree.renderers

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.getUserData
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.toolwindow.model.*
import com.jetbrains.python.packaging.toolwindow.packages.tree.PyPackagesTreeTable
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

internal class PackageNameCellRenderer : TableCellRenderer {
  private val nameLabel = JBLabel()
  private val namePanel = PackageRendererUtils.createBasicPanel().apply { add(nameLabel) }

  override fun getTableCellRendererComponent(
    table: JTable,
    value: Any?,
    isSelected: Boolean,
    hasFocus: Boolean,
    row: Int,
    column: Int,
  ): Component {
    val pkg = PackageRendererUtils.extractPackage(value) ?: return JLabel()
    val background = PackageRendererUtils.getBackgroundForState(isSelected)

    return when (pkg) {
      is ExpandResultNode -> createExpandNodeComponent(pkg, background)
      else -> createNameComponent(pkg, background)
    }
  }

  private fun createExpandNodeComponent(
    node: ExpandResultNode,
    bg: Color,
  ): Component {
    val expandNodeLabel = JBLabel(PyBundle.message("python.toolwindow.packages.load.more", node.more)).apply {
      foreground = UIUtil.getContextHelpForeground()
    }
    return PackageRendererUtils.createBasicPanel().apply {
      add(expandNodeLabel)
      background = bg
    }
  }

  private fun createNameComponent(
    pkg: DisplayablePackage,
    bg: Color,
  ): Component = namePanel.apply {
    nameLabel.text = pkg.name
    nameLabel.icon = if (pkg is InstalledPackage && pkg.isEditMode) AllIcons.Actions.Edit else null
    background = bg
  }
}

internal class PackageVersionCellRenderer : TableCellRenderer {
  private val versionLabel = JLabel().apply {
    border = JBEmptyBorder(JBUI.CurrentTheme.ActionsList.cellPadding())
  }

  private val linkLabel = JLabel(PyBundle.message("action.python.packages.install.text")).apply {
    foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
    border = JBEmptyBorder(JBUI.CurrentTheme.ActionsList.cellPadding())
  }

  private val versionPanel = PyPackagesUiComponents.borderPanel {
    add(versionLabel, BorderLayout.CENTER)
  }

  override fun getTableCellRendererComponent(
    table: JTable,
    value: Any?,
    isSelected: Boolean,
    hasFocus: Boolean,
    row: Int,
    column: Int,
  ): Component {
    val pkg = PackageRendererUtils.extractPackage(value) ?: return JLabel()
    val background = PackageRendererUtils.getBackgroundForState(isSelected)
    val treeTable = table.getUserData(PyPackagesTreeTable.TREE_TABLE_KEY)!!

    versionPanel.removeAll()
    versionPanel.background = background

    when (pkg) {
      is InstallablePackage -> installablePackageVersionStrategy(versionPanel, treeTable, row, linkLabel)
      is InstalledPackage -> if (!treeTable.isReadOnly && pkg.nextVersion != null && pkg.canBeUpdated) {
        updatableInstalledPackageStrategy(versionPanel, pkg, pkg.nextVersion, treeTable, row, linkLabel)
      }
      else {
        defaultPackageStrategy(versionPanel, pkg, versionLabel)
      }
      is RequirementPackage -> requirementPackageStrategy(versionPanel, pkg, versionLabel)
      is ExpandResultNode -> JLabel()
    }

    return versionPanel
  }
}