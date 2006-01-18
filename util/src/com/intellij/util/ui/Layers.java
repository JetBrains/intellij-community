package com.intellij.util.ui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class Layers extends JLayeredPane {

  private ArrayList<Component> myComponents = new ArrayList<Component>();

  public Layers() {
    setLayout(new Layout());
  }

  private class Layout implements LayoutManager2 {
    public void addLayoutComponent(Component comp, Object constraints) {
      myComponents.add(comp);
    }

    public float getLayoutAlignmentX(Container target) {
      return 0;
    }

    public float getLayoutAlignmentY(Container target) {
      return 0;
    }

    public void invalidateLayout(Container target) {
    }

    public Dimension maximumLayoutSize(Container target) {
      int maxWidth = 0;
      int maxHeight = 0;
      for (Component each : myComponents) {
        Dimension min = each.getMaximumSize();
        maxWidth = Math.min(maxWidth, min.width);
        maxHeight = Math.min(maxHeight, min.height);
      }
      return new Dimension(maxWidth, maxHeight);
    }

    public void addLayoutComponent(String name, Component comp) {
      myComponents.add(comp);
    }

    public void layoutContainer(Container parent) {
      for (Component each : myComponents) {
        each.setBounds(0, 0, parent.getWidth() - 1, parent.getHeight() - 1);
      }
    }

    public Dimension minimumLayoutSize(Container parent) {
      int minWidth = 0;
      int minHeight = 0;
      for (Component each : myComponents) {
        Dimension min = each.getMinimumSize();
        minWidth = Math.min(minWidth, min.width);
        minHeight = Math.min(minHeight, min.height);
      }
      return new Dimension(minWidth, minHeight);
    }

    public Dimension preferredLayoutSize(Container parent) {
      int prefWidth = 0;
      int prefHeight = 0;
      for (Component each : myComponents) {
        Dimension min = each.getPreferredSize();
        prefWidth = Math.max(prefWidth, min.width);
        prefHeight = Math.max(prefHeight, min.height);
      }
      return new Dimension(prefWidth, prefHeight);
    }

    public void removeLayoutComponent(Component comp) {
      myComponents.remove(comp);
    }
  }

}
