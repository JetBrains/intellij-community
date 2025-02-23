package com.intellij.terminal.frontend

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.terminal.ui.TerminalWidget
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.TerminalWidgetProvider

internal class ReworkedTerminalWidgetProvider : TerminalWidgetProvider {
  override fun createTerminalWidget(project: Project, parentDisposable: Disposable): TerminalWidget {
    return ReworkedTerminalWidget(project, JBTerminalSystemSettingsProvider(), parentDisposable)
  }
}