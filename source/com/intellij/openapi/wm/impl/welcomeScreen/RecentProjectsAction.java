package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.actions.QuickSwitchSchemeAction;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionListPopup;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.ListPopup;
import com.intellij.ui.UIBundle;

import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: pti
 * Date: Mar 2, 2005
 * Time: 4:02:31 PM
 * To change this template use File | Settings | File Templates.
 */
public class RecentProjectsAction extends QuickSwitchSchemeAction {

  protected void fillActions(Project project, final DefaultActionGroup group) {
    final AnAction[] recentProjectActions = RecentProjectsManager.getInstance().getRecentProjectsActions(false);
    if (recentProjectActions == null || recentProjectActions.length == 0) {
      AnAction action = new AnAction(UIBundle.message("welcome.screen.recent.projects.action.no.recent.projects.to.display.action.name")) {
        public void actionPerformed(AnActionEvent e) {
          group.setPopup(false);
        }
      };
      group.add(action);
    }
    else {
      for (int i = 0; i < recentProjectActions.length; i++) {
        AnAction action = recentProjectActions[i];
        group.add(action);
      }
    }
  }

  public void actionPerformed(AnActionEvent e) {
    DefaultActionGroup group = new DefaultActionGroup();
    fillActions(null, group);

    final ListPopup popup = ActionListPopup.createListPopup(e.getPresentation().getText(), group, e.getDataContext(), true, true);

    Component focusedComponent = e.getInputEvent().getComponent();
    Rectangle r;
    int x;
    int y;

    if (focusedComponent != null) {
      r = focusedComponent.getBounds();
      x = r.x;
      y = r.y + r.height;
    } else {
      focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent((Project)null);
      r = WindowManagerEx.getInstanceEx().getScreenBounds();
      x = r.x + r.width / 2;
      y = r.y + r.height / 2;
    }

    Point point = new Point(x, y);
    SwingUtilities.convertPointToScreen(point, focusedComponent.getParent());
    popup.getWindow().pack();
    popup.show(point.x, point.y);
  }

  protected boolean isEnabled() {
    return true;
  }
}
