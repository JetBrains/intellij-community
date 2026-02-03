// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.spellchecker.util.SpellCheckerBundle
import org.jetbrains.annotations.Nls

object SpellCheckingNotifier {
  private val group = NotificationGroupManager.getInstance().getNotificationGroup(SpellCheckerBundle.message("spellchecker"))

  fun showWarningNotificationBalloon(@Nls title: String, @Nls content: String) {
    val notification = group.createNotification(title, content, NotificationType.WARNING)
    notification.notify(null)
  }
}
