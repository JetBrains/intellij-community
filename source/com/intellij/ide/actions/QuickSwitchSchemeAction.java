package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionListPopup;
import com.intellij.openapi.actionSystem.impl.EmptyIcon;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.ListPopup;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
public abstract class QuickSwitchSchemeAction extends AnAction {
  protected static final Icon ourCurrentAction = IconLoader.getIcon("/diff/currentLine.png");
  protected static final Icon ourNotCurrentAction = EmptyIcon.create(ourCurrentAction.getIconWidth(), ourCurrentAction.getIconHeight());

  public void actionPerformed(AnActionEvent e) {
    Project project = (Project) e.getDataContext().getData(DataConstants.PROJECT);
    DefaultActionGroup group = new DefaultActionGroup();
    fillActions(project, group);
    showPopup(e, group);
  }

  protected abstract void fillActions(Project project, DefaultActionGroup group);

  private void showPopup(AnActionEvent e, DefaultActionGroup group) {
    final ListPopup popup = ActionListPopup.createListPopup(e.getPresentation().getText(), group, e.getDataContext(), true, true);

    Rectangle r = getFocusedWindowRect(e);
    popup.getWindow().pack();
    Dimension popupSize = popup.getSize();
    int x = r.x + r.width / 2 - popupSize.width / 2;
    int y = r.y + r.height / 2 - popupSize.height / 2;

    popup.show(x, y);
  }

  private Rectangle getFocusedWindowRect(final AnActionEvent e) {
    Project project = (Project) e.getDataContext().getData(DataConstants.PROJECT);
    Window window = null;
    Component focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(project);
    if (focusedComponent != null) {
      if (focusedComponent instanceof Window) {
        window = (Window) focusedComponent;
      } else {
        window = SwingUtilities.getWindowAncestor(focusedComponent);
      }
    }
    if (window == null) {
      window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    }

    Rectangle r;
    if (window != null) {
      r = window.getBounds();
    } else {
      r = WindowManagerEx.getInstanceEx().getScreenBounds();
    }
    return r;
  }

  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(isEnabled());
  }

  protected abstract boolean isEnabled();
}
