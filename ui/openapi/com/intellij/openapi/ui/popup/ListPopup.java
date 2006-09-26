package com.intellij.openapi.ui.popup;

/**
 * A popup window displaying a list of items (or other actions).
 *
 * @author mike
 * @see com.intellij.openapi.ui.popup.JBPopupFactory#createActionGroupPopup
 * @see com.intellij.openapi.ui.popup.JBPopupFactory#createWizardStep
 * @since 6.0
 */
public interface ListPopup extends JBPopup {

  /**
   * Returns the popup step currently displayed in the popup.
   *
   * @return the popup step.
   */
  ListPopupStep getListStep();

  /**
   * Handles the selection of the currently focused item in the popup step.
   *
   * @param handleFinalChoices If true, the action of the focused item is always executed
   * (as if Enter was pressed). If false, and the focused item has a submenu, the submenu
   * is opened (as if the right arrow key was pressed). 
   */
  void handleSelect(boolean handleFinalChoices);
}
