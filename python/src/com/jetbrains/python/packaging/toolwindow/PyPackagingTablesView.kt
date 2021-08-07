// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.PyPIPackageUtil
import java.awt.Rectangle
import javax.swing.JPanel
import javax.swing.JTable

class PyPackagingTablesView(private val service: PyPackagingToolWindowService, private val container: JPanel) {
  private val repositories: MutableList<PyPackagingTableGroup<DisplayablePackage>> = mutableListOf()
  private val installedPackages = PyPackagingTableGroup(message("python.toolwindow.packages.installed.label"), "",
                                                PyPackagesTable(PyPackagesTableModel(), service, this))

  init {
    installedPackages.addTo(container)
    installedPackages.expand()
  }

  fun showSearchResult(installed: List<InstalledPackage>, repoData: List<PyPackagesViewData>) {
    updatePackages(installed, repoData)
    installedPackages.expand()
    installedPackages.updateHeaderText(installed.size)
    val tableToData = repositories.map { repo -> repo to repoData.find { it.repoUrl == repo.repoUrl }!! }
    tableToData.forEach { (table, data) ->
      table.updateHeaderText(data.packages.size + data.moreItems)
      table.expand()
    }

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

    for (data in repoData) {
      val existingRepo = findTableForRepo((data.repoUrl))
      val withExpander = if (data.moreItems > 0) data.packages + listOf(ExpandResultNode(data.moreItems, PyPackageRepository(data.repoUrl))) else data.packages

      if (existingRepo != null) existingRepo.items = withExpander
      else {
        val newTable = PyPackagesTable(PyPackagesTableModel(), service, this)
        newTable.items = withExpander

        val name = if (PyPIPackageUtil.isPyPIRepository(data.repoUrl)) message("python.toolwindow.packages.pypi.repo.label") else data.repoUrl
        val newTableGroup = PyPackagingTableGroup(name, data.repoUrl, newTable)

        repositories.add(newTableGroup)
        newTableGroup.addTo(container)
      }
    }
    val existingRepositories = repoData.map { it.repoUrl }

    val invalidRepos = repositories.filter { it.repoUrl !in existingRepositories }
    invalidRepos.forEach { it.removeFrom(container) }
    repositories.removeAll(invalidRepos)
  }

  private fun selectPackage(matchData: PyPackagesViewData) {
    val tableWithMatch = findTableForRepo(matchData.repoUrl)!!.table
    val exactMatch = matchData.exactMatch

    tableWithMatch.setRowSelectionInterval(exactMatch, exactMatch)
  }

  fun requestSelection(table: JTable) {
    if (table != installedPackages.table) installedPackages.table.clearSelection()
    repositories.asSequence()
      .filter { it.table != table }
      .forEach { it.table.clearSelection() }
  }

  fun packagesAdded(newPackages: List<InstalledPackage>) {
    addInstalled(newPackages)
    repositories.forEach {
          val selectedRow = it.table.selectedRow
          it.table.clearSelection()
          newPackages.forEach { pkg ->
            val index = it.table.items.indexOfFirst { item -> item.name == pkg.name }
            if (index != -1) {
              it.replace(index, pkg)
            }
          }

          if (selectedRow != -1) {
            it.table.setRowSelectionInterval(selectedRow, selectedRow)
          }
      }
  }

  private fun findTableForRepo(repoUrl: String) = repositories.find { it.repoUrl == repoUrl }

  fun addInstalled(newPackages: List<InstalledPackage>) {
    installedPackages.table.addRows(newPackages)
    installedPackages.updateHeaderText(installedPackages.table.items.size)
  }

  fun packageDeleted(deletedPackage: DisplayablePackage) {
    val index = installedPackages.items.indexOfFirst { it.name == deletedPackage.name }
    if (index != -1) {
      installedPackages.table.removeRow(index)
      installedPackages.itemsCount?.let {
        installedPackages.updateHeaderText(it - 1)
      }
    }

    repositories.forEach {  repo ->
      val repoIndex = repo.items.indexOfFirst { it.name == deletedPackage.name }
      if (repoIndex != -1) {
        repo.replace(repoIndex, InstallablePackage(deletedPackage.name, PyPackageRepository(repo.repoUrl)))
      }
    }

  }

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