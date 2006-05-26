/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.reference.SoftReference;
import com.intellij.openapi.wm.ex.WindowManagerEx;

import java.awt.*;

/**
 * @author yole
 */
public class CachedGridImage {
  private static final Object CACHED_GRID_IMAGE_KEY = new Object();
  private static float[] ourDashes = new float[] { 3.0f, 1.0f };

  private Image myImage;
  private int[] myHorzGridLines;
  private int[] myVertGridLines;
  private int[] myRows;
  private int[] myRowSpans;
  private int[] myCols;
  private int[] myColSpans;

  private CachedGridImage(final RadContainer container) {
    final GraphicsConfiguration graphicsConfiguration =
      WindowManagerEx.getInstanceEx().getFrame(container.getModule().getProject()).getGraphicsConfiguration();
    myImage = graphicsConfiguration.createCompatibleImage(container.getWidth(), container.getHeight(),
                                                          Transparency.BITMASK);
    update(container);
  }

  private void update(final RadContainer container) {
    int count = container.getComponentCount();
    int[] rows = new int[count];
    int[] rowSpans = new int[count];
    int[] cols = new int[count];
    int[] colSpans = new int[count];
    for(int i=0; i<count; i++) {
      GridConstraints c = container.getComponent(i).getConstraints();
      rows [i] = c.getRow();
      rowSpans [i] = c.getRowSpan();
      cols [i] = c.getColumn();
      colSpans [i] = c.getColSpan();
    }
    int[] horzGridLines = container.getGridLayoutManager().getHorizontalGridLines(container);
    int[] vertGridLines = container.getGridLayoutManager().getVerticalGridLines(container);
    if (!arraysEqual(horzGridLines, myHorzGridLines) ||
        !arraysEqual(vertGridLines, myVertGridLines) ||
        !arraysEqual(rows, myRows) ||
        !arraysEqual(rowSpans, myRowSpans) ||
        !arraysEqual(cols, myCols) ||
        !arraysEqual(colSpans, myColSpans)) {
      myHorzGridLines = horzGridLines;
      myVertGridLines = vertGridLines;
      myRows          = rows;
      myRowSpans      = rowSpans;
      myCols          = cols;
      myColSpans      = colSpans;
      repaint(container);
    }
  }

  private void repaint(final RadContainer container) {
    final int width = container.getWidth();
    final int height = container.getHeight();

    Graphics2D g2d = (Graphics2D) myImage.getGraphics();
    try {
      g2d.setComposite(AlphaComposite.Clear);
      g2d.fillRect(0, 0, width, height);

      g2d.setComposite(AlphaComposite.Src);
      if (container.isSelected()) {
        g2d.setColor(Painter.SELECTED_GRID_COLOR);
      }
      else {
        g2d.setColor(Painter.NON_SELECTED_GRID_COLOR);
      }

      g2d.setStroke(new BasicStroke(1.0f, 0, 0, 1.0f, ourDashes, 0.0f));

      for (int i = 1; i < myHorzGridLines.length - 1; i++) {
        final int y = myHorzGridLines [i];
        g2d.drawLine(0, y, width, y);
      }

      // Vertical lines
      for (int i = 1; i < myVertGridLines.length - 1; i++) {
        final int x = myVertGridLines [i];
        g2d.drawLine(x, 0, x, height);
      }

      g2d.setComposite(AlphaComposite.Clear);
      g2d.setStroke(new BasicStroke(1.0f));
      for(RadComponent childComponent: container.getComponents()) {
        final GridConstraints constraints = childComponent.getConstraints();
        if (constraints.getColSpan() > 1) {
          for(int col = constraints.getColumn()+1; col < constraints.getColumn() + constraints.getColSpan(); col++) {
            g2d.drawLine(myVertGridLines [col],
                         myHorzGridLines [constraints.getRow()]+4,
                         myVertGridLines [col],
                         myHorzGridLines [constraints.getRow() + constraints.getRowSpan()]-4);
          }

        }
        if (constraints.getRowSpan() > 1) {
          for(int row = constraints.getRow()+1; row < constraints.getRow() + constraints.getRowSpan(); row++) {
            g2d.drawLine(myVertGridLines [constraints.getColumn()]+4,
                         myHorzGridLines [row],
                         myVertGridLines [constraints.getColumn() + constraints.getColSpan()]-4,
                         myHorzGridLines [row]);
          }
        }
      }
    }
    finally {
      g2d.dispose();
    }
  }

  private static boolean arraysEqual(final int[] newArray, final int[] oldArray) {
    if (oldArray == null || newArray.length != oldArray.length) {
      return false;
    }
    for(int i=0; i<oldArray.length; i++) {
      if (newArray [i] != oldArray [i]) {
        return false;
      }
    }
    return true;
  }

  private boolean sizeEquals(final RadContainer container) {
    return myImage.getWidth(null) == container.getWidth() &&
           myImage.getHeight(null) == container.getHeight();
  }

  private Image getImage() {
    return myImage;
  }

  public static Image getGridImage(final RadContainer container) {
    CachedGridImage gridImage = null;
    SoftReference<CachedGridImage> imageRef = (SoftReference<CachedGridImage>) container.getDelegee().getClientProperty(CACHED_GRID_IMAGE_KEY);
    if (imageRef != null) {
      gridImage = imageRef.get();
    }
    if (gridImage != null && gridImage.sizeEquals(container)) {
      gridImage.update(container);
    }
    else {
      gridImage = new CachedGridImage(container);
      container.getDelegee().putClientProperty(CACHED_GRID_IMAGE_KEY,
                                               new SoftReference<CachedGridImage>(gridImage));
    }
    return gridImage.getImage();
  }
}
