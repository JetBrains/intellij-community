package com.intellij.openapi.editor.impl.event;

import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;

import java.util.EventObject;

public class MarkupModelEvent extends EventObject {
  private final RangeHighlighter myHighlighter;

  public MarkupModelEvent(MarkupModel source, RangeHighlighter segmentHighlighter) {
    super(source);
    myHighlighter = segmentHighlighter;
  }

  public MarkupModel getMarkupModel() {
    return (MarkupModel) getSource();
  }

  public RangeHighlighter getHighlighter() {
    return myHighlighter;
  }
}
