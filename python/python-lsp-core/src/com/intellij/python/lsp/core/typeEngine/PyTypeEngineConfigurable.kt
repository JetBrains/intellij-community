// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core.typeEngine

import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.python.lsp.core.PyLspCoreBundle
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.toMutableProperty
import com.intellij.ui.dsl.builder.toNullableProperty
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

  init {
    val initialOption = settings.typeEngine
    selectedTypeEngine.set(initialOption)
  }

  override fun getId(): String = "pycharm.type.engine"
  override fun getDisplayName(): String = PyLspCoreBundle.message("display.name")
  override fun getDisplayNameFast(): String = PyLspCoreBundle.message("display.name")
  override fun getHelpTopic(): String = "reference.settings.python.type.engine"

  override fun Panel.createContent() {
    val isSingleModule = project.modules.size == 1
    if (!isSingleModule) {
      row {
        icon(AllIcons.General.Information).commentRight(PyLspCoreBundle.message("comment.multimodule.not.warning"))
      }

      return
    }

    row(PyLspCoreBundle.message("engine.label")) {
      segmentedButton(availableOptions) { text = it.displayName }
        .bind(settings::typeEngine.toMutableProperty().toNullableProperty())
        .whenItemSelected { selectedTypeEngine.set(it) }
    }

    PyTypeEngineProvider.EP.extensionsIfPointIsRegistered.filter { it.isSupported(project) }.forEach { provider ->
      provider.apply {
        val isVisible = selectedTypeEngine.transform { it == provider.pyTypeEngineType }
        createConfigurableContent(project, propertyGraph).visibleIf(isVisible)
      }
    }

    onApply {
      val newEngine = settings.typeEngine
      if (newEngine != previousTypeEngine) {
        PyTypeEngineUsageCollector.logEngineChanged(project, newEngine)
        previousTypeEngine = newEngine
      }
      // Invalidate TypeEvalContext cache so that editor errors reflect the new type engine settings
      PyTypeEngineSettingsModificationTracker.getInstance(project).incModificationCount()
    }
  }
}