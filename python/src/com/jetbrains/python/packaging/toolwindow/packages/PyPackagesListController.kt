// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.SimpleToolWindowPanel.LEFT_ALIGNMENT
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowPanel
import com.jetbrains.python.packaging.toolwindow.PyPackagingTreeView
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.PyPackagesViewData
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants

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

  private val noSdkPanel = JBPanelWithEmptyText().apply {
    emptyText.text = message("python.sdk.no.interpreter.selected")
  }

  val component: JPanel = JPanel(BorderLayout())
  private var currentPanel: JComponent? = null

  init {
    setContentPanel(scrollingPackageListComponent)
  }

  override fun dispose() {}

  @RequiresEdt
  fun showSearchResult(installed: List<DisplayablePackage>, repoData: List<PyPackagesViewData>) {
    showPackageList()
    tablesView.showSearchResult(installed, repoData)
  }

  @RequiresEdt
  fun resetSearch(installed: List<DisplayablePackage>, repos: List<PyPackagesViewData>, currentSdk: Sdk?) {
    showPackageList()
    tablesView.resetSearch(installed, repos, currentSdk)
  }

  fun selectPackage(name: String) {
    tablesView.selectPackage(name)
  }

  fun getSelectedPackages(): List<DisplayablePackage> {
    return tablesView.getSelectedPackages()
  }

  fun setSdkName(@Nls sdkName: String) {
    tablesView.setSdkName(sdkName)
  }

  fun startSdkInit() {
    setContentPanel(scrollingPackageListComponent)
    setLoadingState(true)
  }

  fun collapseAll() {
    tablesView.collapseAll()
  }

  @RequiresEdt
  internal fun showNoSdkMessage() {
    setContentPanel(noSdkPanel)
  }

  private fun showPackageList() {
    setContentPanel(scrollingPackageListComponent)
    setLoadingState(false)
  }

  internal fun setLoadingState(isLoading: Boolean) {
    tablesView.setInstalledLoading(isLoading)
  }

  private fun setContentPanel(panel: JComponent) {
    if (currentPanel != panel) {
      currentPanel = panel
      component.removeAll()
      component.add(panel)
      component.revalidate()
      component.repaint()
    }
  }
}
