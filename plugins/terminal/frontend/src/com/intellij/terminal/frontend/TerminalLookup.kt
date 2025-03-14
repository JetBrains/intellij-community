package com.intellij.terminal.frontend


import com.intellij.codeInsight.lookup.LookupArranger
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.EmptyLookupItem
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.client.ClientProjectSession
import com.intellij.openapi.editor.Editor


class TerminalLookup(session: ClientProjectSession, editor: Editor, myArranger: LookupArranger, val terminalInput: TerminalInput) : LookupImpl(session, editor, myArranger) {

  override fun isCompletion(): Boolean {
    return true
  }

  override fun finishLookup(completionChar: Char) {
    finishLookup(completionChar, list.getSelectedValue())
  }

  override fun finishLookup(completionChar: Char, item: LookupElement?) {
    if (item == null || !item.isValid() || item is EmptyLookupItem) {
      hideWithItemSelected(null, completionChar)
      return
    }
    terminalInput.sendString(item.lookupString)
    hide()
  }
}