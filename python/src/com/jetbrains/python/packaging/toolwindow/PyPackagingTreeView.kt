// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.ui.JBColor
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.repository.InstalledPyPackagedRepository
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.toolwindow.model.*
import com.jetbrains.python.packaging.toolwindow.packages.PyPackagingTreeGroup
import com.jetbrains.python.packaging.toolwindow.packages.tree.PyPackagesTreeTable
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JLabel
import javax.swing.JPanel

internal class PyPackagingTreeView(
  private val project: Project,
  private val container: JPanel,
  private val controller: PyPackagingToolWindowPanel,
) {
  private val repositories: MutableList<PyPackagingTreeGroup> = mutableListOf()
  private val installedPackages =
    PyPackagingTreeGroup(
      InstalledPyPackagedRepository(),
      PyPackagesTreeTable(project, controller),
      container
    )

  private val invalidRepositories: MutableMap<String, JPanel> = mutableMapOf()

  private var isSyncingTreeSelection = false

  init {
    installedPackages.addTo(container)
    installedPackages.expand()

    container.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        synchronizeScrollPaneSize()
      }
    })

    getRepos().forEach { tree ->
      tree.tree.tree.addTreeSelectionListener {
        syncTreeSelection(tree.tree)
      }
    }
  }

  fun showSearchResult(installed: List<DisplayablePackage>, repoData: List<PyPackagesViewData>) {
    updatePackages(installed, repoData)

    installedPackages.expand()
    installedPackages.updatePreferredSize()
    installedPackages.updateHeaderText(installed.size)

    val tableToData = repositories.map { repo -> repo to repoData.find { it.repository.name == repo.repositoryName }!! }
    tableToData.forEach { (table, data) ->
      table.updateHeaderText(data.packages.size + data.moreItems)
      table.expand()
    }

    synchronizeScrollPaneSize()

    val exactMatchPackageName = tableToData
      .firstOrNull { (_, data) -> data.exactMatch != -1 }
      ?.second?.let { data -> data.packages.getOrNull(data.exactMatch)?.name }

    exactMatchPackageName?.let { packageName ->
      val installedPackageIndex = installedPackages.items.indexOfFirst { it.name == packageName }
      if (installedPackageIndex != -1) {
        installedPackages.tree.tree.setSelectionInterval(installedPackageIndex, installedPackageIndex)
        installedPackages.tree.table.setRowSelectionInterval(installedPackageIndex, installedPackageIndex)
      }
    }
  }

  fun resetSearch(installed: List<InstalledPackage>, repoData: List<PyPackagesViewData>, currentSdk: Sdk?) {
    updatePackages(installed, repoData)

    installedPackages.expand()
    installedPackages.setSdkToHeader(currentSdk?.name)

    repositories.forEach {
      it.collapseAll()
      it.updateHeaderText(null)
    }

    container.scrollRectToVisible(Rectangle(0, 0))
  }

  private fun updatePackages(installed: List<DisplayablePackage>, repoData: List<PyPackagesViewData>) {
    val sortedInstalled = installed.sortedBy { it.name }
    installedPackages.tree.items = sortedInstalled
    updateExistingRepository(installedPackages, sortedInstalled)
    val (validRepoData, invalidData) = repoData.partition { it !is PyInvalidRepositoryViewData }

    updateValidRepositories(validRepoData)

    cleanupRemovedRepositories(validRepoData.map { it.repository.name })

    val invalidRepoData = invalidData.filterIsInstance<PyInvalidRepositoryViewData>()
    refreshInvalidRepositories(invalidRepoData)

  }

  private fun synchronizeScrollPaneSize() {
    getRepos().forEach { repo ->
      repo.updatePreferredSize()
    }

    container.revalidate()
    container.repaint()
  }

  private fun updateValidRepositories(validRepoData: List<PyPackagesViewData>) {
    for (data in validRepoData) {
      val withExpander = if (data.moreItems > 0) {
        data.packages + listOf(ExpandResultNode(data.moreItems, data.repository))
      }
      else {
        data.packages
      }

      val existingRepo = findTableForRepo(data.repository)

      if (existingRepo != null) {
        updateExistingRepository(existingRepo, withExpander)
      }
      else {
        createNewRepository(data.repository, withExpander)
      }
    }
  }

  private fun updateExistingRepository(repo: PyPackagingTreeGroup, items: List<DisplayablePackage>) {
    repo.removeFrom(container)
    val selectedPackage = repo.items.firstOrNull { it in repo.tree.items }
    repo.items = items
    repo.addTo(container)
    if (selectedPackage != null) {
      repo.tree.items = items
    }

    synchronizeScrollPaneSize()
  }

  private fun createNewRepository(repository: PyPackageRepository, items: List<DisplayablePackage>) {
    val newTable = PyPackagesTreeTable(project, controller)
    newTable.items = items
    val newTableGroup = PyPackagingTreeGroup(repository, newTable, container)
    newTableGroup.items = items
    repositories.add(newTableGroup)
    newTableGroup.addTo(container)
    newTable.tree.addTreeSelectionListener {
        syncTreeSelection(newTable)
      }
    synchronizeScrollPaneSize()
  }

  private fun cleanupRemovedRepositories(existingRepositoryNames: List<String>) {
    val removedRepositories = repositories.filter { it.repositoryName !in existingRepositoryNames }
    removedRepositories.forEach { it.removeFrom(container) }
    repositories.removeAll(removedRepositories)
  }

  private fun refreshInvalidRepositories(invalid: List<PyInvalidRepositoryViewData>) {
    val invalidRepoNames = invalid.map { it.repository.name }

    invalidRepositories.forEach { container.remove(it.value) }
    invalidRepositories.keys.removeIf { it !in invalidRepoNames }

    invalid.asSequence()
      .map { it.repository.name }
      .filterNot { it in invalidRepositories }
      .map {
        val label = JLabel(message("python.toolwindow.packages.custom.repo.invalid", it)).apply {
          foreground = JBColor.RED
          icon = AllIcons.General.Error
        }
        it to PyPackagesUiComponents.headerPanel(label, null)
      }
      .forEach {
        invalidRepositories[it.first] = it.second
      }
    invalidRepositories.forEach { container.add(it.value) }
  }

  fun selectPackage(packageName: String) {
    val repos = getRepos()
    for (repo in repos) {
      val pkg = repo.items.firstOrNull { it.name == packageName } ?: continue
      repo.tree.selectPackage(pkg)
    }
  }

  private fun findTableForRepo(repository: PyPackageRepository) = repositories.find { it.repositoryName == repository.name }

  fun getSelectedPackages(): List<DisplayablePackage> {
    val repos = getRepos()
    return repos.flatMap { it.tree.selectedItems() }
  }

  fun collapseAll() {
    getRepos().forEach { it.collapseAll() }
  }

  private fun getRepos() = listOf(installedPackages) + repositories

  internal fun syncTreeSelection(selectedTree: PyPackagesTreeTable) {
    if (isSyncingTreeSelection) return

    try {
      isSyncingTreeSelection = true
      getRepos().map { it.tree }.filter { it != selectedTree }.forEach {
        it.tree.clearSelection()
        it.table.clearSelection()
      }
    } finally {
      isSyncingTreeSelection = false
    }
  }
}
