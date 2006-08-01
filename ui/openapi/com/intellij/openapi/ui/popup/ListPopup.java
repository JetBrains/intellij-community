package com.intellij.openapi.ui.popup;

/**
 * @author mike
 */
public interface ListPopup extends JBPopup {

  ListPopupStep getListStep();

  void handleSelect(boolean handleFinalChoices);
}
