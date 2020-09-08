package com.intellij.space.auth

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.settings.SpaceSettingsPanel
import com.intellij.space.utils.notify

internal object SpaceAuthNotifier {
  fun notifyDisconnected() {
    val configureServerAction = object : AnAction(SpaceBundle.message("auth.notification.disconnected.link.text")) {
      override fun actionPerformed(e: AnActionEvent) {
        SpaceSettingsPanel.openSettings(null)
      }
    }
    notify(SpaceBundle.message("auth.notification.disconnected.message"), listOf(configureServerAction))
  }

  fun authCheckFailedNotification() {
    notify(SpaceBundle.message("auth.notification.not.authenticated.message"))
  }
}