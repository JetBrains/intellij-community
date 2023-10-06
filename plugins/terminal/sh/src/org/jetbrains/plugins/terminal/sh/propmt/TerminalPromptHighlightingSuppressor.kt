// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.sh.prompt

import com.intellij.openapi.editor.Editor
import com.intellij.sh.highlighting.ShOccurrencesHighlightingSuppressor
import com.intellij.sh.psi.ShFile
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isPromptEditor

class TerminalPromptHighlightingSuppressor: ShOccurrencesHighlightingSuppressor {
  override fun suppressOccurrencesHighlighting(editor: Editor, file: ShFile): Boolean {
    return editor.isPromptEditor
  }
}