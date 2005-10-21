package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionListPopup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.checkout.CheckoutAction;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.ListPopup;
import com.intellij.ui.UIBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;

public class GetFromVcsAction{

  protected void fillActions(Project project, DefaultActionGroup group) {
    final CheckoutProvider[] providers = ApplicationManager.getApplication().getComponents(CheckoutProvider.class);
    for (CheckoutProvider provider : providers) {
      group.add(new CheckoutAction(provider));
    }
  }

  public void actionPerformed(Component contextComponent, final InputEvent e) {
    final DefaultActionGroup group = new DefaultActionGroup();
    fillActions(null, group);

    if (group.getChildrenCount() == 0) {
      group.add(new AnAction(
        UIBundle.message("welcome.screen.get.from.vcs.action.no.vcs.plugins.with.check.out.action.installed.action.name")) {
        public void actionPerformed(AnActionEvent e) {
          group.setPopup(false);
        }
      } );
    }

    final ListPopup popup = ActionListPopup.createListPopup(
      UIBundle.message("welcome.screen.get.from.vcs.action.checkout.from.list.popup.title"), group, createDataContext(contextComponent), true, true);

    Rectangle r;
    int x;
    int y;

    Component focusedComponent = e.getComponent();

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

  private DataContext createDataContext(final Component contextComponent) {
    return new DataContext() {
      public Object getData(String dataId) {
        if (DataConstants.PROJECT.equals(dataId)) {
          return null;
        }
        return contextComponent;
      }
    };
  }

  protected boolean isEnabled() {
    return true;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
