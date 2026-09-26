// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.history

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptModel

internal class CommandSearchPresenter(
  private val project: Project,
  private val editor: Editor,
  private val promptModel: TerminalPromptModel
) {
  fun showCommandSearch(history: List<String>) {
    val command = promptModel.commandText
    // Reverse the history to move the most recent values to the top.
    // It will be reversed again internally to show them at the bottom.
    val lookup = CommandHistoryUtil.createLookup(project, editor, command, history.asReversed())
    lookup.putUserData(IS_COMMAND_SEARCH_LOOKUP_KEY, true)

    lookup.addLookupListener(object : LookupListener {
      override fun lookupCanceled(event: LookupEvent) {
        project.messageBus.syncPublisher(CommandSearchListener.TOPIC).commandSearchAborted(promptModel)
      }
    })

    if (lookup.showLookup()) {
      lookup.ensureSelectionVisible(false)

      project.messageBus.syncPublisher(CommandSearchListener.TOPIC).commandSearchShown(promptModel)
    }
  }

  companion object {
    private val IS_COMMAND_SEARCH_LOOKUP_KEY: Key<Boolean> = Key.create("isCommandSearchLookup")

    val Lookup.isTerminalCommandSearch: Boolean
      get() = (this as? UserDataHolder)?.getUserData(IS_COMMAND_SEARCH_LOOKUP_KEY) == true
  }

}
