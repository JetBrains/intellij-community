package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.ActionButtonComponent;
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook;

import javax.swing.*;
import java.awt.*;

public abstract class ActionButtonLook {
  public static final ActionButtonLook IDEA_LOOK = new IdeaActionButtonLook();

  public <ButtonType extends JComponent & ActionButtonComponent> void paintBackground(Graphics g, ButtonType button) {
    paintBackground(g, button, getState(button));
  }

  public <ButtonType extends JComponent & ActionButtonComponent> void paintBorder(Graphics g, ButtonType button) {
    paintBorder(g, button, getState(button));
  }

  public abstract void paintBackground(Graphics g, JComponent component, int state);

  public abstract void paintBorder(Graphics g, JComponent component, int state);

  protected int getState(ActionButtonComponent button) { // Do NOT inline this method.
    // Because of compiler bug upcast from ButtonType to ActionButtonComponent is important
    return button.getPopState();
  }

  public abstract void paintIcon(Graphics g, ActionButtonComponent actionButton, Icon icon);

  public abstract void paintIconAt(Graphics g, ActionButtonComponent button, Icon icon, int x, int y);
}
