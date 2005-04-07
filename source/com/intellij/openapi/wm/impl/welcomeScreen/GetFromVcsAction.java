package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.actions.QuickSwitchSchemeAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionListPopup;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.ListPopup;

import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: pti
 * Date: Mar 23, 2005
 * Time: 9:51:44 PM
 * To change this template use File | Settings | File Templates.
 */
public class GetFromVcsAction extends QuickSwitchSchemeAction{

  protected void fillActions(Project project, DefaultActionGroup group) {
    final ActionManager actionManager = ActionManager.getInstance();
    final AnAction cvs = actionManager.getAction("Cvs.CheckoutProject");
    if (cvs != null) {
      group.add(cvs);
    }
    final AnAction svn = actionManager.getAction("Svn.CheckoutProject");
    if (svn != null) {
      group.add(svn);
    }
  }

  public void actionPerformed(AnActionEvent e) {
    final DefaultActionGroup group = new DefaultActionGroup();
    fillActions(null, group);

    if (group.getChildrenCount() == 0) {
      group.add(new AnAction("No VCS plugins with Check-out action installed.") {
        public void actionPerformed(AnActionEvent e) {
          group.setPopup(false);
        }
      } );
    }

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
    return true;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
