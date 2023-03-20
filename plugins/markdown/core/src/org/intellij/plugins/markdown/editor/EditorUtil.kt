package org.intellij.plugins.markdown.editor

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.CaretModel

internal fun CaretModel.runForEachCaret(reverseOrder: Boolean = false, block: (Caret) -> Unit) {
  runForEachCaret(block, reverseOrder)
}
