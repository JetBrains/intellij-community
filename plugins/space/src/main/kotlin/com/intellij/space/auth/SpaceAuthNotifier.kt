package circlet.auth

import circlet.settings.CircletSettingsPanel
import circlet.utils.notify

internal object SpaceAuthNotifier {
  fun notifyDisconnected() {
    notify("Disconnected.<br><a href=\"switch-on\">Configure Server</a>", ::configure)
  }

  fun notifyConnected() {
    notify("Connected")
  }

  fun authCheckFailedNotification() {
    notify("Not authenticated.<br> <a href=\"sign-in\">Sign in</a>") {
    }
  }

  private fun configure() {
    CircletSettingsPanel.openSettings(null)
  }
}