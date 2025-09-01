package com.intellij.terminal.frontend.completion

import com.google.common.base.Ascii
import com.intellij.codeInsight.lookup.*
import com.intellij.codeInsight.lookup.impl.EmptyLookupItem
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.terminal.TerminalUiSettingsManager
import com.intellij.terminal.frontend.TerminalInput
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputModelEditor
import kotlin.math.max
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class TerminalLookupManagerListener : LookupManagerListener {
  override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
    if (newLookup == null || newLookup !is LookupEx || !newLookup.editor.isOutputModelEditor) {
      return
    }

    newLookup.presentation = LookupPresentation.Builder()
      .withMaxVisibleItemsCount(MaxVisibleItemsProperty())
      .build()
    newLookup.addLookupListener(TerminalLookupListener())
  }

  private class MaxVisibleItemsProperty : ReadWriteProperty<LookupPresentation, Int> {
    override fun getValue(thisRef: LookupPresentation, property: KProperty<*>): Int {
      return TerminalUiSettingsManager.getInstance().maxVisibleCompletionItemsCount
    }

    override fun setValue(thisRef: LookupPresentation, property: KProperty<*>, value: Int) {
      TerminalUiSettingsManager.getInstance().maxVisibleCompletionItemsCount = max(5, value)
    }
  }
}

class TerminalLookupListener : LookupListener {
  override fun beforeItemSelected(event: LookupEvent): Boolean {
    val terminalInput = event.lookup.editor.getUserData(TerminalInput.Companion.KEY) ?: return false
    val item = event.item
    val lookup = event.lookup as LookupImpl
    val completionChar = event.completionChar

    if (item == null || !item.isValid() || item is EmptyLookupItem) {
      return false
    }

    val commandSize = lookup.itemPattern(item).length
    if (commandSize > 0) {
      terminalInput.sendBytes(ByteArray(commandSize) { Ascii.BS })
    }
    terminalInput.sendString(item.lookupString)
    // if one of the listeners returns false - the item is not inserted
    return false
  }

  /**
   * Duplicated logic from the gen1 terminal (TerminalCompletionLookupListener).
   * Checks for a full match of the user input text with the inserted completion item.
   * If there is a full match (a user typed exactly the same string that was selected in a lookup)
   * and then pressed Enter - we interpret it as an intention to run the command.
   */
  override fun itemSelected(event: LookupEvent) {
    val lookup = event.lookup
    val chosenItem = event.item
    if (lookup == null
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
    val terminalInput = lookup.editor.getUserData(TerminalInput.Companion.KEY) ?: return
    terminalInput.sendEnter()
  }
}

