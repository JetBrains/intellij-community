package com.jetbrains.performancePlugin.commands

import com.intellij.ide.actions.cache.ProjectRecoveryScope
import com.intellij.ide.actions.cache.RecoveryAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.util.indexing.RefreshIndexableFilesAction
import com.intellij.util.indexing.ReindexAction
import com.intellij.util.indexing.RescanIndexesAction
import com.intellij.workspaceModel.ide.impl.WorkspaceModelRecoveryAction
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise


private val LOG = Logger.getInstance(RecoveryActionCommand::class.java)

class RecoveryActionCommand(text: String, line: Int) : AbstractCommand(text, line) {

  companion object {
    const val PREFIX = CMD_PREFIX + "recovery"
    private val ALLOWED_ACTIONS = listOf("REFRESH", "RESCAN", "REINDEX")
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback = ActionCallbackProfilerStopper()
    val args = text.split(" ".toRegex(), 2).toTypedArray()
    val project = context.project
    val recoveryAction: RecoveryAction = when (args[1]) {
      "REFRESH" -> RefreshIndexableFilesAction()
      "RESCAN" -> RescanIndexesAction()
      "REINDEX" -> ReindexAction()
      "REOPEN" -> WorkspaceModelRecoveryAction()
      else -> error("The argument ${args[1]} to the command is incorrect. Allowed actions: $ALLOWED_ACTIONS")
    }
    recoveryAction.perform(ProjectRecoveryScope(project)).handle { res, err ->
      if (err != null) {
        LOG.error(err)
        return@handle
      }

      if (res.problems.isNotEmpty()) {
        LOG.error("${recoveryAction.actionKey} found and fixed ${res.problems.size} problems, samples: " +
                  res.problems.take(10).joinToString(", ") { it.message })
      }
      LOG.info("Command $PREFIX ${args[1]} finished")
      actionCallback.setDone()
    }
    return actionCallback.toPromise()
  }
}
