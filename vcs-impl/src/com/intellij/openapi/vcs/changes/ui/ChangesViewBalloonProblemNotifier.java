package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;

import javax.swing.*;
import java.awt.*;

public class ChangesViewBalloonProblemNotifier implements Runnable {
  private final Project myProject;
  private final String myMessage;
  private final MessageType myMessageType;

  public ChangesViewBalloonProblemNotifier(final Project project, final String message, final MessageType messageType) {
    myProject = project;
    myMessage = message;
    myMessageType = messageType;
  }

  public void run() {
    final ToolWindowManager manager = ToolWindowManager.getInstance(myProject);
    final boolean haveWindow = (! myProject.isDefault()) && (manager.getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID) != null);
    if (haveWindow) {
      manager.notifyByBalloon(ChangesViewContentManager.TOOLWINDOW_ID, myMessageType, myMessage, null, null);
    } else {
      final JFrame frame = WindowManager.getInstance().getFrame(myProject.isDefault() ? null : myProject);
      if (frame == null) return;
      final JComponent component = frame.getRootPane();
      if (component == null) return;
      final Rectangle rect = component.getVisibleRect();
      final Point p = new Point(rect.x + 30, rect.y + rect.height - 10);
      final RelativePoint point = new RelativePoint(component, p);

      JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
        myMessage, myMessageType.getDefaultIcon(), myMessageType.getPopupBackground(), null).createBalloon().show(
          point, Balloon.Position.above);
    }
  }
}
