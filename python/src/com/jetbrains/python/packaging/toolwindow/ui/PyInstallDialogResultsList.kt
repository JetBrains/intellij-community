// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.repository.PyPackageRepositories
import com.jetbrains.python.packaging.toolwindow.PyPackageIcons
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.AbstractListModel
import javax.swing.DefaultListModel
import javax.swing.JScrollPane
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel

/**
 * Closed set of node types displayed in the install dialog results list.
 */
internal sealed interface PyInstallResultNode

internal data class PackageLeafNode(@param:NlsContexts.Label val packageName: String, @NlsSafe val repoName: String) : PyInstallResultNode
internal data class CommandLeafNode(@param:NlsSafe val fullCommand: String) : PyInstallResultNode

internal class PyInstallDialogResultsList(
  private val project: Project,
  private val packagingService: PyPackagingToolWindowService,
  private val onPackageSelected: (name: String, repoName: String) -> Unit,
  private val onCommandSelected: (command: String) -> Unit,
  private val onSelectionCleared: () -> Unit,
  private val onResultsUpdated: (hasResults: Boolean) -> Unit = {},
) {
  /**
   * Globally priority-sorted flat list of matches for the active query (exact → prefix →
   * substring, tied by name). Pre-sorted so model rows mirror the legacy "merge repositories,
   * then sort across them" behaviour and the visible list never reshuffles when more rows are
   * revealed by scrolling.
   *
   * Materialised once per [performSearch] from every cached page of every repository — bounded
   * by what the cache returns for the typed query (PyPI prefix matches for "requests" ≈ a few
   * hundred, not 800k). An empty query short-circuits with an empty list.
   */
  private var flatResults: List<PackageLeafNode> = emptyList()
  private var displayed: Int = 0
  private var loadMoreJob: Job? = null
  private var searchJob: Job? = null
  private val pageSize: Int = INSTALL_DIALOG_PAGE

  val model: AbstractListModel<Any>
    field: DefaultListModel<Any> = DefaultListModel()
  val list: JBList<Any> = object : JBList<Any>(model) {
    override fun hasFocus(): Boolean = true
  }.apply {
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    background = UIUtil.getListBackground()
    isFocusable = false
    visibleRowCount = 12

    addListSelectionListener { e ->
      if (e.valueIsAdjusting) return@addListSelectionListener
      handleSelection()
    }
  }

  val renderer: ListCellRenderer<Any> = createRenderer()

  private fun createRenderer(): ListCellRenderer<Any> = listCellRenderer {
    when (val v = value) {
      is PackageLeafNode -> {
        icon(PyPackageIcons.PackageGray)
        text(v.packageName)
        text(v.repoName) {
          foreground = greyForeground
        }
      }
      is CommandLeafNode -> {
        icon(AllIcons.Debugger.Console)
        text(v.fullCommand)
      }
      else -> text("")
    }
  }

  private fun handleSelection() {
    val idx = list.selectedIndex
    if (idx < 0) {
      onSelectionCleared()
      return
    }
    when (val sel = list.selectedValue) {
      is PackageLeafNode -> onPackageSelected(sel.packageName, sel.repoName)
      is CommandLeafNode -> onCommandSelected(sel.fullCommand)
      else -> onSelectionCleared()
    }
  }

  fun performSearch(query: String) {
    val sdk = packagingService.currentSdk ?: return
    val packageManager = PythonPackageManager.forSdk(project, sdk)
    loadMoreJob?.cancel()
    searchJob?.cancel()
    if (query.isBlank()) {
      updateResults(emptyList())
      return
    }
    searchJob = packagingService.serviceScope.launch {
      val repoService = service<PyPackageRepositories>()
      val managedSet = packageManager.repositoryManager.repositories.toSet()
      val results = aggregateInstallDialogSearch(
        repoManager = packageManager.repositoryManager,
        extraRepositories = repoService.repositories,
        query = query,
      )
      val sorted = withContext(Dispatchers.Default) {
        buildInstallDialogResults(results, managedSet, query)
      }
      withContext(Dispatchers.EDT) { updateResults(sorted) }
    }
  }

  /** Replaces the results with a pre-sorted [flat] list and reveals the first visual page. */
  fun updateResults(flat: List<PackageLeafNode>) {
    flatResults = flat
    displayed = 0
    model.clear()
    appendPage()
    val hasResults = flat.isNotEmpty()
    if (hasResults) list.selectedIndex = 0
    onResultsUpdated(hasResults)
  }

  fun attachToScroll(scrollPane: JScrollPane) {
    scrollPane.verticalScrollBar.addAdjustmentListener { maybeLoadMore(scrollPane) }
  }

  private fun maybeLoadMore(scrollPane: JScrollPane) {
    if (displayed >= flatResults.size) return
    val bar = scrollPane.verticalScrollBar
    val threshold = maxOf(loadMoreMinThresholdPx(), bar.visibleAmount / 2)
    if (bar.value + bar.visibleAmount < bar.maximum - threshold) return
    appendPage()
  }

  private fun appendPage() {
    val from = displayed
    val to = minOf(from + pageSize, flatResults.size)
    if (from >= to) return
    for (i in from until to) model.addElement(flatResults[i])
    displayed = to
  }

  fun selectPackageByName(@NlsSafe name: String): Boolean {
    for (i in 0 until model.size()) {
      val v = model[i] as PackageLeafNode
      if (v.packageName.equals(name, ignoreCase = true)) {
        list.selectedIndex = i
        list.ensureIndexIsVisible(i)
        return true
      }
    }
    return false
  }
}

private fun loadMoreMinThresholdPx(): Int =
  Registry.intValue("python.packaging.install.dialog.results.list.load.more.threshold.px", 64)
