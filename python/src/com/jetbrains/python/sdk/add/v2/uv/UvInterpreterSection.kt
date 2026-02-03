// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.uv

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.and
import com.intellij.openapi.observable.util.isNotNull
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode
import com.jetbrains.python.sdk.add.v2.PythonMutableTargetAddInterpreterModel
import com.jetbrains.python.sdk.add.v2.PythonNewEnvironmentDialogNavigator.Companion.FAV_MODE
import com.jetbrains.python.sdk.add.v2.booleanProperty
import com.jetbrains.python.sdk.uv.impl.hasUvExecutable
import kotlinx.coroutines.CoroutineScope

/**
 * Encapsulates all UV-specific UI and lifecycle wiring for the interpreter dialog.
 */
internal class UvInterpreterSection(
  private val model: PythonMutableTargetAddInterpreterModel<PathHolder.Eel>,
  module: Module?,
  private val selectedMode: ObservableMutableProperty<PythonInterpreterSelectionMode>,
  propertyGraph: PropertyGraph,
) {
  private val uvCreator: EnvironmentCreatorUv<PathHolder.Eel> = model.uvCreator(module)

  private val _uv = propertyGraph.booleanProperty(selectedMode, PythonInterpreterSelectionMode.PROJECT_UV)

  fun setupUI(outerPanel: Panel, validationRequestor: DialogValidationRequestor) {
    outerPanel.rowsRange {
      uvCreator.setupUI(this, validationRequestor)
    }.visibleIf(_uv)
  }

  suspend fun onShown(scope: CoroutineScope) {
    selectUvIfExists()
    uvCreator.onShown(scope)
  }

  fun hintVisiblePredicate() = _uv and model.uvViewModel.uvExecutable.isNotNull()

  fun getUvCreator() = uvCreator

  private suspend fun selectUvIfExists() {
    if (PropertiesComponent.getInstance().getValue(FAV_MODE) != null) return
    if (hasUvExecutable() && selectedMode.get() != PythonInterpreterSelectionMode.PROJECT_UV) {
      selectedMode.set(PythonInterpreterSelectionMode.PROJECT_UV)
    }
  }
}
