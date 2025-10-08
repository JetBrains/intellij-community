// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.classic

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalTabCloseListener
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

internal class ClassicTerminalTabCloseListener private constructor(
  content: Content,
  project: Project,
  parentDisposable: Disposable,
) : TerminalTabCloseListener(content, project, parentDisposable) {
  override fun hasChildProcesses(content: Content): Boolean {
    val widget = TerminalToolWindowManager.findWidgetByContent(content) ?: return false
    return widget.isCommandRunning()
  }

  companion object {
    @JvmStatic
    fun install(content: Content, project: Project, parentDisposable: Disposable) {
      ClassicTerminalTabCloseListener(content, project, parentDisposable)
    }
  }
}