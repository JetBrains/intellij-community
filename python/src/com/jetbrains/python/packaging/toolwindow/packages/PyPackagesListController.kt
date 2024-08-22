// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel.LEFT_ALIGNMENT
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.packaging.toolwindow.PyPackagingTablesView
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowPanel
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.model.PyPackagesViewData
import javax.swing.BoxLayout
import javax.swing.JPanel

class PyPackagesListController(val project: Project, val controller: PyPackagingToolWindowPanel) : Disposable {
  private val packageListPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    alignmentX = LEFT_ALIGNMENT
    background = UIUtil.getListBackground()
  }

  private val tablesView = PyPackagingTablesView(project, packageListPanel, controller)

  val component = ScrollPaneFactory.createScrollPane(packageListPanel, true)

  override fun dispose() {}

  fun showSearchResult(installed: List<InstalledPackage>, repoData: List<PyPackagesViewData>) {
    tablesView.showSearchResult(installed, repoData)
  }

  fun resetSearch(installed: List<InstalledPackage>, repos: List<PyPackagesViewData>) {
    tablesView.resetSearch(installed, repos)
  }
}