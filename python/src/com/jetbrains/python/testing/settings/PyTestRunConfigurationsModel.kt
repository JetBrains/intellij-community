// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.settings

import com.intellij.openapi.module.Module
import com.intellij.ui.CollectionComboBoxModel
import com.jetbrains.python.testing.PyAbstractTestFactory
import com.jetbrains.python.testing.PythonTestConfigurationType
import com.jetbrains.python.testing.TestRunnerService


internal class PyTestRunConfigurationsModel private constructor(private val module: Module?,
                                                                items: List<PyAbstractTestFactory<*>>,
                                                                selection: PyAbstractTestFactory<*>) :
  CollectionComboBoxModel<PyAbstractTestFactory<*>>(items.toMutableList(), selection) {
  companion object {
    fun create(module: Module?): PyTestRunConfigurationsModel =
      PyTestRunConfigurationsModel(module, PythonTestConfigurationType.getInstance().typedFactories.toTypedArray().toList(),
                                   TestRunnerService.getInstance(module).selectedFactory)
  }

  var testRunner: PyAbstractTestFactory<*> = selection
    private set

  fun reset() {
    selectedItem = testRunner
  }

  fun apply() {
    val item = selected ?: return
    testRunner = item
    TestRunnerService.getInstance(module).selectedFactory = testRunner
  }

}
