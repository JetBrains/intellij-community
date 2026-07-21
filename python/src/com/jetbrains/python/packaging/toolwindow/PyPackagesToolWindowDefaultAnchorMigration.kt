// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager

private const val MIGRATION_FLAG_KEY = "python.packages.toolwindow.default.anchor.migrated.v2"
private const val TOOL_WINDOW_ID = "Python Packages"

/**
 * The Python Packages tool window now defaults to the **right** stripe (alongside AI Assistant /
 * Database), but the app-level [ToolWindowDefaultLayoutManager] caches whatever stripe the user
 * was last on across projects. When the branch shipped earlier intermediate registrations on the
 * `bottom` and `left` stripes, that cache picked them up — and every freshly-created project then
 * inherited the wrong stripe, no matter what the plugin XML or [PyToolWindowLayoutProvider]
 * declare.
 *
 * This activity runs once per IDE install: if the active default layout has the Python Packages
 * window pinned to anything other than RIGHT, force it back to RIGHT and re-save. Existing
 * per-project [workspace.xml] entries still win (they are read before the default layout), so a
 * project where the user has explicitly moved the tool window will keep its custom position; only
 * new projects (and projects without saved layout for this id) are affected.
 */
internal class PyPackagesToolWindowDefaultAnchorMigration : ProjectActivity {
  override suspend fun execute(project: Project) {
    val properties = PropertiesComponent.getInstance()
    if (properties.getBoolean(MIGRATION_FLAG_KEY)) return

    val manager = ToolWindowDefaultLayoutManager.getInstance()
    val layout = manager.getLayoutCopy()
    val info = layout.getInfo(TOOL_WINDOW_ID)
    if (info != null && (info.anchor != ToolWindowAnchor.RIGHT || !info.isSplit)) {
      info.anchor = ToolWindowAnchor.RIGHT
      info.isSplit = true
      info.sideWeight = 0.5f
      manager.setLayout(layout)
    }
    properties.setValue(MIGRATION_FLAG_KEY, true)
  }
}
