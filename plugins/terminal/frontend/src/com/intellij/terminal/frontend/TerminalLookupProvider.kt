package com.intellij.terminal.frontend

import com.intellij.codeInsight.lookup.LookupArranger
import com.intellij.codeInsight.lookup.impl.LookupProvider
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.client.ClientProjectSession
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor

internal class TerminalLookupProvider : LookupProvider {
  override fun createLookup(editor: Editor, arranger: LookupArranger, session: ClientProjectSession): LookupImpl? {
    val terminalInput = editor.getUserData(TerminalInput.KEY)
    if (terminalInput == null || editor.isReworkedTerminalEditor != true) {
      return null
    }
    return TerminalLookup(session, editor, arranger, terminalInput) as LookupImpl
  }
}
