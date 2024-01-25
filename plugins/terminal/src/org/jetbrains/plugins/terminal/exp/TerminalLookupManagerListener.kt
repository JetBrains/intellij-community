// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.lookup.*
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.DataManager
import com.intellij.openapi.application.invokeLater
import com.intellij.terminal.TerminalUiSettingsManager
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.promptController
import org.jetbrains.plugins.terminal.exp.documentation.TerminalDocumentationManager
import org.jetbrains.plugins.terminal.exp.history.CommandHistoryPresenter
import kotlin.math.max
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class TerminalLookupManagerListener : LookupManagerListener {
  override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
    if (newLookup?.editor?.isPromptEditor != true) {
      return
    }
    val lookup = newLookup as? LookupImpl ?: return
    lookup.presentation = LookupPresentation.Builder()
      .withPositionStrategy(LookupPositionStrategy.ONLY_ABOVE)
      .withMostRelevantOnTop(false)
      .withMaxVisibleItemsCount(object : ReadWriteProperty<LookupPresentation, Int> {
        override fun getValue(thisRef: LookupPresentation, property: KProperty<*>): Int {
          return TerminalUiSettingsManager.getInstance().maxVisibleCompletionItemsCount
        }

        override fun setValue(thisRef: LookupPresentation, property: KProperty<*>, value: Int) {
          TerminalUiSettingsManager.getInstance().maxVisibleCompletionItemsCount = max(5, value)
        }
      })
      .build()
    lookup.addLookupListener(TerminalCompletionLookupListener())
    TerminalDocumentationManager.getInstance(lookup.project).autoShowDocumentationOnItemChange(lookup, parentDisposable = lookup)
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
      if (typedString == chosenItem.lookupString
          // if typed string differs only by the absence of the trailing slash, execute the command as well
          || "$typedString/" == chosenItem.lookupString) {
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