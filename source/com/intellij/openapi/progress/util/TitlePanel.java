/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.progress.util;

import com.intellij.ui.SideBorder;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * @author max
 */
public class TitlePanel extends JPanel {
  private JLabel myLabel;
  private final static Color CNT_ENABLE_COLOR = new Color(0xcacaca);
  private final static Color BND_ENABLE_COLOR = new Color(0xefefef);

  public TitlePanel() {
    super(new BorderLayout());
    myLabel = new JLabel();
    myLabel.setOpaque(false);
    myLabel.setForeground(Color.black);
    myLabel.setHorizontalAlignment(SwingConstants.CENTER);
    myLabel.setBorder(new CompoundBorder(new SideBorder(Color.gray, SideBorder.BOTTOM),
                                         new EmptyBorder(1, 2, 1, 2)));
    add(myLabel, BorderLayout.CENTER);
  }

  public void setText(String titleText) {
    myLabel.setText(titleText);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    final Graphics2D g2d = (Graphics2D) g;
    g2d.setPaint(new GradientPaint(0, 0, BND_ENABLE_COLOR, 0, getHeight(), CNT_ENABLE_COLOR));
    g2d.fillRect(0, 0, getWidth(), getHeight());
  }
}
