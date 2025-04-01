package com.intellij.terminal.frontend


import com.google.common.base.Ascii
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupArranger
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.EmptyLookupItem
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.client.ClientProjectSession
import com.intellij.openapi.editor.Editor


internal class TerminalLookup(session: ClientProjectSession, editor: Editor, myArranger: LookupArranger, val terminalInput: TerminalInput) : LookupImpl(session, editor, myArranger) {

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
    val commandSize = itemPattern(item).length
    terminalInput.sendBytes(ByteArray(commandSize) { Ascii.BS })
    terminalInput.sendString(item.lookupString)
    hide()
  }
}