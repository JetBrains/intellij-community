package com.intellij.util.ui;

import java.awt.*;

public abstract class AbstractLayoutManager implements LayoutManager2 {


  public void addLayoutComponent(final Component comp, final Object constraints) {
  }

  public Dimension maximumLayoutSize(final Container target) {
    return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
  }

  public float getLayoutAlignmentX(final Container target) {
    return 0;
  }

  public float getLayoutAlignmentY(final Container target) {
    return 0;
  }

  public void invalidateLayout(final Container target) {
  }

  public void addLayoutComponent(final String name, final Component comp) {
  }

  public void removeLayoutComponent(final Component comp) {
  }

  public Dimension minimumLayoutSize(final Container parent) {
    return new Dimension(0, 0);
  }

}
