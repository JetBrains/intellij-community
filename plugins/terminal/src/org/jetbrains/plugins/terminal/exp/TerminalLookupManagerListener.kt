// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.DataManager
import com.intellij.openapi.application.invokeLater
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.promptController

class TerminalLookupManagerListener : LookupManagerListener {
  override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
    if (newLookup?.editor?.isPromptEditor != true) {
      return
    }
    newLookup.addLookupListener(TerminalCompletionLookupListener())
  }

  /**
   * Checks for a full match of the user input text with the inserted completion item.
   * If there is a full match (a user typed exactly the same string that was selected in a lookup)
   * and then pressed Enter - we interpret it as an intention to run the command.
   */
  private class TerminalCompletionLookupListener : LookupListener {
    override fun itemSelected(event: LookupEvent) {
      val lookup = event.lookup as? LookupImpl
      val chosenItem = event.item
      if (lookup == null
          || lookup.getUserData(CommandHistoryPresenter.IS_COMMAND_HISTORY_LOOKUP_KEY) == true
          || event.completionChar != '\n'
          || chosenItem == null) {
        return
      }
      val typedString = lookup.itemPattern(chosenItem)
      if (typedString == chosenItem.lookupString) {
        executeCommand(lookup)
      }
    }

    private fun executeCommand(lookup: Lookup) {
      val dataContext = DataManager.getInstance().getDataContext(lookup.editor.component)
      val promptController = dataContext.promptController ?: return
      invokeLater {
        promptController.handleEnterPressed()
      }
    }
  }
}