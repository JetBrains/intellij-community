// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.auth

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.messages.SpaceBundleExtensions
import com.intellij.space.settings.SpaceSettingsPanel
import com.intellij.space.utils.notify

internal object SpaceAuthNotifier {
  fun authFailed() {
    val tryAgainAction = object : DumbAwareAction(SpaceBundleExtensions.messagePointer("auth.notification.failed.login.again.action")) {
      override fun actionPerformed(e: AnActionEvent) {
        SpaceSettingsPanel.openSettings(null)
      }
    }
    notify(SpaceBundle.message("auth.notification.failed.message"), listOf(tryAgainAction))
  }
}