/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.options.ex;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.Kernel;
import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: 10-Feb-2006
 */
public class GlassPanel extends JComponent {
  private Set<JComponent> myLightComponents = new HashSet<JComponent>();

  protected RenderingHints myHints;

  private JComponent myPanel;


  public GlassPanel(JComponent containingPanel) {
    myHints = new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    myHints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    myHints.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    myPanel = containingPanel;
    setVisible(false);
  }

  public void paintComponent(Graphics g) {
    paintSpotlights(g);
  }

  protected void paintSpotlights(Graphics g) {
    if (myLightComponents.size() > 0) {
      int width = getWidth();
      int height = getHeight();

      Rectangle2D screen = new Rectangle2D.Double(0, 0, width, height);
      final Rectangle visibleRect = myPanel.getVisibleRect();
      final Point leftPoint = SwingUtilities.convertPoint(myPanel, new Point(visibleRect.x, visibleRect.y), this);
      Area innerPanel = new Area(new Rectangle2D.Double(leftPoint.x, leftPoint.y, visibleRect.width, visibleRect.height));
      Area mask = new Area(screen);

      for (JComponent lightComponent : myLightComponents) {
        if (!lightComponent.isShowing()) continue;
        final Point panelPoint = SwingUtilities.convertPoint(lightComponent, new Point(0, 0), this);
        final int x = panelPoint.x;
        final int y = panelPoint.y;
        final Area area = new Area(new Ellipse2D.Double(x, y, lightComponent.getWidth(), lightComponent.getHeight()));
        area.intersect(innerPanel);
        mask.subtract(area);
      }

      Graphics2D g2 = (Graphics2D)g;
      Color shieldColor = new Color(0.0f, 0.0f, 0.0f, 0.2f);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(shieldColor);
      g2.fill(mask);
    }
  }

  protected static Kernel getBlurKernel(int blurSize) {
    if (blurSize <= 0) return null;

    int size = blurSize * blurSize;
    float coeff = 1.0f / size;
    float[] kernelData = new float[size];

    for (int i = 0; i < size; i ++) {
      kernelData[i] = coeff;
    }

    return new Kernel(blurSize, blurSize, kernelData);
  }


  public static double getArea(JComponent component) {
    return Math.PI * component.getWidth() * component.getHeight() / 4.0;
  }

  public void addSpotlight(final JComponent component) {
    myLightComponents.add(component);
    setVisible(true);
  }

  public void removeSpotlight(final JComponent component){
    myLightComponents.remove(component);
  }

  public void clear() {
    myLightComponents.clear();
    setVisible(false);
  }
}
