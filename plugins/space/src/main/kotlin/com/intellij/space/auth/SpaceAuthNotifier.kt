package com.intellij.space.auth

import com.intellij.space.messages.SpaceBundle
import com.intellij.space.settings.SpaceSettingsPanel
import com.intellij.space.utils.notify
import com.intellij.xml.util.XmlStringUtil.formatLink
import com.intellij.xml.util.XmlStringUtil.wrapInHtmlLines

internal object SpaceAuthNotifier {
  fun notifyDisconnected() {
    notify(
      wrapInHtmlLines(
        SpaceBundle.message("auth.notification.disconnected.message"),
        formatLink("switch-on", SpaceBundle.message("auth.notification.disconnected.link.text"))
      ),
      SpaceAuthNotifier::configure
    )
  }

  fun authCheckFailedNotification() {
    notify(SpaceBundle.message("auth.notification.not.authenticated.message"))
  }

  private fun configure() {
    SpaceSettingsPanel.openSettings(null)
  }
}