// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.target

import com.intellij.execution.target.CustomToolLanguageConfigurable
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.layout.*
import com.jetbrains.python.PyBundle
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.sdk.add.target.PyAddTargetBasedSdkPanel
import java.util.function.Supplier

class PythonLanguageRuntimeUI(project: Project,
                              config: PythonLanguageRuntimeConfiguration,
                              targetSupplier: Supplier<TargetEnvironmentConfiguration>)
  : BoundConfigurable(PyBundle.message("configurable.name.python.language")), CustomToolLanguageConfigurable<Sdk> {

  private val panel: PyAddTargetBasedSdkPanel

  init {
    val existingSdks = PyConfigurableInterpreterList.getInstance(project).model.sdks.asList()

    panel = PyAddTargetBasedSdkPanel(project = project, module = null, existingSdks = existingSdks, targetSupplier = targetSupplier,
                                     config = config)
  }

  override fun createPanel(): DialogPanel =
    panel {
      row { panel.createCenterPanel()() }
    }

  override fun apply() {
    // `apply` all callbacks
    super.apply()
    // we do not create Python SDK here, we return the configuration to the caller and let him do all the work
  }

  override fun createCustomTool(savedConfiguration: TargetEnvironmentConfiguration): Sdk? {
    return panel.getOrCreateSdk(savedConfiguration)
  }
  override fun validate(): Collection<ValidationInfo> = panel.doValidateAll()
}