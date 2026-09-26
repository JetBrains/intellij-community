// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PY-89840: put the "Python Packages" tool window on the right stripe by default.
 *
 * Migrates the application-wide default layout once; new projects (and projects without a saved layout for
 * this id) pick up the new anchor from that default. Projects that already persisted a custom placement
 * keep it.
 */
internal class PyPackagesToolWindowMigrationActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    val appProperties = PropertiesComponent.getInstance()
    if (!appProperties.getBoolean(APP_MIGRATION_FLAG_KEY)) {
      val manager = ToolWindowDefaultLayoutManager.getInstance()
      val layout = manager.getLayoutCopy()
      val info = layout.getInfo(PyPackagingToolWindowPanel.PY_PACKAGES_TOOL_WINDOW_ID)
      if (info != null && (info.anchor != ToolWindowAnchor.RIGHT || info.isSplit)) {
        info.anchor = ToolWindowAnchor.RIGHT
        info.isSplit = false
        manager.setLayout(layout)
      }
      appProperties.setValue(APP_MIGRATION_FLAG_KEY, true)
    }

    val projectProperties = PropertiesComponent.getInstance(project)
    if (projectProperties.getBoolean(PROJECT_MIGRATION_FLAG_KEY)) return

    withContext(Dispatchers.Main) {
      if (project.isDisposed) return@withContext
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(PyPackagingToolWindowPanel.PY_PACKAGES_TOOL_WINDOW_ID)
                       ?: return@withContext
      if (toolWindow.anchor != ToolWindowAnchor.RIGHT || toolWindow.isSplitMode) {
        toolWindow.setAnchor(ToolWindowAnchor.RIGHT, null)
        toolWindow.setSplitMode(false, null)
      }
    }
    projectProperties.setValue(PROJECT_MIGRATION_FLAG_KEY, true)
  }

  companion object {
    private const val APP_MIGRATION_FLAG_KEY: String = "python.packages.toolwindow.migrated.to.right.top.v2"
    private const val PROJECT_MIGRATION_FLAG_KEY: String = "python.packages.toolwindow.project.migrated.to.right.top.v2"
  }
}
