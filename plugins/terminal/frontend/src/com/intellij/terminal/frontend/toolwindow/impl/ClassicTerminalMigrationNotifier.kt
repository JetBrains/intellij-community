package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.openapi.application.UI
import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.toolwindow.TerminalTabsManagerListener
import com.intellij.terminal.frontend.view.TerminalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.ClassicTerminalMigration

internal class ClassicTerminalMigrationNotifier(private val project: Project) : TerminalTabsManagerListener {
  override fun terminalViewCreated(view: TerminalView) {
    if (!ClassicTerminalMigration.shouldShowEngineChangeNotification()) return

    view.coroutineScope.launch {
      awaitCommandExecutedAndIdle(view)

      withContext(Dispatchers.UI) {
        if (ClassicTerminalMigration.shouldShowEngineChangeNotification()) {
          showEngineChangeNotification(project)
          ClassicTerminalMigration.setEngineChangeNotificationShown()
        }
      }
    }
  }
}
