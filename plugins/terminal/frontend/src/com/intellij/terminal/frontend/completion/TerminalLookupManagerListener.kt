package com.intellij.terminal.frontend.completion

import com.google.common.base.Ascii
import com.intellij.codeInsight.lookup.*
import com.intellij.codeInsight.lookup.impl.EmptyLookupItem
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.PrefixChangeListener
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.TextRange
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.TerminalUiSettingsManager
import com.intellij.terminal.frontend.TerminalInput
import kotlinx.coroutines.cancel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputModelEditor
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import kotlin.math.max
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class TerminalLookupManagerListener : LookupManagerListener {
  override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
    if (newLookup == null || newLookup !is LookupImpl || !newLookup.editor.isOutputModelEditor) {
      return
    }

    newLookup.presentation = LookupPresentation.Builder()
      .withMaxVisibleItemsCount(MaxVisibleItemsProperty())
      .build()

    newLookup.addLookupListener(TerminalLookupListener())
    newLookup.addPrefixChangeListener(TerminalSelectedItemIconUpdater(newLookup), newLookup)
    installLookupPrefixUpdater(newLookup)

    val outputModel = newLookup.editor.getUserData(TerminalOutputModel.KEY) ?: error("Output model is not set in the terminal editor")
    outputModel.addListener(newLookup, TerminalLookupOutputModelListener(newLookup, outputModel))
  }

  private fun installLookupPrefixUpdater(lookup: LookupImpl) {
    val outputModel = lookup.editor.getUserData(TerminalOutputModel.KEY)
                      ?: error("Output model is not set in the terminal editor")
    val coroutineScope = terminalProjectScope(lookup.project).childScope("TerminalLookupPrefixUpdater")
    Disposer.register(lookup) { coroutineScope.cancel() }
    TerminalLookupPrefixUpdater.install(outputModel, lookup, coroutineScope)
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

private class TerminalLookupListener : LookupListener {
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
    if (canExecuteWithChosenItem(chosenItem.lookupString, typedString)) {
      executeCommand(lookup)
    }
  }

  private fun executeCommand(lookup: Lookup) {
    val terminalInput = lookup.editor.getUserData(TerminalInput.Companion.KEY) ?: return
    terminalInput.sendEnter()
  }
}

/**
 * Set's the [AllIcons.Actions.Execute] icon for the selected item in the lookup if it matches the user input.
 * To indicate that insertion of the item will cause immediate execution of the command.
 */
private class TerminalSelectedItemIconUpdater(private val lookup: Lookup) : PrefixChangeListener {
  private var curSelectedItem: LookupElement? = null

  override fun afterAppend(c: Char) {
    updateSelectedItemIcon()
  }

  override fun afterTruncate() {
    updateSelectedItemIcon()
  }

  private fun updateSelectedItemIcon() {
    val selectedItem = lookup.currentItem ?: run {
      resetSelectedItemIcon()
      return
    }

    val typedPrefix = lookup.itemPattern(selectedItem)
    if (canExecuteWithChosenItem(selectedItem.lookupString, typedPrefix)) {
      selectedItem.getTerminalIcon()?.forceIcon(AllIcons.Actions.Execute)
      curSelectedItem = selectedItem
    }
    else {
      resetSelectedItemIcon()
    }
  }

  private fun resetSelectedItemIcon() {
    curSelectedItem?.getTerminalIcon()?.useDefaultIcon()
    curSelectedItem = null
  }

  private fun LookupElement.getTerminalIcon(): TerminalStatefulDelegatingIcon? {
    val presentation = LookupElementPresentation()
    renderElement(presentation)
    val icon = presentation.icon
    return if (icon !is TerminalStatefulDelegatingIcon) {
      LOG.warn("Unexpected icon type: ${icon?.javaClass?.name}. All elements in terminal lookup should use TerminalStatefulDelegatingIcon")
      null
    }
    else icon
  }

  companion object {
    private val LOG = logger<TerminalSelectedItemIconUpdater>()
  }
}

/**
 * Hides the lookup if the text below line with cursor is changed.
 * It is the heuristic to address the following cases:
 * 1. We showed the popup, the user pressed Tab, the shell printed the completion items.
 * 2. We showed the popup, the user pressed Ctrl+R, search in command history feature was activated.
 */
private class TerminalLookupOutputModelListener(
  private val lookup: LookupEx,
  model: TerminalOutputModel,
) : TerminalOutputModelListener {
  private val initialTextBelowCursor = model.getTextBelowCursorLine().trim()

  override fun afterContentChanged(model: TerminalOutputModel, startOffset: Int, isTypeAhead: Boolean) {
    val textBelowCursor = model.getTextBelowCursorLine().trim()
    if (textBelowCursor != initialTextBelowCursor) {
      lookup.hideLookup(true)
    }
  }

  private fun TerminalOutputModel.getTextBelowCursorLine(): String {
    val cursorOffset = cursorOffsetState.value.toRelative()
    val line = document.getLineNumber(cursorOffset)
    val lineEndOffset = document.getLineEndOffset(line)
    return document.getText(TextRange(lineEndOffset, document.textLength))
  }
}

/**
 * Returns `true` if we need to execute the command immediately if user select [chosenItemString] in the Lookup.
 */
internal fun canExecuteWithChosenItem(chosenItemString: String, typedString: String): Boolean {
  val isCaseSensitive = SystemInfo.isFileSystemCaseSensitive
  return chosenItemString.equals(typedString, ignoreCase = !isCaseSensitive)
         // If the typed string differs only by the absence of the trailing slash, execute the command as well
         || chosenItemString.equals("$typedString/", ignoreCase = !isCaseSensitive)
         || chosenItemString.equals("$typedString\\", ignoreCase = !isCaseSensitive)
}