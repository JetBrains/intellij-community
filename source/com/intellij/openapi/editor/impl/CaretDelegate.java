package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.util.TextRange;

/**
 * @author Alexey
 */
public class CaretDelegate implements CaretModel {
  private final CaretModel myDelegate;
  private TextRange myRange;


  public CaretDelegate(CaretModel delegate, final TextRange range) {
    myDelegate = delegate;
    myRange = range;
  }

  public void moveCaretRelatively(final int columnShift,
                                  final int lineShift,
                                  final boolean withSelection,
                                  final boolean blockSelection,
                                  final boolean scrollToCaret) {
    myDelegate.moveCaretRelatively(columnShift, lineShift, withSelection, blockSelection, scrollToCaret);
  }

  public void moveToLogicalPosition(final LogicalPosition pos) {
    myDelegate.moveToLogicalPosition(pos);
  }

  public void moveToVisualPosition(final VisualPosition pos) {
    myDelegate.moveToVisualPosition(pos);
  }

  public void moveToOffset(final int offset) {
    myDelegate.moveToOffset(offset+myRange.getStartOffset());
  }

  public LogicalPosition getLogicalPosition() {
    return myDelegate.getLogicalPosition();
  }

  public VisualPosition getVisualPosition() {
    return myDelegate.getVisualPosition();
  }

  public int getOffset() {
    return myDelegate.getOffset() - myRange.getStartOffset();
  }

  public void addCaretListener(final CaretListener listener) {
    myDelegate.addCaretListener(listener);
  }

  public void removeCaretListener(final CaretListener listener) {
    myDelegate.removeCaretListener(listener);
  }
}
