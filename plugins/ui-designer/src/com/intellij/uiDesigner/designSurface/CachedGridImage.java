/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
  private static final float[] ourDashes = new float[] { 3.0f, 1.0f };

  private final Image myImage;
  private int[] myHorzGridLines;
  private int[] myVertGridLines;
  private int[] myRows;
  private int[] myRowSpans;
  private int[] myCols;
  private int[] myColSpans;

  private CachedGridImage(final RadContainer container) {
    final GraphicsConfiguration graphicsConfiguration =
      WindowManagerEx.getInstanceEx().getFrame(container.getProject()).getGraphicsConfiguration();
    if (container.getWidth() * container.getHeight() < 4096*4096) {
      myImage = graphicsConfiguration.createCompatibleImage(container.getWidth(), container.getHeight(),
                                                          Transparency.BITMASK);
      update(container);
    }
    else {
      // create fake image for insanely large containers
      myImage = graphicsConfiguration.createCompatibleImage(16, 16,  Transparency.BITMASK);
    }
  }

  private void update(final RadContainer container) {
    if (container.getWidth() * container.getHeight() >= 4096*4096) return;
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

    if (width * height >= 4096*4096) return;
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

      g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f, ourDashes, 0.0f));

      if (myHorzGridLines.length > 0) {
        int lastLine = (container.getDelegee().getHeight() - myHorzGridLines [myHorzGridLines.length-1] > 4)
                       ? myHorzGridLines.length
                       : myHorzGridLines.length-1;
        for (int i = 1; i < lastLine; i++) {
          final int y = myHorzGridLines [i];
          g2d.drawLine(0, y, width, y);
        }
      }

      if (myVertGridLines.length > 0) {
        // Vertical lines
        int lastLine = (container.getDelegee().getWidth() - myVertGridLines [myVertGridLines.length-1] > 4)
                       ? myVertGridLines.length
                       : myVertGridLines.length-1;
        for (int i = 1; i < lastLine; i++) {
          final int x = myVertGridLines [i];
          g2d.drawLine(x, 0, x, height);
        }
      }

      g2d.setComposite(AlphaComposite.Clear);
      g2d.setStroke(new BasicStroke(1.0f));
      for(RadComponent childComponent: container.getComponents()) {
        final GridConstraints constraints = childComponent.getConstraints();
        if (constraints.getColSpan() > 1) {
          for(int col = constraints.getColumn()+1; col < constraints.getColumn() + constraints.getColSpan(); col++) {
            drawVertGridLine(g2d, col, constraints.getRow(), constraints.getRowSpan());
          }

        }
        if (constraints.getRowSpan() > 1) {
          for(int row = constraints.getRow()+1; row < constraints.getRow() + constraints.getRowSpan(); row++) {
            drawHorzGridLine(g2d, row, constraints.getColumn(), constraints.getColSpan());
          }
        }
      }
    }
    finally {
      g2d.dispose();
    }
  }

  private void drawVertGridLine(final Graphics2D g2d, final int col, final int row, final int rowSpan) {
    // protect against invalid constraints
    if (col < 0 || col >= myVertGridLines.length || row < 0 || row+rowSpan >= myHorzGridLines.length) return;
    g2d.drawLine(myVertGridLines [col],
                 myHorzGridLines [row]+4,
                 myVertGridLines [col],
                 myHorzGridLines [row+rowSpan]-4);
  }

  private void drawHorzGridLine(final Graphics2D g2d, final int row, final int col, final int colSpan) {
    // protect against invalid constraints
    if (col < 0 || col+colSpan >= myVertGridLines.length || row < 0 || row >= myHorzGridLines.length) return;
    g2d.drawLine(myVertGridLines [col]+4,
                 myHorzGridLines [row],
                 myVertGridLines [col + colSpan]-4,
                 myHorzGridLines [row]);
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
    //noinspection unchecked
    SoftReference<CachedGridImage> imageRef = (SoftReference<CachedGridImage>) container.getDelegee().getClientProperty(CACHED_GRID_IMAGE_KEY);
    CachedGridImage gridImage = SoftReference.dereference(imageRef);
    if (gridImage != null && gridImage.sizeEquals(container)) {
      gridImage.update(container);
    }
    else {
      gridImage = new CachedGridImage(container);
      container.getDelegee().putClientProperty(CACHED_GRID_IMAGE_KEY,
                                               new SoftReference<>(gridImage));
    }
    return gridImage.getImage();
  }
}
