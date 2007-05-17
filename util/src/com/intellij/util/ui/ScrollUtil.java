package com.intellij.util.ui;

import javax.swing.*;
import java.awt.*;

/**
 * @author mike
 */
public class ScrollUtil {
  private ScrollUtil() {
  }

  public static void center(JComponent c, Rectangle r) {
    center(c, r, false);
  }

  public static void center(JComponent c, Rectangle r, boolean withInsets) {
    Rectangle visible = c.getVisibleRect();

    visible.x = r.x - (visible.width - r.width) / 2;
    visible.y = r.y - (visible.height - r.height) / 2;

    Rectangle bounds = c.getBounds();
    Insets i = withInsets ? new Insets(0, 0, 0, 0) : c.getInsets();
    bounds.x = i.left;
    bounds.y = i.top;
    bounds.width -= i.left + i.right;
    bounds.height -= i.top + i.bottom;

    if (visible.x < bounds.x) visible.x = bounds.x;

    if (visible.x + visible.width > bounds.x + bounds.width) visible.x = bounds.x + bounds.width - visible.width;

    if (visible.y < bounds.y) visible.y = bounds.y;

    if (visible.y + visible.height > bounds.y + bounds.height) visible.y = bounds.y + bounds.height - visible.height;

    c.scrollRectToVisible(visible);
  }

  public enum ScrollBias {
    /**
     * take the policy of the viewport
     */
    VIEWPORT,
    UNCHANGED,      // don't scroll if it fills the visible area, otherwise take the policy of the viewport
    FIRST,          // scroll the first part of the region into view
    CENTER,         // center the region
    LAST           // scroll the last part of the region into view
  }

  public static void scroll(JComponent c, Rectangle r, ScrollBias horizontalBias, ScrollBias verticalBias) {
    Rectangle visible = c.getVisibleRect();
    Rectangle dest = new Rectangle(r);

    if (dest.width > visible.width) {
      if (horizontalBias == ScrollBias.VIEWPORT) {
        // leave as is
      }
      else if (horizontalBias == ScrollBias.UNCHANGED) {
        if (dest.x <= visible.x && dest.x + dest.width >= visible.x + visible.width) {
          dest.width = visible.width;
        }
      }
      else {
        if (horizontalBias == ScrollBias.CENTER) {
          dest.x += (dest.width - visible.width) / 2;
        }
        else if (horizontalBias == ScrollBias.LAST) dest.x += dest.width - visible.width;

        dest.width = visible.width;
      }
    }

    if (dest.height > visible.height) {
      // same code as above in the other direction
    }

    if (!visible.contains(dest)) c.scrollRectToVisible(dest);
  }
}
