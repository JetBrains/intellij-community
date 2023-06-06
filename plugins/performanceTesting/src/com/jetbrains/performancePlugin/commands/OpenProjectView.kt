package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise

class OpenProjectView(text: String, line: Int) : AbstractCommand(text, line) {
  override fun _execute(context: PlaybackContext): Promise<Any> {
    val actionCallback: ActionCallback = ActionCallbackProfilerStopper()
    ApplicationManager.getApplication().invokeLater {
      val windowManager = getInstance(context.project)
      val window = windowManager.getToolWindow(ToolWindowId.PROJECT_VIEW)
      if (window != null) {
        if (!window.isActive &&
            (windowManager.isEditorComponentActive || ToolWindowId.PROJECT_VIEW != windowManager.activeToolWindowId)) {
          window.activate(null)
          LOG.warn("Project View is opened")
        }
        else {
          LOG.warn("Project View has been opened already")
        }
        actionCallback.setDone()
      }
      else {
        actionCallback.reject("Window is not found")
      }
    }
    return actionCallback.toPromise()
  }

  companion object {
    const val PREFIX = CMD_PREFIX + "openProjectView"
    private val LOG = Logger.getInstance(OpenProjectView::class.java)
  }
}