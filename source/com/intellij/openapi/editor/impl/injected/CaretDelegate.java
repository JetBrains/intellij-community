package com.intellij.openapi.editor.impl.injected;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.ex.EditorEx;

/**
 * @author Alexey
 */
public class CaretDelegate implements CaretModel {
  private final CaretModel myDelegate;
  private final EditorEx myHostEditor;
  private final EditorDelegate myEditorDelegate;

  public CaretDelegate(CaretModel delegate, EditorDelegate editorDelegate) {
    myDelegate = delegate;
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
    int hostOffset = myEditorDelegate.getDocument().injectedToHost(offset);
    myDelegate.moveToOffset(hostOffset);
  }

  public LogicalPosition getLogicalPosition() {
    return myEditorDelegate.parentToInjected(myHostEditor.offsetToLogicalPosition(myDelegate.getOffset()));
  }

  public VisualPosition getVisualPosition() {
    LogicalPosition logicalPosition = getLogicalPosition();
    return myEditorDelegate.logicalToVisualPosition(logicalPosition);
  }

  public int getOffset() {
    return myEditorDelegate.getDocument().hostToInjected(myDelegate.getOffset());
  }

  public void addCaretListener(final CaretListener listener) {
    myDelegate.addCaretListener(listener);
  }

  public void removeCaretListener(final CaretListener listener) {
    myDelegate.removeCaretListener(listener);
  }
}
