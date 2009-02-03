package com.intellij.openapi.roots.ui.componentsList.components;

import javax.swing.*;
import java.awt.*;

public class ScrollablePanel extends JPanel implements Scrollable {
  private int myUnitHeight = -1;
  private final int myUnitWidth = 10;

  public ScrollablePanel(LayoutManager layout) {
    super(layout);
  }

  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  public void addNotify() {
    super.addNotify();
    final FontMetrics fontMetrics = getFontMetrics(getFont());
    if (myUnitHeight < 0) {
      myUnitHeight = fontMetrics.getMaxAscent() + fontMetrics.getMaxDescent();
    }
  }

  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    if (orientation == SwingConstants.HORIZONTAL) {
      return myUnitWidth;
    }
    else {
      return myUnitHeight;
    }
  }

  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    if (orientation == SwingConstants.HORIZONTAL) {
      return visibleRect.width;
    }
    return visibleRect.height;
  }

  public boolean getScrollableTracksViewportHeight() {
    return false;
  }
}
