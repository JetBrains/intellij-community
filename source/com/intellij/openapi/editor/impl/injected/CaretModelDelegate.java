package com.intellij.openapi.editor.impl.injected;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.ex.EditorEx;

/**
 * @author Alexey
 */
public class CaretModelDelegate implements CaretModel {
  private final CaretModel myDelegate;
  private final EditorEx myHostEditor;
  private final EditorDelegate myEditorDelegate;

  public CaretModelDelegate(CaretModel delegate, EditorDelegate editorDelegate) {
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

  private final ListenerWrapperMap<CaretListener> myCaretListeners = new ListenerWrapperMap<CaretListener>();
  public void addCaretListener(final CaretListener listener) {
    CaretListener wrapper = new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        CaretEvent event = new CaretEvent(myEditorDelegate, myEditorDelegate.parentToInjected(e.getOldPosition()),
                                          myEditorDelegate.parentToInjected(e.getNewPosition()));
        listener.caretPositionChanged(event);
      }
    };
    myCaretListeners.registerWrapper(listener, wrapper);
    myDelegate.addCaretListener(wrapper);
  }

  public void removeCaretListener(final CaretListener listener) {
    CaretListener wrapper = myCaretListeners.removeWrapper(listener);
    if (wrapper != null) {
      myDelegate.removeCaretListener(wrapper);
    }
  }

  public void disposeModel() {
    for (CaretListener wrapper : myCaretListeners.wrappers()) {
      myDelegate.removeCaretListener(wrapper);
    }
    myCaretListeners.clear();
  }
}
