// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.packaging.toolwindow.PyPackageIcons
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.LoadingNode
import com.jetbrains.python.packaging.toolwindow.packages.tree.PyPackagesTree
import com.jetbrains.python.packaging.toolwindow.packages.tree.PyPackagesTreeListener
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

internal class PyPackagingTreeGroup(
  val repository: PyPackageRepository,
  val tree: PyPackagesTree,
  private val container: JPanel,
  private val showHeader: Boolean = true,
  private val useTreeNodeHeader: Boolean = false,
  private val headerIcon: javax.swing.Icon = PyPackageIcons.Repository,
  private val collapsible: Boolean = true,
) {

  private data class HeaderProperties(
    val label: JBLabel,
    val panel: JPanel,
  )

  @NlsSafe
  val repositoryName: String = repository.name

  private var itemsCount: Int? = null

  private val headerProperties: HeaderProperties = createHeaderProperties()

  internal var items: List<DisplayablePackage>
    get() = tree.items
    set(value) {
      tree.items = value
    }

  init {
    setupTreeListener()

    tree.emptyText.text = ""

    if (useTreeNodeHeader && showHeader && collapsible) {
      headerProperties.panel.addMouseListener(object : java.awt.event.MouseAdapter() {
        override fun mouseClicked(e: java.awt.event.MouseEvent) {
          if (tree.isVisible) collapse() else expand()
        }
      })
    }
  }

  private fun createHeaderProperties(): HeaderProperties {
    if (useTreeNodeHeader) {
      val repoIcon = JBLabel(headerIcon)
      val displayName = if (collapsible) repositoryName
                        else PyBundle.message("python.toolwindow.packages.custom.repo.invalid", repositoryName)
      val nameLabel = JBLabel(displayName).apply {
        if (!collapsible) foreground = JBColor.RED
      }
      
      val contentPanel = JPanel().apply {
        layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.X_AXIS)
        background = UIUtil.getListBackground()
        isOpaque = false
        
        val chevronIcon = if (collapsible) AllIcons.General.ArrowDown
                          else IconLoader.getDisabledIcon(AllIcons.General.ArrowRight)
        val chevron = JBLabel(chevronIcon)
        add(chevron)
        add(javax.swing.Box.createHorizontalStrut(2))
        add(repoIcon)
        add(javax.swing.Box.createHorizontalStrut(6))
        add(nameLabel)
      }
      
      val panel = JPanel(BorderLayout()).apply {
        background = UIUtil.getListBackground()
        val hPad = UIUtil.getListCellHPadding()
        val vPad = UIUtil.getListCellVPadding()
        border = JBUI.Borders.empty(vPad, hPad * 2, vPad, hPad + JBUI.scale(4))
        alignmentX = java.awt.Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Integer.MAX_VALUE, JBUI.CurrentTheme.List.rowHeight()) // Will be updated by updatePreferredSize()
        
        add(contentPanel, BorderLayout.WEST)
      }
      
      return HeaderProperties(nameLabel, panel)
    } else {
      val label = JBLabel(repositoryName).apply {
        border = JBUI.Borders.empty(5, 0)
        icon = AllIcons.General.ArrowDown
        horizontalAlignment = SwingConstants.LEFT
        verticalAlignment = SwingConstants.CENTER
      }
      val panel = PyPackagesUiComponents.headerPanel(label, tree)
      return HeaderProperties(label, panel)
    }
  }

  private fun setupTreeListener() {
    tree.setTreeListener(object : PyPackagesTreeListener {
      override fun onTreeStructureChanged() {
        SwingUtilities.invokeLater {
          updatePreferredSize()
          repaint()
        }
      }
    })
  }

  fun updatePreferredSize() {
    val width = container.width

    val insets = tree.insets
    val insetsHeight = insets.top + insets.bottom
    val rowsHeight = tree.rowCount * tree.rowHeight
    val totalHeight = if (rowsHeight == 0) 0 else rowsHeight + insetsHeight
    tree.preferredSize = Dimension(width, totalHeight)
    tree.minimumSize = Dimension(width, totalHeight)
    tree.maximumSize = Dimension(width, totalHeight)
    
    if (useTreeNodeHeader && showHeader) {
      headerProperties.panel.maximumSize = Dimension(width, headerProperties.panel.preferredSize.height)
    }
  }

  fun collapse() {
    tree.isVisible = false
    if (useTreeNodeHeader) {
      val panel = headerProperties.panel
      val contentPanel = (panel.layout as BorderLayout).getLayoutComponent(BorderLayout.WEST) as JPanel
      val chevron = contentPanel.getComponent(0) as JBLabel
      chevron.icon = AllIcons.General.ArrowRight
    } else {
      updateExpandStateIcon()
    }
    repaint()
  }

  fun collapseAll() {
    for (i in 0 until tree.rowCount) {
      tree.collapseRow(i)
    }
    collapse()
  }

  fun expand() {
    tree.isVisible = true
    if (useTreeNodeHeader) {
      val panel = headerProperties.panel
      val contentPanel = (panel.layout as BorderLayout).getLayoutComponent(BorderLayout.WEST) as JPanel
      val chevron = contentPanel.getComponent(0) as JBLabel
      chevron.icon = AllIcons.General.ArrowDown
    } else {
      updateExpandStateIcon()
    }
    repaint()
  }

  private fun updateExpandStateIcon() {
    headerProperties.label.icon = if (tree.isVisible)
      AllIcons.General.ArrowDown
    else
      AllIcons.General.ArrowRight
  }

  fun updateHeaderText(newItemCount: Int?) {
    itemsCount = newItemCount
    
    if (useTreeNodeHeader) {
      val panel = headerProperties.panel
      val contentPanel = (panel.layout as BorderLayout).getLayoutComponent(BorderLayout.WEST) as JPanel
      val nameLabel = contentPanel.getComponent(4) as JBLabel
      nameLabel.text = if (itemsCount == null)
        repositoryName
      else
        PyBundle.message("python.toolwindow.packages.custom.repo.searched", repositoryName, itemsCount)
    } else {
      headerProperties.label.text = if (itemsCount == null)
      repositoryName
    else
      PyBundle.message("python.toolwindow.packages.custom.repo.searched", repositoryName, itemsCount)
    }
  }

  fun setSdkToHeader(@Nls sdkName: String?) {
    itemsCount = null
    headerProperties.label.text = PyBundle.message("python.toolwindow.packages.sdk.label.html", repositoryName, sdkName)
  }

  fun setLoading(loading: Boolean) {
    if (loading) {
      tree.items = listOf(LoadingNode())
    }
    else if (tree.items.singleOrNull() is LoadingNode) {
      tree.items = emptyList()
    }
    repaint()
  }

  fun addTo(panel: JPanel) {
    if (showHeader) panel.add(headerProperties.panel)
    if (collapsible) panel.add(tree)
  }

  fun removeFrom(panel: JPanel) {
    if (showHeader) panel.remove(headerProperties.panel)
    if (collapsible) panel.remove(tree)
  }

  fun repaint() {
    container.revalidate()
    container.repaint()
  }
}
