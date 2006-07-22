/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This is mock implementation to be used in null-object pattern where necessary.
 * @author max
 */
public class EmptyMarkupModel implements MarkupModelEx {
  private Document myDocument;

  public EmptyMarkupModel(final Document document) {
    myDocument = document;
  }

  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @NotNull
  public RangeHighlighter addRangeHighlighter(int startOffset,
                                              int endOffset,
                                              int layer,
                                              @Nullable TextAttributes textAttributes,
                                              HighlighterTargetArea targetArea) {
    return new RangeHighlighterImpl(this, startOffset, endOffset, layer, targetArea, textAttributes, false);
  }

  @NotNull
  public RangeHighlighter addLineHighlighter(int line, int layer, @Nullable TextAttributes textAttributes) {
    return new RangeHighlighterImpl(this, 0, 0, layer, HighlighterTargetArea.LINES_IN_RANGE, textAttributes, false);
  }

  public void removeHighlighter(RangeHighlighter rangeHighlighter) {
  }

  public void removeAllHighlighters() {
  }

  @NotNull
  public RangeHighlighter[] getAllHighlighters() {
    return new RangeHighlighter[0];
  }

  public <T> T getUserData(Key<T> key) {
    return null;
  }

  public <T> void putUserData(Key<T> key, T value) {
  }

  public void dispose() {
  }

  public HighlighterList getHighlighterList() {
    return null;
  }

  public RangeHighlighter addPersistentLineHighlighter(int lineNumber, int layer, TextAttributes textAttributes) {
    return null;
  }

  public boolean containsHighlighter(RangeHighlighter highlighter) {
    return false;
  }

  public void addMarkupModelListener(MarkupModelListener listener) {
  }

  public void removeMarkupModelListener(MarkupModelListener listener) {
  }
}
