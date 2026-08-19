// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core.typeEngine

import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.Disposer
import com.intellij.python.lsp.core.PyLspCoreBundle
import com.intellij.python.pytools.ui.PyToolTypeEnginePreview
import com.intellij.python.pytools.ui.configuration.PyExternalToolsConfigurable
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.SegmentedButton
import com.jetbrains.python.psi.types.PyTypeEngineSettingsModificationTracker

class PyTypeEngineConfigurable(
  private val project: Project,
) : UiDslUnnamedConfigurable.Simple(), SearchableConfigurable {
  private val settings: PyTypeEngineProjectSettings
    get() = project.service<PyTypeEngineProjectSettings>()

  private val propertyGraph = PropertyGraph()
  private val selectedTypeEngine = propertyGraph.property(PyTypeEngineType.PYCHARM)

  private val availableOptions: List<PyTypeEngineType>
    get() = PyTypeEngineProvider.getSupportedTypes(project)

  private var previousTypeEngine: PyTypeEngineType = settings.typeEngine

  /** The shared, transient staged-engine bridge the External Tools page also reads/writes. */
  private val stagedEngine get() = PyToolTypeEnginePreview.getInstance(project).stagedEnginePackage

  /** Scopes the bridge→button observer to this page's lifetime. */
  private val previewObserverDisposable = Disposer.newDisposable()

  /** The engine segmented button, captured so the bridge observer can flip it when the tool is turned off elsewhere. */
  private lateinit var engineButton: SegmentedButton<PyTypeEngineType>

  init {
    val initialOption = settings.typeEngine
    selectedTypeEngine.set(initialOption)
  }

  override fun getId(): String = "pycharm.type.engine"
  override fun getDisplayName(): String = PyLspCoreBundle.message("display.name")
  override fun getDisplayNameFast(): String = PyLspCoreBundle.message("display.name")
  override fun getHelpTopic(): String = "reference.settings.python.type.engine"

  override fun disposeUIResources() {
    Disposer.dispose(previewObserverDisposable)
    stagedEngine.set(null)
    PyToolTypeEnginePreview.getInstance(project).pendingDisable.set(emptySet())
    super<UiDslUnnamedConfigurable.Simple>.disposeUIResources()
  }

  private fun engineTypeFor(pkg: String?): PyTypeEngineType =
    availableOptions.firstOrNull { it.packageName == pkg } ?: PyTypeEngineType.PYCHARM

  /**
   * A user picked [newEngine] in the segmented button. Publish it to the shared bridge, and — when
   * switching **away** from a tool-backed engine — offer to turn that engine's External Tools tool off
   * too (it may be used for other purposes). The choice is staged in [PyToolTypeEnginePreview.pendingDisable]
   * and committed at Apply, so it works whether or not the External Tools page is open.
   */
  private fun onEngineSelectedByUser(newEngine: PyTypeEngineType) {
    val preview = PyToolTypeEnginePreview.getInstance(project)
    val oldType = engineTypeFor(stagedEngine.get())
    stagedEngine.set(newEngine.packageName)
    // Re-selecting an engine cancels any pending "disable" for its own tool.
    if (newEngine.packageName.isNotEmpty()) {
      preview.pendingDisable.set(preview.pendingDisable.get() - newEngine.packageName)
    }
    // Only prompt when leaving the *persisted* (actually active) engine. A merely staged engine that was
    // never applied has nothing enabled to turn off — its tool auto-reverts on the External Tools page.
    if (oldType == PyTypeEngineType.PYCHARM || oldType == newEngine || oldType != settings.typeEngine) return
    val turnOff = MessageDialogBuilder.yesNo(
      PyLspCoreBundle.message("type.engine.disable.tool.title"),
      PyLspCoreBundle.message("type.engine.disable.tool.message", oldType.displayName),
    ).ask(project)
    val current = preview.pendingDisable.get()
    preview.pendingDisable.set(if (turnOff) current + oldType.packageName else current - oldType.packageName)
  }

  override fun Panel.createContent() {
    val isSingleModule = project.modules.size == 1
    if (!isSingleModule) {
      row {
        icon(AllIcons.General.Information).commentRight(PyLspCoreBundle.message("comment.multimodule.not.warning"))
      }
      // The engine is single-module only, but Pyrefly/ty can still be used as an LSP tool in
      // multi-module (workspace) projects — point the user there.
      row {
        link(PyLspCoreBundle.message("comment.multimodule.use.tool.link")) {
          ShowSettingsUtil.getInstance().showSettingsDialog(project, PyExternalToolsConfigurable::class.java)
        }
      }

      return
    }

    row(PyLspCoreBundle.message("engine.label")) {
      // Keep the settings binding (it drives apply/reset/isModified). `whenItemSelected` keeps the
      // sub-panel visibility model in sync for both user and programmatic changes; `whenItemSelectedFromUi`
      // publishes only *user* selections to the shared bridge, so the bridge→button reflection below
      // (a programmatic `selectedItem` set) can't feed back into a loop.
      // Driven by the shared staged bridge (not bound to settings) so it can reflect a staged engine
      // that differs from the persisted one — e.g. after the External Tools page deselects it. User
      // picks go through `whenItemSelectedFromUi`; the bridge→button reflection sets `selectedItem`
      // (which fires `whenItemSelected` for visibility, but not `…FromUi`, so there is no loop).
      engineButton = segmentedButton(availableOptions) { text = it.displayName }
        .whenItemSelected { selectedTypeEngine.set(it) }
        .whenItemSelectedFromUi { onEngineSelectedByUser(it) }
    }

    stagedEngine.afterChange(previewObserverDisposable) { pkg ->
      val type = engineTypeFor(pkg)
      if (engineButton.selectedItem != type) engineButton.selectedItem = type
      selectedTypeEngine.set(type)
    }

    PyTypeEngineProvider.EP.extensionsIfPointIsRegistered.filter { it.isSupported(project) }.forEach { provider ->
      provider.apply {
        val isVisible = selectedTypeEngine.transform { it == provider.pyTypeEngineType }
        createConfigurableContent(project, propertyGraph).visibleIf(isVisible)
      }
    }

    onReset {
      // Seed the bridge from the persisted engine only when nothing is staged yet, so a staged change
      // made on the External Tools page (deselecting the engine) survives when this page opens.
      if (stagedEngine.get() == null) {
        stagedEngine.set(settings.typeEngine.packageName)
        PyToolTypeEnginePreview.getInstance(project).pendingDisable.set(emptySet())
      }
      val type = engineTypeFor(stagedEngine.get())
      engineButton.selectedItem = type
      selectedTypeEngine.set(type)
    }
    onIsModified { engineTypeFor(stagedEngine.get()) != settings.typeEngine }
    onApply {
      val newEngine = engineTypeFor(stagedEngine.get())
      settings.typeEngine = newEngine
      if (newEngine != previousTypeEngine) {
        PyTypeEngineUsageCollector.logEngineChanged(project, newEngine)
        previousTypeEngine = newEngine
      }
      // Invalidate TypeEvalContext cache so that editor errors reflect the new type engine settings
      PyTypeEngineSettingsModificationTracker.getInstance(project).incModificationCount()
    }
  }
}