package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.ui.PopupHandler;

public interface EditorMarkupModel extends MarkupModel {
  Editor getEditor();

  void setErrorStripeVisible(boolean val);

  void setErrorStripeRenderer(ErrorStripeRenderer renderer);
  ErrorStripeRenderer getErrorStripeRenderer();

  void addErrorMarkerListener(ErrorStripeListener listener);
  void removeErrorMarkerListener(ErrorStripeListener listener);

  void setErrorPanelPopupHandler(PopupHandler handler);
}
