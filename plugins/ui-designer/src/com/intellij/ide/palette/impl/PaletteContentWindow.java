// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.palette.impl;

import com.intellij.util.ui.JBUI;
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

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    return 20;
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    return 100;
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  @Override
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

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
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

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      return JBUI.emptySize();
    }

    @Override
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

    @Override
    public void removeLayoutComponent(Component comp) {
    }
  }
}
