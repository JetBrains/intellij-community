// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui.configuration

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.actionsOnSave.ActionsOnSaveConfigurable
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.python.pytools.ui.PyToolsUiBundle.message
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.launchOnShow
import com.jetbrains.python.TraceContext
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.supervisorScope
import javax.swing.JComponent

class PyExternalToolsConfigurable(private val project: Project) : BoundSearchableConfigurable(
  displayName = message("settings.external.tools.title"),
  helpTopic = "",
  _id = ID,
) {

  /**
   * Owns the uv-state snapshot + install/upgrade actions. Its coroutine scope is supplied later by
   * [createPanel]'s `launchOnShow` block via [UvController.onShown], so background work lives for
   * the panel's showing-lifetime.
   */
  private val uv: UvController = UvController(
    project = project,
    onStateChanged = ::onUvStateChanged,
    refreshRow = ::refreshRow,
  )

  /** Initialized once in [createPanel]; every method that touches it is invoked while the UI is live. */
  private lateinit var toolsTable: PyExternalToolsTable

  /** Wired in as [UvController.onStateChanged]; refresh the table on EDT. The hint footer rebinds via [UvController.uvAvailable]. */
  private fun onUvStateChanged() {
    toolsTable.fireAllRowsChanged()
  }

  /** Wired in as [UvController.refreshRow]; delegate to the table. */
  private fun refreshRow(item: ToolRow) {
    toolsTable.refreshRow(item)
  }

  /**
   * Called by the IDE when a global Settings search hit lands on this configurable. We use the
   * search text (typically a tool's presentable name) to select and scroll the matching row.
   */
  override fun enableSearch(option: String?): Runnable? {
    // The Settings framework may call this from the searchable-options index builder before the
    // page is visible, i.e. before [createPanel] has run; bail out cleanly in that case.
    if (!::toolsTable.isInitialized) return null
    // Settings calls this on every search-text change, including the transition back to an empty
    // query when the user clears the field. Return a Runnable for empty/no-match input so we can
    // drop the spotlight border in lock-step with the search field, instead of leaving it
    // lingering until the user clicks.
    if (option.isNullOrBlank()) {
      return Runnable { toolsTable.clearSelection() }
    }
    val match = toolsTable.findMatchingRowIndex(option)
    if (match < 0) {
      return Runnable { toolsTable.clearSelection() }
    }
    return Runnable { toolsTable.selectForSearchHit(match) }
  }

  /** Give focus to the table when the page opens so the very first cell click starts editing. */
  override fun getPreferredFocusedComponent(): JComponent = toolsTable.view

  override fun createPanel(): DialogPanel {
    toolsTable = PyExternalToolsTable(project, uv)

    val scrollPane = JBScrollPane(toolsTable.view).apply {
      border = JBUI.Borders.empty()
      viewportBorder = JBUI.Borders.empty()
    }
    val framedTable = BorderLayoutPanel()
      .addToTop(buildHeaderBar(toolsTable.view))
      .addToCenter(scrollPane)
      .apply { border = IdeBorderFactory.createBorder(SideBorder.ALL) }

    val resultPanel = panel {
      row {
        text(
          text = message("settings.external.tools.description"),
          action = HyperlinkEventAction { event ->
            if (event.description == "actionsOnSave") openActionsOnSavePage()
          },
        )
      }

      row {
        cell(framedTable).align(Align.FILL)
      }.resizableRow()

      row {
        icon(AllIcons.General.Warning).gap(RightGap.SMALL)
        label(message("settings.external.tools.uv.hint.not.installed")).resizableColumn()
        button(message("settings.external.tools.uv.hint.install.button")) { uv.installUv() }
      }.visibleIf(uv.uvAvailable.transform { it == false })
    }

    // Owns the lifetime of uv detection + path/version probes. The supervisorScope stays alive
    // (via [awaitCancellation]) past the initial setup so click-driven uv install/upgrade
    // follow-ups still launch into a live scope. Cancellation is automatic when the panel is
    // hidden, and the block re-runs (with a fresh scope) on subsequent shows.
    resultPanel.launchOnShow(
      "${this::class.java.simpleName} launchOnShow",
      TraceContext(message("trace.context.python.external.tools"), null),
    ) {
      supervisorScope {
        uv.onShown(this@supervisorScope)
        toolsTable.onShown(this@supervisorScope)
        awaitCancellation()
      }
    }

    return resultPanel
  }

  override fun isModified(): Boolean = toolsTable.isModified()

  override fun apply(): Unit = toolsTable.apply()

  override fun reset(): Unit = toolsTable.reset()

  override fun disposeUIResources() {
    toolsTable.disposeUIResources()
    super.disposeUIResources()
  }

  private fun openActionsOnSavePage() {
    DataManager.getInstance().dataContextFromFocusAsync.onSuccess { context ->
      Settings.KEY.getData(context)?.let { settings ->
        settings.select(settings.find(ActionsOnSaveConfigurable.CONFIGURABLE_ID))
      }
    }
  }

  companion object {
    const val ID: String = "python.external.tools.group.settings"
  }
}
