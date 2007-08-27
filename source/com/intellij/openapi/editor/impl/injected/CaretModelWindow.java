package com.intellij.openapi.editor.impl.injected;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.ex.EditorEx;

/**
 * @author Alexey
 */
public class CaretModelWindow implements CaretModel {
  private final CaretModel myDelegate;
  private final EditorEx myHostEditor;
  private final EditorWindow myEditorWindow;

  public CaretModelWindow(CaretModel delegate, EditorWindow editorWindow) {
    myDelegate = delegate;
    myHostEditor = (EditorEx)editorWindow.getDelegate();
    myEditorWindow = editorWindow;
  }

  public void moveCaretRelatively(final int columnShift,
                                  final int lineShift,
                                  final boolean withSelection,
                                  final boolean blockSelection,
                                  final boolean scrollToCaret) {
    myDelegate.moveCaretRelatively(columnShift, lineShift, withSelection, blockSelection, scrollToCaret);
  }

  public void moveToLogicalPosition(final LogicalPosition pos) {
    LogicalPosition hostPos = myEditorWindow.injectedToHost(pos);
    myDelegate.moveToLogicalPosition(hostPos);
  }

  public void moveToVisualPosition(final VisualPosition pos) {
    LogicalPosition hostPos = myEditorWindow.injectedToHost(myEditorWindow.visualToLogicalPosition(pos));
    myDelegate.moveToLogicalPosition(hostPos);
  }

  public void moveToOffset(final int offset) {
    int hostOffset = myEditorWindow.getDocument().injectedToHost(offset);
    myDelegate.moveToOffset(hostOffset);
  }

  public LogicalPosition getLogicalPosition() {
    return myEditorWindow.hostToInjected(myHostEditor.offsetToLogicalPosition(myDelegate.getOffset()));
  }

  public VisualPosition getVisualPosition() {
    LogicalPosition logicalPosition = getLogicalPosition();
    return myEditorWindow.logicalToVisualPosition(logicalPosition);
  }

  public int getOffset() {
    return myEditorWindow.getDocument().hostToInjected(myDelegate.getOffset());
  }

  private final ListenerWrapperMap<CaretListener> myCaretListeners = new ListenerWrapperMap<CaretListener>();
  public void addCaretListener(final CaretListener listener) {
    CaretListener wrapper = new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        CaretEvent event = new CaretEvent(myEditorWindow, myEditorWindow.hostToInjected(e.getOldPosition()),
                                          myEditorWindow.hostToInjected(e.getNewPosition()));
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