package com.intellij.openapi.ui.popup;


import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Base interface for popup windows.
 *
 * @author mike
 * @see com.intellij.openapi.ui.popup.JBPopupFactory
 * @since 6.0
 */
public interface JBPopup {
  /**
   * Shows the popup at the bottom left corner of the specified component.
   *
   * @param componentUnder the component near which the popup should be displayed.
   */
  void showUnderneathOf(Component componentUnder);

  /**
   * Shows the popup at the specified point.
   *
   * @param point the relative point where the popup should be displayed.
   */
  void show(RelativePoint point);

  void showInScreenCoordinates(@NotNull Component owner, Point point);

  /**
   * Shows the popup in the position most appropriate for the specified data context.
   *
   * @param dataContext the data context to which the popup is related.
   * @see com.intellij.openapi.ui.popup.JBPopupFactory#guessBestPopupLocation(com.intellij.openapi.actionSystem.DataContext)
   */
  void showInBestPositionFor(DataContext dataContext);

  /**
   * Shows the popup near the cursor location in the specified editor.
   *
   * @param editor the editor relative to which the popup should be displayed.
   * @see com.intellij.openapi.ui.popup.JBPopupFactory#guessBestPopupLocation(com.intellij.openapi.editor.Editor)
   */
  void showInBestPositionFor(Editor editor);

  /**
   * Shows the popup in the center of the specified component.
   *
   * @param focusOwner the component at which the popup should be centered.
   */
  void showInCenterOf(@NotNull Component focusOwner);

  /**
   * Shows the popup in the center of the active window in the IDEA frame for the specified project.
   *
   * @param project the project in which the popup should be displayed.
   */
  void showCenteredInCurrentWindow(Project project);

  /**
   * Cancels the popup (as if Esc was pressed).
   */
  void cancel();

  /**
   * Checks if it's currently allowed to close the popup.
   *
   * @return true if the popup can be closed, false if a callback disallowed closing the popup.
   * @see com.intellij.openapi.ui.popup.ComponentPopupBuilder#setCancelCallback(com.intellij.openapi.util.Computable<java.lang.Boolean>)
   */
  boolean canClose();

  /**
   * Checks if the popup is currently visible.
   *
   * @return true if the popup is visible, false otherwise.
   */
  boolean isVisible();

  /**
   * Returns the Swing component contained in the popup.
   *
   * @return the contents of the popup.
   */
  Component getContent();
}
