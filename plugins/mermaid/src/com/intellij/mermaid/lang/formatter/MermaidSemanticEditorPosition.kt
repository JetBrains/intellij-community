// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.formatter

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.psi.tree.IElementType

class MermaidSemanticEditorPosition(
  editor: Editor,
  offset: Int,
  val iterator: HighlighterIterator = editor.highlighter.createIterator(offset)
) {

  companion object {
    fun createEditorPosition(editor: Editor, offset: Int): MermaidSemanticEditorPosition {
      return MermaidSemanticEditorPosition(editor, offset)
    }
  }

  fun moveBefore() {
    if (!iterator.atEnd()) {
      iterator.retreat()
    }
  }

  fun moveBeforeOptionalMix(vararg elements: IElementType) {
    while (isAtAnyOf(*elements)) {
      iterator.retreat()
    }
  }

  fun moveBeforeNotAtOptionalMix(vararg elements: IElementType) {
    while (!isAtAnyOf(*elements) && !iterator.atEnd()) {
      iterator.retreat()
    }
  }

  fun moveAfterOptionalMix(vararg elements: IElementType) {
    while (isAtAnyOf(*elements)) {
      iterator.advance()
    }
  }

  fun isAtAnyOf(vararg syntaxElements: IElementType): Boolean {
    if (!iterator.atEnd()) {
      val currElement = iterator.tokenType
      for (element in syntaxElements) {
        if (element == currElement) return true
      }
    }
    return false
  }

  fun isAt(elementType: IElementType): Boolean {
    return !iterator.atEnd() && iterator.tokenType === elementType
  }

  fun getStartOffset(): Int {
    return if (!iterator.atEnd()) iterator.start else -1
  }
}
