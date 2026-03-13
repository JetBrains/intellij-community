// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.wm.IdeFocusManager
import io.opentelemetry.context.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Command to execute find usages in the tool window (not in the popup). This command does not take any arguments, it is assumed that the
 * caret has been moved to the appropriate location in the editor beforehand. Additionally, the command does not wait for find usages to
 * complete; that job is left to [FindUsagesInToolWindowWaitCommand].
 */
class FindUsagesInToolWindowCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME: String = "findUsagesInToolWindow"
    const val PREFIX: String = CMD_PREFIX + NAME

    const val SPAN_NAME: String = NAME
    const val FIRST_USAGE_SPAN_NAME: String = "${NAME}_firstUsage"
    const val TOOL_WINDOW_SPAN_NAME: String = "${NAME}_toolWindow"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    AdvancedSettings.setInt("ide.find.result.count.warning.limit", Integer.MAX_VALUE)

    val currentOTContext = Context.current()
    withContext(Dispatchers.EDT) {
      currentOTContext.makeCurrent().use {
        val focusedComponent = IdeFocusManager.findInstance().focusOwner
        val dataContext = DataManager.getInstance().getDataContext(focusedComponent)
        val findUsagesAction = ApplicationManager.getApplication().serviceAsync<ActionManager>().getAction(IdeActions.ACTION_FIND_USAGES)
        val findUsagesActionEvent = AnActionEvent.createFromAnAction(findUsagesAction, null, ActionPlaces.KEYBOARD_SHORTCUT, dataContext)

        findUsagesAction.actionPerformed(findUsagesActionEvent)
      }
    }
  }

  override fun getName(): String = PREFIX
}
