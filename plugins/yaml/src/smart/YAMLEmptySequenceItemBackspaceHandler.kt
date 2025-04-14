// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.smart

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.YAMLTokenTypes

class YAMLEmptySequenceItemBackspaceHandler : BackspaceHandlerDelegate() {
  private var shouldDelete = false

  override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) {
    shouldDelete = shouldDeleteSequenceMarker(c, file, editor)
  }

  private fun shouldDeleteSequenceMarker(c: Char, file: PsiFile, editor: Editor): Boolean {
    // we cannot do all this logic right in the charDeleted(...) method, because this method is called
    // when the char is already deleted, and in the case when the deleted char was in the end of a file,
    // the "-" is not parsed by a lexer as YAMLTokenTypes.SEQUENCE_MARKER anymore.

    if (!c.isWhitespace()) return false
    if (c != ' ') return false
    if (!file.language.isKindOf(YAMLLanguage.INSTANCE)) return false

    val possibleSequenceItemMarkerOffset = editor.caretModel.currentCaret.offset - 2
    if (possibleSequenceItemMarkerOffset < 0) return false
    if (possibleSequenceItemMarkerOffset >= editor.document.textLength) return false // virtual lines after the end of file?

    val possibleSequenceItemMarker = editor.document.getText(
      TextRange(possibleSequenceItemMarkerOffset, possibleSequenceItemMarkerOffset + 1))
    if (possibleSequenceItemMarker != "-") return false

    val iterator = editor.highlighter.createIterator(possibleSequenceItemMarkerOffset)
    if (iterator.tokenType != YAMLTokenTypes.SEQUENCE_MARKER) return false

    return true
  }

  override fun charDeleted(c: Char, file: PsiFile, editor: Editor): Boolean {
    if (!shouldDelete) return false

    val sequenceItemMarkerOffset = editor.caretModel.currentCaret.offset - 1
    if (sequenceItemMarkerOffset < 0) return false
    if (sequenceItemMarkerOffset >= editor.document.textLength) return false

    val text = editor.document.getText(
      TextRange(sequenceItemMarkerOffset, sequenceItemMarkerOffset + 1))
    if (text != "-") return false

    editor.document.deleteString(sequenceItemMarkerOffset, sequenceItemMarkerOffset + 1)
    return true
  }
}