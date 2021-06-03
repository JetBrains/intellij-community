// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.intellij.openapi.module.Module
import com.intellij.ui.CollectionComboBoxModel
import org.jetbrains.annotations.Nls


internal class PyTestRunConfigurationsModel private constructor(private val module: Module?,
                                                                items: List<@Nls String>,
                                                                @Nls selection: String) :
  CollectionComboBoxModel<String>(items, selection) {
  companion object {
    fun create(module: Module?): PyTestRunConfigurationsModel =
      PyTestRunConfigurationsModel(module, pythonFactories.map { it.name }, TestRunnerService.getInstance(module).selectedFactory.name)
  }

  @Nls
  var testRunnerName: String = selection
    private set

  fun reset() {
    selectedItem = testRunnerName
  }

  fun apply() {
    val item = selected ?: return
    testRunnerName = item
    TestRunnerService.getInstance(module).selectedFactory = pythonFactories.find { it.name == testRunnerName }!!
  }

}