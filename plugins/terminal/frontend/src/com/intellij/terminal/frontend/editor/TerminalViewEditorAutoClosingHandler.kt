package com.intellij.terminal.frontend.editor

import com.intellij.openapi.fileEditor.impl.EditorAutoClosingHandler
import com.intellij.openapi.fileEditor.impl.EditorComposite

internal class TerminalViewEditorAutoClosingHandler : EditorAutoClosingHandler {
  override fun isClosingAllowed(composite: EditorComposite): Boolean {
    return composite.file !is TerminalViewVirtualFile
  }
}
