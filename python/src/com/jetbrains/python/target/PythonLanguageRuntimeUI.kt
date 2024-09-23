// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.target

import com.intellij.execution.target.CustomToolLanguageConfigurable
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.sdk.add.v1.PyAddTargetBasedSdkPanel
import java.util.function.Supplier

class PythonLanguageRuntimeUI(project: Project,
                              config: PythonLanguageRuntimeConfiguration,
                              targetSupplier: Supplier<TargetEnvironmentConfiguration>)
  : BoundConfigurable(PyBundle.message("configurable.name.python.language")), CustomToolLanguageConfigurable<Sdk> {
  private val existingSdks: List<Sdk> = PyConfigurableInterpreterList.getInstance(project).model.sdks.asList()

  private var introspectable: LanguageRuntimeType.Introspectable? = null

  private val panel: PyAddTargetBasedSdkPanel by lazy {
    PyAddTargetBasedSdkPanel(project = project, module = null, existingSdks = existingSdks, targetSupplier = targetSupplier,
                             config = config, introspectable = introspectable).apply {
                               disposable?.let { Disposer.register(it, this) }
    }
  }

  override fun createPanel(): DialogPanel =
    panel {
      row { cell(panel.createCenterPanel()).align(AlignX.FILL) }
    }

  override fun apply() {
    // `apply` all callbacks
    super.apply()
    // we do not create Python SDK here, we return the configuration to the caller and let him do all the work
  }

  override fun setIntrospectable(introspectable: LanguageRuntimeType.Introspectable) {
    this.introspectable = introspectable
  }

  override fun createCustomTool(): Sdk? {
    return panel.getOrCreateSdk()
  }

  override fun validate(): Collection<ValidationInfo> = panel.doValidateAll()
}