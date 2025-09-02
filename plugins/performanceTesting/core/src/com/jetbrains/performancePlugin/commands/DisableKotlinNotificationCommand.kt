// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.ui.playback.PlaybackContext

private const val NOTIFICATION_PROPERTIES_KEY = "kotlin.code.style.migration.dialog.show.count"

class DisableKotlinNotificationCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME: String = "disableKotlinNotification"
    const val PREFIX: String = CMD_PREFIX + NAME
  }

  override suspend fun doExecute(context: PlaybackContext) {
    PropertiesComponent.getInstance().setValue(NOTIFICATION_PROPERTIES_KEY, Integer.MAX_VALUE, 0)
  }

  override fun getName(): String {
    return NAME
  }
}

