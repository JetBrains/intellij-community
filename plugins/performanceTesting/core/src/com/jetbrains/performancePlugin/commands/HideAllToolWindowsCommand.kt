// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HideAllToolWindowsCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "hideAllToolWindows"
    private val LOG = logger<HideAllToolWindowsCommand>()
  }

  override suspend fun doExecute(context: PlaybackContext) {
    withContext(Dispatchers.EDT) {
      val dataContext = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, context.project)
        .build()

      val action = ActionManager.getInstance().getAction("HideAllWindows")
      if (action == null) {
        LOG.error("HideAllWindows action not found")
        return@withContext
      }

      val event = AnActionEvent.createEvent(dataContext, null, "", ActionUiKind.NONE, null)
      action.actionPerformed(event)
    }
  }
}
