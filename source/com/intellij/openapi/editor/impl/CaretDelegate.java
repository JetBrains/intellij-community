package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.ex.EditorEx;

/**
 * @author Alexey
 */
public class CaretDelegate implements CaretModel {
  private final CaretModel myDelegate;
  private RangeMarker myRange;
  private final EditorEx myHostEditor;
  private final EditorDelegate myEditorDelegate;

  public CaretDelegate(CaretModel delegate, final RangeMarker range, EditorDelegate editorDelegate) {
    myDelegate = delegate;
    myRange = range;
    myHostEditor = (EditorEx)editorDelegate.getDelegate();
    myEditorDelegate = editorDelegate;
  }

  public void moveCaretRelatively(final int columnShift,
                                  final int lineShift,
                                  final boolean withSelection,
                                  final boolean blockSelection,
                                  final boolean scrollToCaret) {
    myDelegate.moveCaretRelatively(columnShift, lineShift, withSelection, blockSelection, scrollToCaret);
  }

  public void moveToLogicalPosition(final LogicalPosition pos) {
    LogicalPosition hostPos = myEditorDelegate.injectedToParent(pos);
    myDelegate.moveToLogicalPosition(hostPos);
  }

  public void moveToVisualPosition(final VisualPosition pos) {
    LogicalPosition hostPos = myEditorDelegate.injectedToParent(myEditorDelegate.visualToLogicalPosition(pos));
    myDelegate.moveToLogicalPosition(hostPos);
  }

  public void moveToOffset(final int offset) {
    myDelegate.moveToOffset(offset+myRange.getStartOffset());
  }

  public LogicalPosition getLogicalPosition() {
    return myEditorDelegate.parentToInjected(myHostEditor.offsetToLogicalPosition(myDelegate.getOffset()));
  }

  public VisualPosition getVisualPosition() {
    LogicalPosition logicalPosition = getLogicalPosition();
    return myEditorDelegate.logicalToVisualPosition(logicalPosition);
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
