// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.SimpleToolWindowPanel.LEFT_ALIGNMENT
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowPanel
import com.jetbrains.python.packaging.toolwindow.PyPackagingTreeView
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.ErrorNode
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.model.PyPackagesViewData
import java.awt.BorderLayout
import javax.swing.*

internal class PyPackagesListController(val project: Project, val controller: PyPackagingToolWindowPanel) : Disposable {
  private val packageListPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    alignmentX = LEFT_ALIGNMENT
    background = UIUtil.getListBackground()
  }

  private val packageListOuterPanel = JPanel(BorderLayout()).apply {
    add(packageListPanel, BorderLayout.NORTH)
  }

  private val tablesView = PyPackagingTreeView(project, packageListPanel, controller)

  private val scrollingPackageListComponent: JScrollPane = ScrollPaneFactory.createScrollPane(packageListOuterPanel, true).apply {
    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
  }

  private val loadingPanel = JBPanelWithEmptyText().apply {
    emptyText.appendLine(AnimatedIcon.Default.INSTANCE, message("python.toolwindow.packages.description.panel.loading"), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES, null)
  }

  private val noSdkPanel = JBPanelWithEmptyText().apply {
    emptyText.text = message("python.sdk.no.interpreter.selected")
  }

  val component: JPanel = JPanel(BorderLayout())

  init {
    setLoadingState(false)
  }

  override fun dispose() {}

  fun showSearchResult(installed: List<DisplayablePackage>, repoData: List<PyPackagesViewData>) {
    tablesView.showSearchResult(installed, repoData)
    setLoadingState(false)
  }

  fun resetSearch(installed: List<InstalledPackage>, repos: List<PyPackagesViewData>, currentSdk: Sdk?) {
    tablesView.resetSearch(installed, repos, currentSdk)
    setLoadingState(false)
  }

  fun selectPackage(name: String) {
    tablesView.selectPackage(name)
  }

  fun getSelectedPackages(): List<DisplayablePackage> {
    return tablesView.getSelectedPackages()
  }

  fun startSdkInit() {
    setLoadingState(true)
  }

  fun collapseAll() {
    tablesView.collapseAll()
  }

  fun showErrorResult(errorNode: ErrorNode) {
    tablesView.showErrorResult(errorNode)
  }

  @RequiresEdt
  internal fun showNoSdkMessage() {
    setContentPanel(noSdkPanel)
  }

  @RequiresEdt
  internal fun setLoadingState(isLoading: Boolean) {
    val newPanel = if (isLoading) loadingPanel else scrollingPackageListComponent
    setContentPanel(newPanel)
  }

  private fun setContentPanel(panel: JComponent) {
    val currentComponent = component.components.firstOrNull()
    if (currentComponent != panel) {
      component.removeAll()
      component.add(panel)
      component.revalidate()
      component.repaint()
    }
  }
}