package com.intellij.openapi.ui.popup;


import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author mike
 */
public interface JBPopup {
  void showUnderneathOf(Component componentUnder);
  void show(RelativePoint point);
  void showInScreenCoordinates(@NotNull Component owner, Point point);
  void showInBestPositionFor(DataContext dataContext);
  void showInBestPositionFor(Editor editor);
  void showInCenterOf(@NotNull Component focusOwner);
  void showCenteredInCurrentWindow(Project project);

  void cancel();

  boolean canClose();

  boolean isVisible();

  Component getContent();
}
