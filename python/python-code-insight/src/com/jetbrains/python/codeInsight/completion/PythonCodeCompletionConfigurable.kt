package com.jetbrains.python.codeInsight.completion

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.jetbrains.python.codeInsight.PyCodeInsightBundle
import com.jetbrains.python.codeInsight.PyCodeInsightSettings

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class PythonCodeCompletionConfigurable: UiDslUnnamedConfigurable.Simple(), Configurable {

  override fun getDisplayName(): String {
    return PyCodeInsightBundle.message("configurable.PythonCodeCompletionConfigurable.display.name.python")
  }

  override fun Panel.createContent() {
    val settings = PyCodeInsightSettings.getInstance()

    group(PyCodeInsightBundle.message("configurable.PythonCodeCompletionConfigurable.border.title")) {
      row {
        checkBox(PyCodeInsightBundle.message("configurable.PythonCodeCompletionConfigurable.checkbox.suggest.importable.names"))
          .bindSelected(settings::INCLUDE_IMPORTABLE_NAMES_IN_BASIC_COMPLETION)
          .contextHelp(PyCodeInsightBundle.message("configurable.PythonCodeCompletionConfigurable.checkbox.suggest.importable.names.help"))
      }
      row {
        checkBox(PyCodeInsightBundle.message("configurable.PythonCodeCompletionConfigurable.checkbox.copy.annotations.from.stubs"))
          .bindSelected(settings::COPY_TYPE_ANNOTATIONS_FROM_STUBS)
      }
    }
  }
}