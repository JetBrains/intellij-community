package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.wm.ex.WindowManagerEx;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class WelcomePopupAction {

  protected abstract void fillActions(DefaultActionGroup group);

  protected abstract String getTextForEmpty();

  protected abstract String getCaption();

  public void showPopup(Component contextComponent, final InputEvent e) {
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
                              createDataContext(contextComponent),
                              JBPopupFactory.ActionSelectionAid.NUMBERING,
                              true);


    Component focusedComponent = e.getComponent();
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

  private static DataContext createDataContext(final Component contextComponent) {
    return new DataContext() {
      public Object getData(String dataId) {
        if (DataConstants.PROJECT.equals(dataId)) {
          return null;
        }
        return contextComponent;
      }
    };
  }
}
