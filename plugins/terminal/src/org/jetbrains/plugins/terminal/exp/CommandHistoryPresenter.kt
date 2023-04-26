// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.lookup.*
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import kotlin.math.max

class CommandHistoryPresenter(private val project: Project, private val editor: Editor) {
  private var initialCommand: String? = null

  fun showCommandHistory(history: List<String>) {
    val command = editor.document.text
    initialCommand = command
    val prefix = command.trim()
    val arranger = CommandHistoryLookupArranger()
    val lookup = LookupManager.getInstance(project).createLookup(editor, emptyArray(), prefix, arranger) as LookupImpl
    val matcher = PlainPrefixMatcher(prefix, true)
    history.forEachIndexed { index, cmd ->
      // put index as lookup object to make each lookup element distinct in terms of 'equals' method of LookupElementBuilder
      val item = LookupElementBuilder.create(index, cmd)
      lookup.addItem(item, matcher)
    }

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
        // prevent item inserting, because it was already inserted in a result of 'currentItemChanged'
        return false
      }

      override fun itemSelected(event: LookupEvent) {
        initialCommand = null
      }

      override fun lookupCanceled(event: LookupEvent) {
        initialCommand = null
      }
    })

    val showBottomPanel = editor.getUserData(AutoPopupController.SHOW_BOTTOM_PANEL_IN_LOOKUP_UI)
    try {
      editor.putUserData(AutoPopupController.SHOW_BOTTOM_PANEL_IN_LOOKUP_UI, false)
      if (lookup.showLookup()) {
        lookup.ensureSelectionVisible(false)
      }
      else thisLogger().error("Failed to show command history")
    }
    finally {
      editor.putUserData(AutoPopupController.SHOW_BOTTOM_PANEL_IN_LOOKUP_UI, showBottomPanel)
    }
  }

  /**
   * Should be invoked when user intentionally close the history popup.
   * For example, when Escape is pressed.
   */
  fun onCommandHistoryClosed() {
    val commandToRestore = initialCommand
    if (commandToRestore != null) {
      initialCommand = null
      invokeLater {
        runWriteAction {
          editor.document.setText(commandToRestore)
          editor.caretModel.moveToOffset(commandToRestore.length)
        }
      }
    }
  }

  private class CommandHistoryLookupArranger : LookupArranger() {
    override fun arrangeItems(lookup: Lookup, onExplicitAction: Boolean): Pair<List<LookupElement>, Int> {
      val result = mutableListOf<LookupElement>()
      matchingItems.forEach { item ->
        if (result.lastOrNull()?.lookupString != item.lookupString) {
          result.add(item)
        }
      }
      val selectedIndex = if (!lookup.isSelectionTouched && onExplicitAction) result.lastIndex else result.indexOf(lookup.currentItem)
      return Pair.create(result, max(selectedIndex, 0))
    }

    override fun createEmptyCopy(): LookupArranger {
      return CommandHistoryLookupArranger()
    }
  }

  companion object {
    val IS_COMMAND_HISTORY_LOOKUP_KEY: Key<Boolean> = Key.create("isCommandHistoryLookup")
  }
}