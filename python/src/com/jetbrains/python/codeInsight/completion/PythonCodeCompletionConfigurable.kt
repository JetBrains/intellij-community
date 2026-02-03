package com.jetbrains.python.codeInsight.completion

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.jetbrains.python.PyBundle
import com.jetbrains.python.codeInsight.PyCodeInsightSettings

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class PythonCodeCompletionConfigurable: UiDslUnnamedConfigurable.Simple(), Configurable {

  override fun getDisplayName(): String {
    return PyBundle.message("configurable.PythonCodeCompletionConfigurable.display.name.python")
  }

  override fun Panel.createContent() {
    val settings = PyCodeInsightSettings.getInstance()

    group(PyBundle.message("configurable.PythonCodeCompletionConfigurable.border.title")) {
      row {
        checkBox(PyBundle.message("configurable.PythonCodeCompletionConfigurable.checkbox.suggest.importable.names"))
          .bindSelected(settings::INCLUDE_IMPORTABLE_NAMES_IN_BASIC_COMPLETION)
          .contextHelp(PyBundle.message("configurable.PythonCodeCompletionConfigurable.checkbox.suggest.importable.names.help"))
      }
    }
  }
}