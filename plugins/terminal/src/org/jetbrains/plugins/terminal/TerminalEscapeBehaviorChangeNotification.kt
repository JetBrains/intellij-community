// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.util.RunOnceUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object TerminalEscapeBehaviorChangeNotification {
  @JvmStatic
  fun showNotificationIfNeeded(project: Project) {
    RunOnceUtil.runOnceForApp("terminal.escape.behavior.change.notification") {
      val moveFocusToEditorAction = ActionManager.getInstance().getAction("Terminal.SwitchFocusToEditor")
      if (!TerminalNewUserTracker.isNewUserForRelease() && !moveFocusToEditorAction.shortcutSet.hasShortcuts()) {
        doShowNotification(project)
      }
    }
  }

  private fun doShowNotification(project: Project) {
    val configureAction = NotificationAction.createSimple(TerminalBundle.message("escape.behavior.change.notification.action")) {
      ShowSettingsUtilImpl.showSettingsDialog(
        project,
        idToSelect = TERMINAL_CONFIGURABLE_ID,
        filter = TerminalBundle.message("settings.move.focus.to.editor.with"),
      )
    }

    NotificationGroupManager.getInstance()
      .getNotificationGroup("terminal")
      .createNotification(
        TerminalBundle.message("escape.behavior.change.notification.title"),
        TerminalBundle.message("escape.behavior.change.notification.content"),
        NotificationType.INFORMATION,
      )
      .addAction(configureAction)
      .notify(project)
  }
}