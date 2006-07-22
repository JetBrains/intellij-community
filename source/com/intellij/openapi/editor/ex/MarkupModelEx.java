package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.impl.HighlighterList;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public interface MarkupModelEx extends MarkupModel {
  void dispose();

  @Nullable
  HighlighterList getHighlighterList();
  
  RangeHighlighter addPersistentLineHighlighter(int lineNumber, int layer, TextAttributes textAttributes);
  boolean containsHighlighter(RangeHighlighter highlighter);

  void addMarkupModelListener(MarkupModelListener listener);
  void removeMarkupModelListener(MarkupModelListener listener);
}
