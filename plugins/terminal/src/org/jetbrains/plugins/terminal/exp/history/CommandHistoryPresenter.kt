// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.history

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import org.jetbrains.plugins.terminal.exp.TerminalCommandExecutor
import org.jetbrains.plugins.terminal.exp.getDisposed
import org.jetbrains.plugins.terminal.exp.invokeLater

internal class CommandHistoryPresenter(private val project: Project,
                                       private val editor: Editor,
                                       private val commandExecutor: TerminalCommandExecutor) {
  private var initialCommand: String? = null

  fun showCommandHistory(history: List<String>) {
    val command = editor.document.text
    initialCommand = command
    // Reverse the history to move the most recent values to the top.
    // It will be reversed again internally to show them at the bottom.
    val lookup = CommandHistoryUtil.createLookup(project, editor, command.trim(), history.asReversed())
    lookup.putUserData(IS_COMMAND_HISTORY_LOOKUP_KEY, true)

    lookup.addLookupListener(object : LookupListener {
      override fun currentItemChanged(event: LookupEvent) {
        val selectedCommand = event.item?.lookupString ?: return
        invokeLater {
          if (!lookup.isLookupDisposed) {
            runWriteAction {
              lookup.performGuardedChange {
                editor.document.setText(selectedCommand)
                editor.caretModel.moveToOffset(selectedCommand.length)
              }
            }
          }
        }
      }

      override fun beforeItemSelected(event: LookupEvent): Boolean {
        // prevent item inserting because it was already inserted in a result of 'currentItemChanged'
        return false
      }

      override fun itemSelected(event: LookupEvent) {
        initialCommand = null
        if (event.completionChar == '\n') {
          commandExecutor.startCommandExecution(editor.document.text)
        }
      }

      override fun lookupCanceled(event: LookupEvent) {
        initialCommand = null
      }
    })

    if (lookup.showLookup()) {
      lookup.ensureSelectionVisible(false)
    }
    else thisLogger().error("Failed to show command history")
  }

  /**
   * Should be invoked when user intentionally close the history popup.
   * For example, when Escape is pressed.
   */
  fun onCommandHistoryClosed() {
    val commandToRestore = initialCommand
    if (commandToRestore != null) {
      initialCommand = null
      invokeLater(editor.getDisposed()) {
        runWriteAction {
          editor.document.setText(commandToRestore)
          editor.caretModel.moveToOffset(commandToRestore.length)
        }
      }
    }
  }

  companion object {
    private val IS_COMMAND_HISTORY_LOOKUP_KEY: Key<Boolean> = Key.create("isCommandHistoryLookup")

    val Lookup.isTerminalCommandHistory: Boolean
      get() = (this as? UserDataHolder)?.getUserData(IS_COMMAND_HISTORY_LOOKUP_KEY) == true
  }
}