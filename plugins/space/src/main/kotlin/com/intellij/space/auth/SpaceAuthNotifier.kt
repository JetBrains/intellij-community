package com.intellij.space.auth

import com.intellij.space.settings.SpaceSettingsPanel
import com.intellij.space.utils.notify

internal object SpaceAuthNotifier {
  fun notifyDisconnected() {
    notify("Disconnected.<br><a href=\"switch-on\">Configure Server</a>", SpaceAuthNotifier::configure)
  }

  fun notifyConnected() {
    notify("Connected")
  }

  fun authCheckFailedNotification() {
    notify("Not authenticated.<br> <a href=\"sign-in\">Sign in</a>") {
    }
  }

  private fun configure() {
    SpaceSettingsPanel.openSettings(null)
  }
}