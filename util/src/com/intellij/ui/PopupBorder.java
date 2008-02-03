package com.intellij.ui;

import com.intellij.openapi.util.SystemInfo;

import javax.swing.border.Border;
import java.awt.*;

public interface PopupBorder extends Border {

  void setActive(boolean active);

  class Factory {

    private Factory() {
    }

    public static PopupBorder create(boolean active) {
      final BaseBorder border =
        SystemInfo.isMac ? new BaseBorder() : new BaseBorder(true, CaptionPanel.getBorderColor(true), CaptionPanel.getBorderColor(false));
      border.setActive(active);
      return border;
    }
  }

  class BaseBorder implements PopupBorder {

    private boolean myVisible;
    private Color myActiveColor;
    private Color myPassiveColor;

    private boolean myActive;

    protected BaseBorder() {
      this(false, null, null);
    }

    protected BaseBorder(final boolean visible, final Color activeColor, final Color passiveColor) {
      myVisible = visible;
      myActiveColor = activeColor;
      myPassiveColor = passiveColor;
    }

    public void setActive(final boolean active) {
      myActive = active;
    }

    public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
      if (!myVisible) return;

      Color color = myActive ? myActiveColor : myPassiveColor;
      g.setColor(color);
      g.drawRect(x, y, width - 1, height - 1);
    }

    public Insets getBorderInsets(final Component c) {
      return myVisible ? new Insets(1, 1, 1, 1) : new Insets(0, 0, 0, 0);
    }

    public boolean isBorderOpaque() {
      return true;
    }
  }


}
