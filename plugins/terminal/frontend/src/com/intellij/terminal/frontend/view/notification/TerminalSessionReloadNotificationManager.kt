// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.notification

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.toolwindow.impl.createTerminalTab
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalEnvironmentChanged
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

/**
 * Provides a pre-made notification that suggests reloading terminal sessions.
 * Main use case is when integrations are enabled/disabled in their respective settings (e.g. [JdkCustomizer])
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class TerminalSessionReloadNotificationManager(private val project: Project) {
  companion object {
    fun getInstance(project: Project): TerminalSessionReloadNotificationManager =
      project.service<TerminalSessionReloadNotificationManager>()
  }
  
  private val notificationManager: SingletonNotificationManager by lazy {
    SingletonNotificationManager("terminal",
                                 NotificationType.INFORMATION)
  }

  /**
   * Shows a notification suggesting a user 
   * to create a new terminal tab
   */
  fun show(environmentChange: TerminalEnvironmentChanged.EnvironmentChange) {
    if (!hasTerminalSession(project)) return

    val reloadAction = NotificationAction.createSimpleExpiring(TerminalBundle.message("notification.environment.changed.action")) {
      createTerminalTab(project) 
    }
    val doNotAddAction = NotificationAction.createExpiring(EditorBundle.message("notification.dont.show.again.message")) { _, notification ->
      notification.setDoNotAskFor(project)
    }

    notificationManager
      .notify(
        TerminalBundle.message("notification.environment.changed.title"),
        TerminalBundle.message("notification.environment.changed.description", environmentChange.environment),
        project
      ) { notification -> 
        notification
          .addAction(reloadAction)
          .addAction(doNotAddAction)
          .setIcon(AllIcons.Toolwindows.SettingSync)
          .setSuggestionType(true)
      }
  }
  
  /** @return Whether we have at least one terminal window */
  private fun hasTerminalSession(project: Project): Boolean {
    if (project.isDisposed || !project.isInitialized || !project.isOpen) return false
    val manager = TerminalToolWindowManager.getInstance(project)
    val toolWindow = manager.toolWindow ?: return false
    
    return toolWindow.contentManager.contentCount > 0
  }
}
