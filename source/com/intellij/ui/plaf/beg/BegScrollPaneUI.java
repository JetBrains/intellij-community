package com.intellij.ui.plaf.beg;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalScrollBarUI;
import javax.swing.plaf.metal.MetalScrollPaneUI;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author Eugene Belyaev
 */
public class BegScrollPaneUI extends MetalScrollPaneUI {
  public static ComponentUI createUI(JComponent x) {
    return new BegScrollPaneUI();
  }

  /**
   * If the border of the scrollpane is an instance of
   * <code>MetalBorders.ScrollPaneBorder</code>, the client property
   * <code>FREE_STANDING_PROP</code> of the scrollbars
   * is set to false, otherwise it is set to true.
   */
  private void updateScrollbarsFreeStanding() {
    if (scrollpane == null) {
      return;
    }
    Object value = Boolean.FALSE;
    scrollpane.getHorizontalScrollBar().putClientProperty
        (MetalScrollBarUI.FREE_STANDING_PROP, value);
    scrollpane.getVerticalScrollBar().putClientProperty
        (MetalScrollBarUI.FREE_STANDING_PROP, value);
  }

  protected PropertyChangeListener createScrollBarSwapListener() {
    return new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent e) {
        String propertyName = e.getPropertyName();
        if (propertyName.equals("verticalScrollBar") ||
            propertyName.equals("horizontalScrollBar")) {
          ((JScrollBar) e.getOldValue()).putClientProperty(MetalScrollBarUI.FREE_STANDING_PROP,
              null);
          ((JScrollBar) e.getNewValue()).putClientProperty(MetalScrollBarUI.FREE_STANDING_PROP,
              Boolean.FALSE);
        }
        else if ("border".equals(propertyName)) {
          updateScrollbarsFreeStanding();
        }
      }
    };
  }
}
