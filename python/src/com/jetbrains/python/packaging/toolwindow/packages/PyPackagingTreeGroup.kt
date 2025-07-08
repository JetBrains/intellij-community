// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.packages.tree.PyPackagesTreeListener
import com.jetbrains.python.packaging.toolwindow.packages.tree.PyPackagesTreeTable
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

internal class PyPackagingTreeGroup(
  repository: PyPackageRepository,
  val tree: PyPackagesTreeTable,
  private val container: JPanel,
) {

  private data class HeaderProperties(
    val label: JBLabel,
    val panel: JPanel,
  )

  @NlsSafe
  val repositoryName: String = repository.name

  private var itemsCount: Int? = null

  val scrollPane = JBScrollPane(tree).apply {
    border = JBUI.Borders.empty()
    verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
  }

  private val headerProperties: HeaderProperties = createHeaderProperties()

  internal var items: List<DisplayablePackage>
    get() = tree.items
    set(value) {
      tree.items = value
    }

  init {
    setupTreeListener()
  }

  private fun createHeaderProperties(): HeaderProperties {
    val label = JBLabel(repositoryName).apply {
      icon = AllIcons.General.ArrowDown
      horizontalAlignment = SwingConstants.LEFT
      verticalAlignment = SwingConstants.CENTER
    }
    val panel = PyPackagesUiComponents.headerPanel(label, scrollPane)
    return HeaderProperties(label, panel)
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
    val totalHeight = tree.tree.rowCount * tree.tree.rowHeight
    val width = container.width

    scrollPane.preferredSize = Dimension(width, totalHeight)
    scrollPane.minimumSize = Dimension(width, totalHeight)
    scrollPane.maximumSize = Dimension(width, totalHeight)
  }

  fun collapse() {
    scrollPane.isVisible = false
    updateExpandStateIcon()
    repaint()
  }

  fun collapseAll() {
    for (i in 0 until tree.tree.rowCount) {
      tree.tree.collapseRow(i)
    }
    collapse()
  }


  fun expand() {
    scrollPane.isVisible = true
    updateExpandStateIcon()
    repaint()
  }

  private fun updateExpandStateIcon() {
    headerProperties.label.icon = if (scrollPane.isVisible)
      AllIcons.General.ArrowDown
    else
      AllIcons.General.ArrowRight
  }

  fun updateHeaderText(newItemCount: Int?) {
    itemsCount = newItemCount
    headerProperties.label.text = if (itemsCount == null)
      repositoryName
    else
      PyBundle.message("python.toolwindow.packages.custom.repo.searched", repositoryName, itemsCount)
  }

  fun setSdkToHeader(@Nls sdkName: String?) {
    itemsCount = null
    headerProperties.label.text =  PyBundle.message("python.toolwindow.packages.sdk.label.html", repositoryName, sdkName)
  }

  fun addTo(panel: JPanel) {
    scrollPane.alignmentX = Component.LEFT_ALIGNMENT
    panel.add(headerProperties.panel)
    panel.add(scrollPane)
  }

  fun removeFrom(panel: JPanel) {
    panel.remove(headerProperties.panel)
    panel.remove(scrollPane)
  }

  fun repaint() {
    container.revalidate()
    container.repaint()
  }
}
