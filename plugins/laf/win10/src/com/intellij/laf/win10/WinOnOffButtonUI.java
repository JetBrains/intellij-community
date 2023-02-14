// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.laf.win10;

import com.intellij.ui.components.OnOffButton;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import javax.swing.text.View;
import java.awt.*;

@SuppressWarnings("unused")
public final class WinOnOffButtonUI extends BasicToggleButtonUI {
  private static final Dimension TOGGLE_SIZE = new JBDimension(29, 16);
  private static final Dimension BUTTON_SIZE = new JBDimension(46, 18);
  private static final Border BUTTON_BORDER = JBUI.Borders.empty(1, 6);

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(JComponent c) {
    c.setBorder(BUTTON_BORDER);
    return new WinOnOffButtonUI();
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    Dimension size = new Dimension(BUTTON_SIZE); // Don't scale it twice.
    JBInsets.addTo(size, BUTTON_BORDER.getBorderInsets(c));
    return size;
  }

  @Override
  public Dimension getMaximumSize(JComponent c) {
    return getPreferredSize(c);
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    return getPreferredSize(c);
  }

  @SuppressWarnings("UseJBColor")
  @Override
  public void paint(Graphics g, JComponent c) {
    if (!(c instanceof OnOffButton b)) return;

    Graphics2D g2 = (Graphics2D)g.create();

    try {
      Insets i = c.getInsets();
      Point origin = new Point((c.getWidth() - BUTTON_SIZE.width) / 2 + i.left,
                               (c.getHeight() - BUTTON_SIZE.height) / 2 + i.top);
      Rectangle outerRect = new Rectangle(origin, BUTTON_SIZE);

      // Background
      g2.setColor(new Color(0xadadad));
      g2.fill(outerRect);

      // Fill
      g2.setColor(b.isSelected() ? new Color(0x119bfe) : new Color(0xe3e3e3));

      Point location = new Point((b.isSelected() ? JBUIScale.scale(16) : JBUIScale.scale(1)) + origin.x, JBUIScale.scale(1) + origin.y);
      Rectangle innerRect = new Rectangle(location, TOGGLE_SIZE);
      g2.fill(innerRect);

      Rectangle textRect = new Rectangle();
      Rectangle iconRect = new Rectangle();

      Font f = c.getFont();
      FontMetrics fm = g.getFontMetrics();
      g.setFont(f);

      // Font color set through foreground because paintText uses it to set the graphics color
      b.setForeground(b.isSelected() ? Color.white : Color.black);

      // layout the text and icon
      String text = SwingUtilities.layoutCompoundLabel(
        c, fm, b.isSelected() ? b.getOnText() : b.getOffText(), null,
        b.getVerticalAlignment(), b.getHorizontalAlignment(),
        b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
        innerRect, iconRect, textRect,
        b.getText() == null ? 0 : b.getIconTextGap());

      // Draw the Text
      if (text != null && !text.isEmpty()) {
        View v = (View)c.getClientProperty(BasicHTML.propertyKey);
        if (v != null) {
          v.paint(g2, textRect);
        }
        else {
          paintText(g2, b, textRect, text);
        }
      }
    }
    finally {
      g2.dispose();
    }
  }
}
