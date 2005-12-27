package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.event.DocumentListener;

import java.beans.PropertyChangeListener;

public interface EditorEventMulticasterEx extends EditorEventMulticaster{
  void addErrorStripeListener(ErrorStripeListener listener);
  void removeErrorStripeListener(ErrorStripeListener listener);

  void addEditReadOnlyListener(EditReadOnlyListener listener);
  void removeEditReadOnlyListener(EditReadOnlyListener listener);

  void addPropertyChangeListener(PropertyChangeListener listener);
  void removePropertyChangeListener(PropertyChangeListener listener);

  void addFocusChangeListner(FocusChangeListener listener);
  void removeFocusChangeListner(FocusChangeListener listener);
}
