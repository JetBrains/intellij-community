package com.intellij.terminal.frontend

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.terminal.ui.TerminalWidget
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.TerminalWidgetProvider
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo

internal class ReworkedTerminalWidgetProvider : TerminalWidgetProvider {
  override fun createTerminalWidget(project: Project, startupFusInfo: TerminalStartupFusInfo?, parentDisposable: Disposable): TerminalWidget {
    return ReworkedTerminalWidget(project, JBTerminalSystemSettingsProvider(), startupFusInfo, parentDisposable)
  }
}