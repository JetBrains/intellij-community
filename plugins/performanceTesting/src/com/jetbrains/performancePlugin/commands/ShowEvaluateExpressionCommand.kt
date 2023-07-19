package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import com.intellij.xdebugger.impl.evaluate.XDebuggerEvaluationDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShowEvaluateExpressionCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "showEvaluateExpression"
    var currentDialog: XDebuggerEvaluationDialog? = null
  }

  override suspend fun doExecute(context: PlaybackContext) {
    withContext(Dispatchers.EDT) {
      val debugSessions = XDebuggerManager.getInstance(context.project).debugSessions
      if (debugSessions.isEmpty()) {
        throw IllegalStateException("Debug process was not started")
      }
      if (debugSessions.size > 1) {
        throw IllegalStateException("Currently running ${debugSessions.size} debug processes")
      }
      val debugSession = debugSessions.first()
      val editorsProvider = debugSession.getDebugProcess().getEditorsProvider()
      currentDialog = XDebuggerEvaluationDialog(debugSession, editorsProvider, XExpressionImpl("", null, null),
                                                debugSession.currentPosition, false)
      currentDialog!!.show()
    }
  }
}