// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.smart

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.yaml.YAMLBundle

class YAMLSmartOptionsProvider : BoundConfigurable(YAMLBundle.message("yaml.smartkeys.option.title"),
                                                   "reference.settings.editor.smart.keys.yaml") {

  override fun createPanel(): DialogPanel {
    val settings = YAMLEditorOptions.getInstance()

    return panel {
      row {
        checkBox(YAMLBundle.message("yaml.smartkeys.option.paste"))
          .bindSelected(settings::isUseSmartPaste, settings::setUseSmartPaste)
      }
    }
  }
}
