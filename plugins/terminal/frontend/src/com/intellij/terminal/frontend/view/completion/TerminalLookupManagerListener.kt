package com.intellij.terminal.frontend.view.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.codeInsight.lookup.LookupPresentation
import com.intellij.codeInsight.lookup.impl.EmptyLookupItem
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.PrefixChangeListener
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.TerminalUiSettingsManager
import com.intellij.terminal.frontend.view.impl.TerminalInput
import kotlinx.coroutines.cancel
import org.jetbrains.plugins.terminal.block.reworked.TerminalCommandCompletion
import org.jetbrains.plugins.terminal.block.reworked.TerminalUsageLocalStorage
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputModelEditor
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
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
    val item = event.item
    if (item == null || !item.isValid() || item is EmptyLookupItem) {
      return false
    }

    insertTerminalCompletionItem(event.lookup as LookupImpl, item)

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
    else {
      TerminalUsageLocalStorage.getInstance().recordCompletionItemChosen()
    }
  }

  private fun executeCommand(lookup: Lookup) {
    val terminalInput = lookup.editor.getUserData(TerminalInput.Companion.KEY) ?: return
    terminalInput.sendEnter()
  }

  override fun firstElementShown() {
    TerminalUsageLocalStorage.getInstance().recordCompletionPopupShown()
  }

  /**
   * Stores the last selected item in the lookup by [TerminalCommandCompletion.LAST_SELECTED_ITEM_KEY].
   */
  override fun currentItemChanged(event: LookupEvent) {
    val lookup = event.lookup as? LookupImpl ?: return
    val item = event.item ?: return
    lookup.putUserData(TerminalCommandCompletion.LAST_SELECTED_ITEM_KEY, item)
  }
}

/**
 * Set's the [AllIcons.Actions.Execute] icon for the selected item in the lookup if it matches the user input.
 * To indicate that insertion of the item will cause immediate execution of the command.
 */
private class TerminalSelectedItemIconUpdater(private val lookup: LookupImpl) : PrefixChangeListener {
  private var curSelectedItem: LookupElement? = null

  override fun afterAppend(c: Char) {
    scheduleUpdate()
  }

  override fun afterTruncate() {
    scheduleUpdate()
  }

  private fun scheduleUpdate() {
    invokeLater(ModalityState.stateForComponent(lookup.component)) {
      if (!lookup.isLookupDisposed) {
        updateSelectedItemIcon()
      }
    }
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
      lookup.list.repaint()
    }
    else {
      resetSelectedItemIcon()
    }
  }

  private fun resetSelectedItemIcon() {
    curSelectedItem?.getTerminalIcon()?.useDefaultIcon()
    curSelectedItem = null
    lookup.list.repaint()
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

  override fun afterContentChanged(event: TerminalContentChangeEvent) {
    val textBelowCursor = event.model.getTextBelowCursorLine().trim()
    if (textBelowCursor != initialTextBelowCursor) {
      lookup.hideLookup(false)
    }
  }

  private fun TerminalOutputModel.getTextBelowCursorLine(): CharSequence {
    val line = getLineByOffset(this.cursorOffset)
    val lineEndOffset = getEndOfLine(line)
    return getText(lineEndOffset, endOffset)
  }
}

/**
 * Returns `true` if we need to execute the command immediately if user select [chosenItemString] in the Lookup.
 */
internal fun canExecuteWithChosenItem(chosenItemString: String, typedString: String): Boolean {
  return chosenItemString.equals(typedString, ignoreCase = true)
         // If the typed string differs only by the absence of the trailing slash, execute the command as well
         || chosenItemString.equals("$typedString/", ignoreCase = true)
         || chosenItemString.equals("$typedString\\", ignoreCase = true)
}