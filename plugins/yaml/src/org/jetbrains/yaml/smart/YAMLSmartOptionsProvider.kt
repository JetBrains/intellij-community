// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.smart

import com.intellij.openapi.options.BeanConfigurable
import org.jetbrains.yaml.YAMLBundle

class YAMLSmartOptionsProvider : BeanConfigurable<YAMLEditorOptions>(
  YAMLEditorOptions.getInstance(), YAMLBundle.message("yaml.smartkeys.option.title")) {

  init {
    checkBox(YAMLBundle.message("yaml.smartkeys.option.paste"), instance::isUseSmartPaste, instance::setUseSmartPaste)
  }
}
