// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.formatter.settings.TableStyle
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings

internal fun withTableStyle(project: Project, style: TableStyle, block: () -> Unit) {
  val settings = CodeStyle.getSettings(project).getCustomSettings(MarkdownCustomCodeStyleSettings::class.java)
  val oldStyle = settings.tableStyle
  settings.tableStyle = style
  try {
    block()
  }
  finally {
    settings.tableStyle = oldStyle
  }
}

internal fun setupTableReformatting(disposable: Disposable) {
  val settings = MarkdownCodeInsightSettings.getInstance()
  val old = settings.state.reformatTablesOnType
  settings.state.reformatTablesOnType = true
  Disposer.register(disposable) { settings.state.reformatTablesOnType = old }
}
