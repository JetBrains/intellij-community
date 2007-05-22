package com.intellij.openapi.wm.impl.content;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

class BaseLabel extends JLabel {

  protected ToolWindowContentUi myUi;

  private Color myActiveFg;
  private Color myPassiveFg;
  private boolean myBold;

  public BaseLabel(ToolWindowContentUi ui, boolean bold) {
    myUi = ui;
    setOpaque(false);
    setActiveFg(Color.white);
    setPassiveFg(Color.white);
    myBold = bold;
    updateFont();
  }

  public void updateUI() {
    super.updateUI();
    updateFont();
  }

  private void updateFont() {
    if (myBold) {
      setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    }
  }

  public void setActiveFg(final Color fg) {
    myActiveFg = fg;
  }

  public void setPassiveFg(final Color passiveFg) {
    myPassiveFg = passiveFg;
  }

  protected void paintComponent(final Graphics g) {
    setForeground(myUi.myWindow.isActive() ? myActiveFg : myPassiveFg);
    super.paintComponent(g);
  }

}
