package com.intellij.openapi.wm.impl.status;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.wm.impl.IdeFrame;

import java.awt.*;

import org.jetbrains.annotations.Nullable;

public class ShowProcessWindowAction extends ToggleAction {

  public ShowProcessWindowAction() {
    super(ActionsBundle.message("action.ShowProcessWindow.text"), ActionsBundle.message("action.ShowProcessWindow.description"), null);
  }


  public boolean isSelected(final AnActionEvent e) {
    final IdeFrame frame = getFrame();
    if (frame == null) return false;
    return frame.getStatusBar().isProcessWindowOpen();
  }

  public void update(final AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(getFrame() != null);
  }

  @Nullable
  private IdeFrame getFrame() {
    Container window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    while (window != null) {
      if (window instanceof IdeFrame) return (IdeFrame)window;
      window = window.getParent();
    }

    return null;
  }

  public void setSelected(final AnActionEvent e, final boolean state) {
    final IdeFrame frame = getFrame();
    if (frame == null) return;
    frame.getStatusBar().setProcessWindowOpen(state);
  }
}
