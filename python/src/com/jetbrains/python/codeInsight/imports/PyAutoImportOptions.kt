// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.imports

import com.intellij.application.options.editor.AutoImportOptionsProvider
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.jetbrains.python.PyBundle
import com.jetbrains.python.codeInsight.PyCodeInsightSettings

class PyAutoImportOptions : UiDslUnnamedConfigurable.Simple(), AutoImportOptionsProvider {

  override fun Panel.createContent() {
    val settings = PyCodeInsightSettings.getInstance()

    group(PyBundle.message("form.auto.import.python")) {
      row {
        checkBox(PyBundle.message("form.auto.import.auto.import.show.popup"))
          .bindSelected(settings::SHOW_IMPORT_POPUP)
      }

      buttonsGroup(PyBundle.message("form.auto.import.preferred.import.style")) {
        row {
          radioButton(PyBundle.message("form.auto.import.from.module.import.name"), true)
        }
        row {
          radioButton(PyBundle.message("form.auto.import.import.module.name"), false)
        }
      }.bind(settings::PREFER_FROM_IMPORT)
    }
  }
}