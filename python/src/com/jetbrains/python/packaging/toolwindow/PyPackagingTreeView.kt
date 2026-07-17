// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.repository.InstalledPyPackagedRepository
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.PyInvalidRepositoryViewData
import com.jetbrains.python.packaging.toolwindow.model.PyPackagesViewData
import com.jetbrains.python.packaging.toolwindow.packages.PyPackagingTreeGroup
import com.jetbrains.python.packaging.toolwindow.packages.tree.PyPackagesTree
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Rectangle
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyBoundsAdapter
import java.awt.event.HierarchyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants

internal class PyPackagingTreeView(
  private val project: Project,
  private val container: JPanel,
  private val controller: PyPackagingToolWindowPanel,
) {
  private val repositories: MutableList<PyPackagingTreeGroup> = mutableListOf()
  private val installedPackages =
    PyPackagingTreeGroup(
      InstalledPyPackagedRepository(),
      PyPackagesTree(project, controller),
      container,
      showHeader = false,
    )

  private val invalidRepositories: MutableMap<String, PyPackagingTreeGroup> = mutableMapOf()

  private val uninstalledContainerPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    alignmentX = Component.LEFT_ALIGNMENT
    background = UIUtil.getListBackground()
    maximumSize = Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)
  }
  
  private val uninstalledChevron = JBLabel(AllIcons.General.ArrowDown)

  private val uninstalledLeftPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    background = UIUtil.getListBackground()
    isOpaque = false

    add(uninstalledChevron)
    add(Box.createHorizontalStrut(4))
    add(JBLabel(message("python.toolwindow.packages.uninstalled.label")))
  }

  private val uninstalledSeparatorWrapper = JPanel(BorderLayout()).apply {
    isOpaque = false
    add(JSeparator(SwingConstants.HORIZONTAL).apply {
      background = JBColor.border()
      preferredSize = Dimension(Integer.MAX_VALUE, 1)
      maximumSize = Dimension(Integer.MAX_VALUE, 1)
      minimumSize = Dimension(0, 1)
    }, BorderLayout.CENTER)
    addHierarchyBoundsListener(object : HierarchyBoundsAdapter() {
      override fun ancestorResized(e: HierarchyEvent) {
        val textHeight = uninstalledLeftPanel.preferredSize.height
        border = JBUI.Borders.emptyTop((textHeight - 1) / 2)
      }
    })
    border = JBUI.Borders.emptyTop((uninstalledLeftPanel.preferredSize.height - 1) / 2)
  }

  private val uninstalledHeaderPanel = JPanel(BorderLayout()).apply {
    background = UIUtil.getListBackground()
    border = JBUI.Borders.empty(com.intellij.ui.TitledSeparator.TOP_INSET, UIUtil.getListCellHPadding())
    alignmentX = Component.LEFT_ALIGNMENT

    add(uninstalledLeftPanel, BorderLayout.WEST)
    add(uninstalledSeparatorWrapper, BorderLayout.CENTER)

    addHierarchyBoundsListener(object : HierarchyBoundsAdapter() {
      override fun ancestorResized(e: HierarchyEvent) {
        maximumSize = Dimension(Integer.MAX_VALUE, preferredSize.height)
      }
    })
  }
  
  private var uninstalledExpanded = true

  private var isSyncingTreeSelection = false

  init {
    installedPackages.addTo(container)
    installedPackages.expand()

    container.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        synchronizeScrollPaneSize()
      }
    })
    container.addHierarchyBoundsListener(object : HierarchyBoundsAdapter() {
      override fun ancestorResized(e: HierarchyEvent) {
        synchronizeScrollPaneSize()
      }
    })

    getRepos().forEach { treeGroup ->
      treeGroup.tree.addTreeSelectionListener {
        syncTreeSelection(treeGroup.tree)
      }
    }
    
    setupUninstalledHeaderListener()
  }

  private fun setupUninstalledHeaderListener() {
    val toggleListener = object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        toggleUninstalledSection()
      }
    }
    uninstalledHeaderPanel.addMouseListener(toggleListener)
    uninstalledLeftPanel.addMouseListener(toggleListener)
    uninstalledSeparatorWrapper.addMouseListener(toggleListener)
  }

  private fun toggleUninstalledSection() {
    uninstalledExpanded = !uninstalledExpanded
    uninstalledContainerPanel.isVisible = uninstalledExpanded
    uninstalledChevron.icon = if (uninstalledExpanded) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
    synchronizeScrollPaneSize()
  }

  fun showSearchResult(installed: List<DisplayablePackage>, repoData: List<PyPackagesViewData>) {
    updatePackages(installed, repoData)

    installedPackages.expand()
    installedPackages.tree.expandAll()
    installedPackages.updateHeaderText(installed.size)

    if (repositories.isNotEmpty()) {
      showUninstalledSection()
      javax.swing.SwingUtilities.invokeLater {
        container.scrollRectToVisible(Rectangle(0, 0, 1, 1))
      }
    }

    val tableToData = repositories.map { repo -> repo to repoData.find { it.repository === repo.repository }!! }
    tableToData.forEach { (table, data) ->
      table.updateHeaderText(data.result.total)
      table.expand()
    }

    synchronizeScrollPaneSize()

    val exactMatchPackageName = tableToData
      .firstOrNull { (_, data) -> data.exactMatch != -1 }
      ?.second?.let { data -> data.displayable.getOrNull(data.exactMatch)?.name }

    exactMatchPackageName?.let { packageName ->
      val installedPackageIndex = installedPackages.items.indexOfFirst { it.name == packageName }
      if (installedPackageIndex != -1) {
        installedPackages.tree.setSelectionRow(installedPackageIndex)
      }
    }
  }

  private fun showUninstalledSection() {
    if (!container.isAncestorOf(uninstalledHeaderPanel)) {
      container.add(uninstalledHeaderPanel)
      container.add(uninstalledContainerPanel)
    }
    uninstalledExpanded = true
    uninstalledContainerPanel.isVisible = true
  }

  private fun hideUninstalledSection() {
    container.remove(uninstalledHeaderPanel)
    container.remove(uninstalledContainerPanel)
    uninstalledExpanded = false
  }

  fun setSdkName(@Nls sdkName: String?) {
    installedPackages.setSdkToHeader(sdkName)
  }

  fun resetSearch(installed: List<DisplayablePackage>, currentSdk: Sdk?) {
    updatePackages(installed, emptyList())

    installedPackages.expand()
    installedPackages.setSdkToHeader(currentSdk?.name)

    repositories.forEach { it.removeFrom(uninstalledContainerPanel) }
    repositories.clear()

    hideUninstalledSection()

    container.scrollRectToVisible(Rectangle(0, 0))
  }

  private fun updatePackages(installed: List<DisplayablePackage>, repoData: List<PyPackagesViewData>) {
    updateExistingRepository(installedPackages, installed, moreItems = 0, sortedAll = installed)
    val (validRepoData, invalidData) = repoData.partition { it !is PyInvalidRepositoryViewData }

    updateValidRepositories(validRepoData)

    cleanupRemovedRepositories(validRepoData.map { it.repository })

    val invalidRepoData = invalidData.filterIsInstance<PyInvalidRepositoryViewData>()
    refreshInvalidRepositories(invalidRepoData)
  }

  private fun synchronizeScrollPaneSize() {
    getRepos().forEach {
      it.updatePreferredSize()
      it.tree.revalidate()
      it.tree.repaint()
    }
    container.revalidate()
    container.repaint()
  }

  private fun updateValidRepositories(validRepoData: List<PyPackagesViewData>) {
    for (data in validRepoData) {
      val existingRepo = findTableForRepo(data.repository)
      val totalForRepo = if (data.sortedAll.size > data.displayable.size) data.sortedAll.size else data.result.total
      val moreItems = (totalForRepo - data.displayable.size).coerceAtLeast(0)

      if (existingRepo != null) {
        updateExistingRepository(existingRepo, data.displayable, moreItems, data.sortedAll)
      }
      else {
        createNewRepository(data.repository, data.displayable, moreItems, data.sortedAll)
      }
    }
    installScrollLoaderIfNeeded()
  }

  private fun updateExistingRepository(repo: PyPackagingTreeGroup, items: List<DisplayablePackage>, moreItems: Int, sortedAll: List<DisplayablePackage>) {
    repo.items = items
    if (sortedAll.size > items.size) repo.tree.primeSortedMatches(sortedAll)
    repo.tree.pendingMore = moreItems
    synchronizeScrollPaneSize()
  }

  private fun createNewRepository(repository: PyPackageRepository, items: List<DisplayablePackage>, moreItems: Int, sortedAll: List<DisplayablePackage>) {
    val newTree = PyPackagesTree(project, controller)
    newTree.items = items
    if (sortedAll.size > items.size) newTree.primeSortedMatches(sortedAll)
    newTree.pendingMore = moreItems
    val newTreeGroup = PyPackagingTreeGroup(repository, newTree, container, showHeader = true, useTreeNodeHeader = true)
    newTreeGroup.items = items
    repositories.add(newTreeGroup)
    newTreeGroup.addTo(uninstalledContainerPanel)
    newTree.addTreeSelectionListener {
      syncTreeSelection(newTree)
    }
    synchronizeScrollPaneSize()
  }

  private var scrollLoaderInstalled: Boolean = false
  private var isLoadingMore: Boolean = false

  private fun installScrollLoaderIfNeeded() {
    if (scrollLoaderInstalled) return
    val scrollPane = javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane::class.java, container) as? javax.swing.JScrollPane
                     ?: return
    scrollLoaderInstalled = true
    val bar = scrollPane.verticalScrollBar
    bar.addAdjustmentListener {
      if (isLoadingMore) return@addAdjustmentListener
      val threshold = maxOf(64, bar.visibleAmount / 2)
      if (bar.value + bar.visibleAmount < bar.maximum - threshold) return@addAdjustmentListener
      if (repositories.none { it.tree.pendingMore > 0 }) return@addAdjustmentListener
      isLoadingMore = true
      try { loadMoreInPendingRepos() } finally { isLoadingMore = false }
    }
  }

  private fun loadMoreInPendingRepos() {
    repositories.filter { it.tree.pendingMore > 0 }.forEach { it.tree.loadMore() }
    synchronizeScrollPaneSize()
  }

  private fun cleanupRemovedRepositories(existingRepositories: List<PyPackageRepository>) {
    val removedRepositories = repositories.filter { it.repository !in existingRepositories }
    removedRepositories.forEach { it.removeFrom(uninstalledContainerPanel) }
    repositories.removeAll(removedRepositories)
  }

  private fun refreshInvalidRepositories(invalid: List<PyInvalidRepositoryViewData>) {
    val validKeys = invalid.map { it.repository.repositoryUrl.ifEmpty { it.repository.name } }.toHashSet()
    invalidRepositories.filter { (key, _) -> key !in validKeys }.forEach { (_, group) ->
      group.removeFrom(uninstalledContainerPanel)
    }
    invalidRepositories.keys.removeIf { it !in validKeys }

    for (data in invalid) {
      val key = data.repository.repositoryUrl.ifEmpty { data.repository.name }
      if (key in invalidRepositories) continue
      val tree = PyPackagesTree(project, controller)
      tree.items = emptyList()
      val group = PyPackagingTreeGroup(data.repository, tree, container, showHeader = true, useTreeNodeHeader = true, headerIcon = PyPackageIcons.RepositoryFailed, collapsible = false)
      group.addTo(uninstalledContainerPanel)
      invalidRepositories[key] = group
    }
  }

  fun selectPackage(packageName: String) {
    val repos = getRepos()
    for (repo in repos) {
      val pkg = repo.items.firstOrNull { it.name == packageName } ?: continue
      repo.tree.selectPackage(pkg)
    }
  }

  private fun findTableForRepo(repository: PyPackageRepository) = repositories.find { it.repository === repository }

  fun getSelectedPackages(): List<DisplayablePackage> {
    val repos = getRepos()
    return repos.flatMap { it.tree.selectedItems() }
  }

  fun setInstalledLoading(loading: Boolean) {
    installedPackages.setLoading(loading)
  }

  fun collapseAll() {
    getRepos().forEach { it.collapseAll() }
  }

  private fun getRepos() = listOf(installedPackages) + repositories

  internal fun syncTreeSelection(selectedTree: PyPackagesTree) {
    if (isSyncingTreeSelection) return

    try {
      isSyncingTreeSelection = true
      getRepos().map { it.tree }.filter { it != selectedTree }.forEach {
        it.clearSelection()
      }
    }
    finally {
      isSyncingTreeSelection = false
    }
  }
}
