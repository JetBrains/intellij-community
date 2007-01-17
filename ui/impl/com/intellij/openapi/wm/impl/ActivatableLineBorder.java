package com.intellij.openapi.wm.impl;

import com.intellij.util.ui.UIUtil;
import com.intellij.openapi.wm.impl.InternalDecorator;

import javax.swing.border.Border;
import javax.swing.*;
import java.awt.*;

public class ActivatableLineBorder implements Border {

  public static Color ACTIVE_COLOR = new Color(160, 186, 213);
  public static Color INACTIVE_COLOR = new Color(128, 128, 128);

  private boolean active = false;

  private InternalDecorator myDecorator;

  public ActivatableLineBorder(final InternalDecorator decorator) {
    myDecorator = decorator;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Insets getBorderInsets(Component c) {
    return new Insets(1, 1, 1, 1);
  }

  public boolean isBorderOpaque() {
    return false;
  }

  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    final Color lineColor = active ? ACTIVE_COLOR : INACTIVE_COLOR;
    g.setColor(lineColor);

    final JComponent buttons = myDecorator.getTitlePanel().getSideButtonsComponent();
    final Rectangle buttonsRec = SwingUtilities.convertRectangle(buttons.getParent(), buttons.getBounds(), c);
    UIUtil.drawLine(g, x + 1, y, buttonsRec.x - 1, y);
    UIUtil.drawLine(g, x + 1, y + height - 1, x + width - 2, y + height - 1);
    UIUtil.drawLine(g, x, y + 1, x, y + height - 2);
    UIUtil.drawLine(g, x + width - 1, y + 1, x + width - 1, y + height - 2);

    g.setColor(TitlePanel.BUTTON_SEPARATOR_COLOR);
    g.drawLine(buttonsRec.x, y, buttonsRec.x, y);
    g.setColor(lineColor);
    UIUtil.drawLine(g, buttonsRec.x + 1, y, x + width - 2, y);
  }
}
