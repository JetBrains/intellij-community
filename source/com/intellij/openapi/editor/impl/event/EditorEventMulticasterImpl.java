package com.intellij.openapi.editor.impl.event;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.EventDispatcher;

import java.beans.PropertyChangeListener;

public class EditorEventMulticasterImpl implements EditorEventMulticasterEx {
  private EventDispatcher<DocumentListener> myDocumentMulticaster = EventDispatcher.create(DocumentListener.class);
  private EventDispatcher<EditReadOnlyListener> myEditReadOnlyMulticaster = EventDispatcher.create(EditReadOnlyListener.class);

  private EventDispatcher<EditorMouseListener> myEditorMouseMulticaster = EventDispatcher.create(EditorMouseListener.class);
  private EventDispatcher<EditorMouseMotionListener> myEditorMouseMotionMulticaster = EventDispatcher.create(EditorMouseMotionListener.class);
  private EventDispatcher<ErrorStripeListener> myErrorStripeMulticaster = EventDispatcher.create(ErrorStripeListener.class);
  private EventDispatcher<CaretListener> myCaretMulticaster = EventDispatcher.create(CaretListener.class);
  private EventDispatcher<SelectionListener> mySelectionMulticaster = EventDispatcher.create(SelectionListener.class);
  private EventDispatcher<VisibleAreaListener> myVisibleAreaMulticaster = EventDispatcher.create(VisibleAreaListener.class);
  private EventDispatcher<PropertyChangeListener> myPropertyChangeMulticaster = EventDispatcher.create(PropertyChangeListener.class);
  private EventDispatcher<FocusChangeListener> myFocusChangeListenerMulticaster = EventDispatcher.create(FocusChangeListener.class);

  public void registerDocument(DocumentEx document) {
    document.addDocumentListener(myDocumentMulticaster.getMulticaster());
    document.addEditReadOnlyListener(myEditReadOnlyMulticaster.getMulticaster());
  }

  public void registerEditor(EditorEx editor) {
    editor.addEditorMouseListener(myEditorMouseMulticaster.getMulticaster());
    editor.addEditorMouseMotionListener(myEditorMouseMotionMulticaster.getMulticaster());
    ((EditorMarkupModel) editor.getMarkupModel()).addErrorMarkerListener(myErrorStripeMulticaster.getMulticaster());
    editor.getCaretModel().addCaretListener(myCaretMulticaster.getMulticaster());
    editor.getSelectionModel().addSelectionListener(mySelectionMulticaster.getMulticaster());
    editor.getScrollingModel().addVisibleAreaListener(myVisibleAreaMulticaster.getMulticaster());
    editor.addPropertyChangeListener(myPropertyChangeMulticaster.getMulticaster());
    editor.addFocusListener(myFocusChangeListenerMulticaster.getMulticaster());
  }

  public void addDocumentListener(DocumentListener listener) {
    myDocumentMulticaster.addListener(listener);
  }

  public void addDocumentListener(final DocumentListener listener, Disposable parentDisposable) {
    addDocumentListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeDocumentListener(listener);
      }
    });
  }

  public void removeDocumentListener(DocumentListener listener) {
    myDocumentMulticaster.removeListener(listener);
  }

  public void addEditorMouseListener(EditorMouseListener listener) {
    myEditorMouseMulticaster.addListener(listener);
  }

  public void removeEditorMouseListener(EditorMouseListener listener) {
    myEditorMouseMulticaster.removeListener(listener);
  }

  public void addEditorMouseMotionListener(EditorMouseMotionListener listener) {
    myEditorMouseMotionMulticaster.addListener(listener);
  }

  public void removeEditorMouseMotionListener(EditorMouseMotionListener listener) {
    myEditorMouseMotionMulticaster.removeListener(listener);
  }

  public void addCaretListener(CaretListener listener) {
    myCaretMulticaster.addListener(listener);
  }

  public void removeCaretListener(CaretListener listener) {
    myCaretMulticaster.removeListener(listener);
  }

  public void addSelectionListener(SelectionListener listener) {
    mySelectionMulticaster.addListener(listener);
  }

  public void removeSelectionListener(SelectionListener listener) {
    mySelectionMulticaster.removeListener(listener);
  }

  public void addErrorStripeListener(ErrorStripeListener listener) {
    myErrorStripeMulticaster.addListener(listener);
  }

  public void removeErrorStripeListener(ErrorStripeListener listener) {
    myErrorStripeMulticaster.removeListener(listener);
  }

  public void addVisibleAreaListener(VisibleAreaListener listener) {
    myVisibleAreaMulticaster.addListener(listener);
  }

  public void removeVisibleAreaListener(VisibleAreaListener listener) {
    myVisibleAreaMulticaster.removeListener(listener);
  }

  public void addEditReadOnlyListener(EditReadOnlyListener listener) {
    myEditReadOnlyMulticaster.addListener(listener);
  }

  public void removeEditReadOnlyListener(EditReadOnlyListener listener) {
    myEditReadOnlyMulticaster.removeListener(listener);
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeMulticaster.addListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeMulticaster.removeListener(listener);
  }

  public void addFocusChangeListner(FocusChangeListener listener) {
    myFocusChangeListenerMulticaster.addListener(listener);
  }

  public void removeFocusChangeListner(FocusChangeListener listener) {
    myFocusChangeListenerMulticaster.removeListener(listener);
  }
}
