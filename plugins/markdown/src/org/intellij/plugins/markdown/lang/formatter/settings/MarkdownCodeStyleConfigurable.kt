// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter.settings

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.intellij.plugins.markdown.MarkdownBundle

internal class MarkdownCodeStyleConfigurable(settings: CodeStyleSettings, originalSettings: CodeStyleSettings)
  : CodeStyleAbstractConfigurable(settings, originalSettings, MarkdownBundle.message("markdown.settings.name")) {

  override fun createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel {
    return MarkdownCodeStyleSettingsPanel(currentSettings, settings)
  }
}