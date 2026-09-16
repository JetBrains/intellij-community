// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.typeEngine.frontend

import com.intellij.icons.AllIcons
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.project.projectId
import com.intellij.python.pytools.frontend.ui.PyToolTypeEnginePreview
import com.intellij.python.pytools.frontend.ui.configuration.PyExternalToolsConfigurable
import com.intellij.python.typeEngine.common.PyTypeEngineApi
import com.intellij.python.typeEngine.common.PyTypeEngineId
import com.intellij.python.typeEngine.common.PyTypeEngineSelectionRequest
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.SegmentedButton

class PyTypeEngineConfigurable(
  private val project: Project,
) : UiDslUnnamedConfigurable.Simple(), SearchableConfigurable {
  private val propertyGraph = PropertyGraph()
  private val selectedTypeEngine = propertyGraph.property(PyTypeEngineId.PYCHARM)

  private val availableOptions: List<PyTypeEngineFrontend>
    get() = PyTypeEngineFrontend.getSupported(project)

  private var previousTypeEngine: PyTypeEngineId = PyTypeEngineFrontendState.getInstance(project).get().selected

  /** The shared, transient staged-engine bridge the External Tools page also reads/writes. */
  private val stagedEngine get() = PyToolTypeEnginePreview.getInstance(project).stagedEnginePackage

  /** Scopes the bridge→button observer to this page's lifetime. */
  private val previewObserverDisposable = Disposer.newDisposable()

  /** The engine segmented button, captured so the bridge observer can flip it when the tool is turned off elsewhere. */
  private lateinit var engineButton: SegmentedButton<PyTypeEngineId>

  init {
    val initialOption = previousTypeEngine
    selectedTypeEngine.set(initialOption)
  }

  override fun getId(): String = "pycharm.type.engine"
  override fun getDisplayName(): String = TypeEngineFrontendBundle.message("display.name")
  override fun getDisplayNameFast(): String = TypeEngineFrontendBundle.message("display.name")
  override fun getHelpTopic(): String = "reference.settings.python.type.engine"

  override fun disposeUIResources() {
    Disposer.dispose(previewObserverDisposable)
    stagedEngine.set(null)
    PyToolTypeEnginePreview.getInstance(project).pendingDisable.set(emptySet())
    super<UiDslUnnamedConfigurable.Simple>.disposeUIResources()
  }

  private fun engineTypeFor(pkg: String?): PyTypeEngineId =
    availableOptions.firstOrNull { it.id.packageName == pkg }?.id ?: PyTypeEngineId.PYCHARM

  private fun frontendFor(id: PyTypeEngineId): PyTypeEngineFrontend =
    availableOptions.first { it.id == id }

  /**
   * A user picked [newEngine] in the segmented button. Publish it to the shared bridge, and — when
   * switching **away** from a tool-backed engine — offer to turn that engine's External Tools tool off
   * too (it may be used for other purposes). The choice is staged in [PyToolTypeEnginePreview.pendingDisable]
   * and committed at Apply, so it works whether or not the External Tools page is open.
   */
  private fun onEngineSelectedByUser(newEngine: PyTypeEngineId) {
    val preview = PyToolTypeEnginePreview.getInstance(project)
    val oldType = engineTypeFor(stagedEngine.get())
    stagedEngine.set(newEngine.packageName)
    // Re-selecting an engine cancels any pending "disable" for its own tool.
    if (newEngine.packageName.isNotEmpty()) {
      preview.pendingDisable.set(preview.pendingDisable.get() - newEngine.packageName)
    }
    // Only prompt when leaving the *persisted* (actually active) engine. A merely staged engine that was
    // never applied has nothing enabled to turn off — its tool auto-reverts on the External Tools page.
    if (oldType == PyTypeEngineId.PYCHARM || oldType == newEngine || oldType != previousTypeEngine) return
    val turnOff = MessageDialogBuilder.yesNo(
      TypeEngineFrontendBundle.message("type.engine.disable.tool.title"),
      TypeEngineFrontendBundle.message("type.engine.disable.tool.message", frontendFor(oldType).presentableName),
    ).ask(project)
    val current = preview.pendingDisable.get()
    preview.pendingDisable.set(if (turnOff) current + oldType.packageName else current - oldType.packageName)
  }

  override fun Panel.createContent() {
    if (availableOptions.size == 1) {
      row {
        icon(AllIcons.General.Information).commentRight(TypeEngineFrontendBundle.message("comment.multimodule.not.warning"))
      }
      // The engine is single-module only, but Pyrefly/ty can still be used as an LSP tool in
      // multi-module (workspace) projects — point the user there.
      row {
        link(TypeEngineFrontendBundle.message("comment.multimodule.use.tool.link")) {
          ShowSettingsUtil.getInstance().showSettingsDialog(project, PyExternalToolsConfigurable::class.java)
        }
      }

      return
    }

    row(TypeEngineFrontendBundle.message("engine.label")) {
      // Keep the settings binding (it drives apply/reset/isModified). `whenItemSelected` keeps the
      // sub-panel visibility model in sync for both user and programmatic changes; `whenItemSelectedFromUi`
      // publishes only *user* selections to the shared bridge, so the bridge→button reflection below
      // (a programmatic `selectedItem` set) can't feed back into a loop.
      // Driven by the shared staged bridge (not bound to settings) so it can reflect a staged engine
      // that differs from the persisted one — e.g. after the External Tools page deselects it. User
      // picks go through `whenItemSelectedFromUi`; the bridge→button reflection sets `selectedItem`
      // (which fires `whenItemSelected` for visibility, but not `…FromUi`, so there is no loop).
      engineButton = segmentedButton(availableOptions.map { it.id }) { text = frontendFor(it).presentableName }
        .whenItemSelected { selectedTypeEngine.set(it) }
        .whenItemSelectedFromUi { onEngineSelectedByUser(it) }
    }

    stagedEngine.afterChange(previewObserverDisposable) { pkg ->
      val type = engineTypeFor(pkg)
      if (engineButton.selectedItem != type) engineButton.selectedItem = type
      selectedTypeEngine.set(type)
    }

    PyTypeEngineFrontend.getSupported(project).forEach { provider ->
      provider.apply {
        val isVisible = selectedTypeEngine.transform { it == provider.id }
        createConfigurableContent(project, propertyGraph).visibleIf(isVisible)
      }
    }

    onReset {
      // Seed the bridge from the persisted engine only when nothing is staged yet, so a staged change
      // made on the External Tools page (deselecting the engine) survives when this page opens.
      if (stagedEngine.get() == null) {
        stagedEngine.set(previousTypeEngine.packageName)
        PyToolTypeEnginePreview.getInstance(project).pendingDisable.set(emptySet())
      }
      val type = engineTypeFor(stagedEngine.get())
      engineButton.selectedItem = type
      selectedTypeEngine.set(type)
    }
    onIsModified { engineTypeFor(stagedEngine.get()) != previousTypeEngine }
    onApply {
      val newEngine = engineTypeFor(stagedEngine.get())
      val preview = PyToolTypeEnginePreview.getInstance(project)
      val state = runWithModalProgressBlocking(project, TypeEngineFrontendBundle.message("type.engine.apply.progress")) {
        PyTypeEngineApi.getInstance().select(
          PyTypeEngineSelectionRequest(project.projectId(), newEngine, preview.pendingDisable.get())
        )
      }
      PyTypeEngineFrontendState.getInstance(project).apply(state)
      previousTypeEngine = state.selected
      preview.pendingDisable.set(emptySet())
    }
  }
}
