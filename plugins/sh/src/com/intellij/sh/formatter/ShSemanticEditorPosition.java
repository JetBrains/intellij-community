// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.formatter;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

final class ShSemanticEditorPosition {
  private final HighlighterIterator myIterator;

  private ShSemanticEditorPosition(@NotNull EditorEx editor, int offset) {
    myIterator = editor.getHighlighter().createIterator(offset);
  }

  void moveBefore() {
    if (!myIterator.atEnd()) {
      myIterator.retreat();
    }
  }

  void moveBeforeOptionalMix(IElementType @NotNull ... elements) {
    while (isAtAnyOf(elements)) {
      myIterator.retreat();
    }
  }

  void moveAfterOptionalMix(IElementType @NotNull ... elements) {
    while (isAtAnyOf(elements)) {
      myIterator.advance();
    }
  }

  boolean isAtAnyOf(IElementType @NotNull ... syntaxElements) {
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
    return !myIterator.atEnd() ? myIterator.getStart() : -1;
  }

  @NotNull
  static ShSemanticEditorPosition createEditorPosition(@NotNull EditorEx editor, int offset) {
    return new ShSemanticEditorPosition(editor, offset);
  }
}
