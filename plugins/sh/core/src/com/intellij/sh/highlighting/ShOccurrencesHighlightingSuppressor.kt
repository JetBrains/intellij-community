package com.intellij.sh.highlighting

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile
import com.intellij.sh.psi.ShFile

interface ShOccurrencesHighlightingSuppressor {
  fun suppressOccurrencesHighlighting(editor: Editor, file: ShFile): Boolean

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<ShOccurrencesHighlightingSuppressor> = ExtensionPointName.create(
      "com.intellij.shellOccurrencesHighlightingSuppressor")

    @JvmStatic
    fun isOccurrencesHighlightingEnabled(editor: Editor, file: PsiFile): Boolean {
      if (editor.isOneLineMode() || file !is ShFile) return false
      return EP_NAME.extensionList.none {
        it.suppressOccurrencesHighlighting(editor, file)
      }
    }
  }
}