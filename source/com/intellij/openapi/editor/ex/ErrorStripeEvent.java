package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;

import java.awt.event.MouseEvent;
import java.util.EventObject;

public class ErrorStripeEvent extends EventObject {
  private final MouseEvent myMouseEvent;
  private final RangeHighlighter myHighlighter;

  public ErrorStripeEvent(Editor editor, MouseEvent mouseEvent, RangeHighlighter highlighter) {
    super(editor);
    myMouseEvent = mouseEvent;
    myHighlighter = highlighter;
  }

  public Editor getEditor() {
    return (Editor) getSource();
  }

  public MouseEvent getMouseEvent() {
    return myMouseEvent;
  }

  public RangeHighlighter getHighlighter() {
    return myHighlighter;
  }
}
