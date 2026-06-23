// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.jetbrains.python.PyBundle

class PythonTypeCheckingConfigurable : UiDslUnnamedConfigurable.Simple(), Configurable {

  override fun getDisplayName(): String {
    return PyBundle.message("configurable.PythonTypeCheckingConfigurable.display.name")
  }

  override fun Panel.createContent() {
    val settings = PyCodeInsightSettings.getInstance()
    row {
      checkBox(PyBundle.message("configurable.PythonTypeCheckingConfigurable.checkbox.type.ignore.strict"))
        .bindSelected(settings::TYPE_IGNORE_STRICT_CODE_COVERAGE)
        .contextHelp(PyBundle.message("configurable.PythonTypeCheckingConfigurable.checkbox.type.ignore.strict.help"))
    }
  }
}
