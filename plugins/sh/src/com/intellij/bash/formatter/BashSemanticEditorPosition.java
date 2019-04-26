package com.intellij.bash.formatter;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

class BashSemanticEditorPosition {
  private final HighlighterIterator myIterator;

  private BashSemanticEditorPosition(@NotNull EditorEx editor, int offset) {
    myIterator = editor.getHighlighter().createIterator(offset);
  }

  void moveBefore() {
    if (!myIterator.atEnd()) {
      myIterator.retreat();
    }
  }

  void moveBeforeOptionalMix(@NotNull IElementType... elements) {
    while (isAtAnyOf(elements)) {
      myIterator.retreat();
    }
  }

  void moveAfterOptionalMix(@NotNull IElementType... elements) {
    while (isAtAnyOf(elements)) {
      myIterator.advance();
    }
  }

  boolean isAtAnyOf(@NotNull IElementType... syntaxElements) {
    if (!myIterator.atEnd()) {
      IElementType currElement = myIterator.getTokenType();
      for (IElementType element : syntaxElements) {
        if (element.equals(currElement)) return true;
      }
    }
    return false;
  }

  boolean isAt(@NotNull IElementType elementType) {
    return !myIterator.atEnd() && myIterator.getTokenType() == elementType;
  }

  int getStartOffset() {
    return myIterator.getStart();
  }

  @NotNull
  static BashSemanticEditorPosition createEditorPosition(@NotNull EditorEx editor, int offset) {
    return new BashSemanticEditorPosition(editor, offset);
  }
}
