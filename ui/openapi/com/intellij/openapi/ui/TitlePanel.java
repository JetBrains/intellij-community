/*
 * @author max
 */
package com.intellij.openapi.ui;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public final class TitlePanel extends JPanel {
  public TitlePanel(String title, String description) {
    super(new BorderLayout());
    JLabel label = new JLabel(title);
    add(label, BorderLayout.NORTH);
    label.setOpaque(false);
    Font font = label.getFont();
    label.setFont(font.deriveFont(Font.BOLD, font.getSize() + 2));
    if (description != null) {
      label.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
      JLabel descriptionLabel = new JLabel(description);
      descriptionLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
      add(descriptionLabel, BorderLayout.CENTER);
    }
    else {
      label.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }
  }

  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2 = (Graphics2D) g;
    int width = getSize().width;
    int height = getSize().height;
    Object oldAntialiasing = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setPaint(new Color(247, 247, 247));
    RoundRectangle2D rect = new RoundRectangle2D.Double(0, 0, width - 1, height - 1, 0, 0);
    g2.fill(rect);
    g2.setPaint(Color.GRAY);
    UIUtil.drawLine(g2, 0, height - 1, width - 1, height - 1);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing);
  }
}