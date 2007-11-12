package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.wm.ex.WindowManagerEx;

import javax.swing.*;
import java.awt.*;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class WelcomePopupAction extends AnAction {

  protected abstract void fillActions(DefaultActionGroup group);

  protected abstract String getTextForEmpty();

  protected abstract String getCaption();

  public void actionPerformed(final AnActionEvent e) {
    showPopup(e.getDataContext());
  }

  private void showPopup(final DataContext context) {
    final DefaultActionGroup group = new DefaultActionGroup();
    fillActions(group);

    if (group.getChildrenCount() == 0) {
      group.add(new AnAction(getTextForEmpty()) {
        public void actionPerformed(AnActionEvent e) {
          group.setPopup(false);
        }
      } );
    }

    final ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(getCaption(),
                              group,
                              context,
                              JBPopupFactory.ActionSelectionAid.NUMBERING,
                              true);


    Component focusedComponent = PlatformDataKeys.CONTEXT_COMPONENT.getData(context);
    if (focusedComponent != null) {
      popup.showUnderneathOf(focusedComponent);
    }
    else {
      Rectangle r;
      int x;
      int y;
      focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent((Project)null);
      r = WindowManagerEx.getInstanceEx().getScreenBounds();
      x = r.x + r.width / 2;
      y = r.y + r.height / 2;
      Point point = new Point(x, y);
      SwingUtilities.convertPointToScreen(point, focusedComponent.getParent());

      popup.showInScreenCoordinates(focusedComponent.getParent(), point);
    }
  }

}
