/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.palette.impl;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class PaletteContentWindow extends JPanel implements Scrollable {
  public PaletteContentWindow() {
    setLayout(new PaletteLayoutManager());
  }

  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    return 20;
  }

  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    return 100;
  }

  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  public boolean getScrollableTracksViewportHeight() {
    return false;
  }

  @Nullable PaletteGroupHeader getLastGroupHeader() {
    PaletteGroupHeader result = null;
    for(Component comp: getComponents()) {
      if (comp instanceof PaletteGroupHeader) {
        result = (PaletteGroupHeader) comp;
      }
    }
    return result;
  }

  private static class PaletteLayoutManager implements LayoutManager {

    public void addLayoutComponent(String name, Component comp) {
    }

    public void layoutContainer(Container parent) {
      int width = parent.getWidth();

      int height = 0;
      for(Component c: parent.getComponents()) {
        if (c instanceof PaletteGroupHeader) {
          PaletteGroupHeader groupHeader = (PaletteGroupHeader) c;
          groupHeader.setLocation(0, height);
          if (groupHeader.isVisible()) {
            groupHeader.setSize(width, groupHeader.getPreferredSize().height);
            height += groupHeader.getPreferredSize().height;
          }
          else {
            groupHeader.setSize(0, 0);
          }
          if (groupHeader.isSelected() || !groupHeader.isVisible()) {
            PaletteComponentList componentList = groupHeader.getComponentList();
            componentList.setSize(width, componentList.getPreferredSize().height);
            componentList.setLocation(0, height);
            height += componentList.getHeight();
          }
        }
      }
    }

    public Dimension minimumLayoutSize(Container parent) {
      return new Dimension(0, 0);
    }

    public Dimension preferredLayoutSize(Container parent) {
      int height = 0;
      int width = parent.getWidth();
      for(Component c: parent.getComponents()) {
        if (c instanceof PaletteGroupHeader) {
          PaletteGroupHeader groupHeader = (PaletteGroupHeader) c;
          height += groupHeader.getHeight();
          if (groupHeader.isSelected()) {
            height += groupHeader.getComponentList().getPreferredHeight(width);
          }
        }
      }
      return new Dimension(10 /* not used - tracks viewports width*/, height);
    }

    public void removeLayoutComponent(Component comp) {
    }
  }
}
