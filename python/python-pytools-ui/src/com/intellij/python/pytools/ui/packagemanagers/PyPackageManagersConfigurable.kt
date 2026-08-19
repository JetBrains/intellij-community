// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui.packagemanagers

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.python.pytools.ui.PyToolsUiBundle.message
import com.intellij.python.pytools.ui.configuration.PyToolManagementController
import com.intellij.python.pytools.ui.configuration.ToolRow
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.launchOnShow
import com.jetbrains.python.TraceContext
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.supervisorScope
import javax.swing.JComponent

/**
 * `Settings | Python | Tools | Package Managers` — a flat table of the package/environment managers
 * (uv, Poetry, Hatch, Pipenv) showing each one's detected executable path with inline install /
 * upgrade / browse actions. Mirrors [com.intellij.python.pytools.ui.configuration.PyExternalToolsConfigurable]
 * but simpler: no per-tool feature panel, no enable toggle, no lookup column.
 */
class PyPackageManagersConfigurable(private val project: Project) : BoundSearchableConfigurable(
  displayName = message("settings.package.managers.title"),
  helpTopic = "",
  _id = ID,
) {

  private val uv: PyToolManagementController = PyToolManagementController(
    project = project,
    onStateChanged = ::onUvStateChanged,
    refreshRow = ::refreshRow,
  )

  private lateinit var toolsList: PyPackageManagersList

  private fun onUvStateChanged() {
    toolsList.fireAllRowsChanged()
  }

  private fun refreshRow(item: ToolRow) {
    toolsList.refreshRow(item)
  }

  override fun enableSearch(option: String?): Runnable? {
    if (!::toolsList.isInitialized) return null
    if (option.isNullOrBlank()) return Runnable { toolsList.clearSelection() }
    val match = toolsList.findMatchingRowIndex(option)
    if (match < 0) return Runnable { toolsList.clearSelection() }
    return Runnable { toolsList.selectForSearchHit(match) }
  }

  override fun getPreferredFocusedComponent(): JComponent = toolsList.view

  override fun createPanel(): DialogPanel {
    toolsList = PyPackageManagersList(project, uv)

    val scrollPane = JBScrollPane(toolsList.view).apply {
      border = JBUI.Borders.empty()
      viewportBorder = JBUI.Borders.empty()
      setColumnHeaderView(buildPackageManagersHeaderBar())
    }
    val framedTable = BorderLayoutPanel()
      .addToCenter(scrollPane)
      .apply { border = IdeBorderFactory.createBorder(SideBorder.ALL) }

    val resultPanel = panel {
      row {
        text(message("settings.package.managers.description"))
      }
      row {
        cell(framedTable).align(Align.FILL)
      }.resizableRow()
    }

    resultPanel.launchOnShow(
      "${this::class.java.simpleName} launchOnShow",
      TraceContext(message("trace.context.python.package.managers"), null),
    ) {
      supervisorScope {
        uv.onShown(this@supervisorScope)
        toolsList.onShown(this@supervisorScope)
        awaitCancellation()
      }
    }

    return resultPanel
  }

  override fun isModified(): Boolean = toolsList.isModified()
  override fun apply(): Unit = toolsList.apply()
  override fun reset(): Unit = toolsList.reset()

  override fun disposeUIResources() {
    toolsList.disposeUIResources()
    super.disposeUIResources()
  }

  companion object {
    const val ID: String = "python.package.managers.group.settings"
  }
}
