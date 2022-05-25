package com.github.firsttimeinforever.mermaid.lang.formatter

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.psi.tree.IElementType

class MermaidSemanticEditorPosition(
  editor: Editor,
  offset: Int,
  private val iterator: HighlighterIterator = editor.highlighter.createIterator(offset)
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
