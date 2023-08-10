// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.repository.PyPackageRepository
import java.awt.Rectangle
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable

class PyPackagingTablesView(private val project: Project,
                            private val container: JPanel,
                            private val controller: PyPackagingToolWindowPanel) {
  private val repositories: MutableList<PyPackagingTableGroup<DisplayablePackage>> = mutableListOf()
  private val installedPackages = PyPackagingTableGroup(
    object : PyPackageRepository(message("python.toolwindow.packages.installed.label"), "", "") {},
             PyPackagesTable(project, PyPackagesTableModel(), this, controller))
  private val invalidRepositories: MutableMap<String, JPanel> = mutableMapOf()
  init {
    installedPackages.addTo(container)
    installedPackages.expand()
  }

  fun showSearchResult(installed: List<InstalledPackage>, repoData: List<PyPackagesViewData>) {
    updatePackages(installed, repoData)
    installedPackages.expand()
    installedPackages.updateHeaderText(installed.size)
    val tableToData = repositories.map { repo -> repo to repoData.find { it.repository.name == repo.name }!! }
    tableToData.forEach { (table, data) ->
      table.updateHeaderText(data.packages.size + data.moreItems)
      table.expand()
    }

    // todo[akniazev]: selecting a package in 'installed' list might make more sense
    tableToData
      .firstOrNull { (_, data) -> data.exactMatch != -1 }
      ?.let { selectPackage(it.second) }
  }

  fun resetSearch(installed: List<InstalledPackage>, repoData: List<PyPackagesViewData>) {
    updatePackages(installed, repoData)
    installedPackages.expand()
    installedPackages.updateHeaderText(null)
    repositories.forEach {
      it.collapse()
      it.updateHeaderText(null)
    }
    container.scrollRectToVisible(Rectangle(0, 0))
  }

  private fun updatePackages(installed: List<InstalledPackage>, repoData: List<PyPackagesViewData>) {
    installedPackages.table.items = installed
    val (validRepoData, invalid) = repoData.partition { it !is PyInvalidRepositoryViewData }

    for (data in validRepoData) {
      val existingRepo = findTableForRepo(data.repository)
      val withExpander = if (data.moreItems > 0) data.packages + listOf(ExpandResultNode(data.moreItems, data.repository)) else data.packages

      if (existingRepo != null) {
        // recreate order of the repositories -- it might have changed in the package manager (e.g. Sdk switch)
        existingRepo.removeFrom(container)
        existingRepo.items = withExpander
        existingRepo.addTo(container)
      }
      else {
        val newTable = PyPackagesTable(project, PyPackagesTableModel(), this, controller)
        newTable.items = withExpander

        val newTableGroup = PyPackagingTableGroup(data.repository, newTable)

        repositories.add(newTableGroup)
        newTableGroup.addTo(container)
      }
    }
    val existingRepositories = validRepoData.map { it.repository.name }

    val removedRepositories = repositories.filter { it.name !in existingRepositories }
    removedRepositories.forEach { it.removeFrom(container) }
    repositories.removeAll(removedRepositories)

    @Suppress("UNCHECKED_CAST")
    refreshInvalidRepositories(invalid as List<PyInvalidRepositoryViewData>)
  }

  private fun refreshInvalidRepositories(invalid: List<PyInvalidRepositoryViewData>) {
    val invalidRepoNames = invalid.map { it.repository.name }

    invalidRepositories.forEach { container.remove(it.value) }
    invalidRepositories.keys.removeIf { it !in invalidRepoNames }

    invalid.asSequence()
      .map { it.repository.name!! }
      .filterNot { it in invalidRepositories }
      .map {
        val label = JLabel(message("python.toolwindow.packages.custom.repo.invalid", it)).apply {
          foreground = JBColor.RED
          icon = AllIcons.General.Error
        }
        it to headerPanel(label, null)
      }
      .forEach {
        invalidRepositories[it.first] = it.second
      }
    invalidRepositories.forEach { container.add(it.value) }
  }

  private fun selectPackage(matchData: PyPackagesViewData) {
    val tableWithMatch = findTableForRepo(matchData.repository)!!.table
    val exactMatch = matchData.exactMatch

    tableWithMatch.setRowSelectionInterval(exactMatch, exactMatch)
  }

  fun requestSelection(table: JTable) {
    if (table != installedPackages.table) installedPackages.table.clearSelection()
    repositories.asSequence()
      .filter { it.table != table }
      .forEach { it.table.clearSelection() }
  }

  private fun findTableForRepo(repository: PyPackageRepository) = repositories.find { it.name == repository.name }

  fun selectNextFrom(currentTable: JTable) {
    val targetGroup = when (currentTable) {
      installedPackages.table -> repositories.firstOrNull()
      else -> {
        val currentIndex = repositories.indexOfFirst { it.table == currentTable }
        if (currentIndex + 1 != repositories.size)  repositories[currentIndex + 1]
        else return
      }
    }


    targetGroup?.apply {
      if (items.isNotEmpty()) {
        expand()
        table.setRowSelectionInterval(0, 0)
        table.requestFocus()
      }
    }
  }

  fun selectPreviousOf(currentTable: JTable) {
    val targetGroup = when (currentTable) {
      installedPackages.table -> return
      repositories.firstOrNull()?.table -> installedPackages
      else -> {
        val currentIndex = repositories.indexOfFirst { it.table == currentTable }
        repositories[currentIndex - 1]
      }
    }


    targetGroup.apply {
      if (items.isNotEmpty()) {
        expand()
        val newIndex = items.size - 1
        table.setRowSelectionInterval(newIndex, newIndex)
        table.requestFocus()
      }
    }
  }
}