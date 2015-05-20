/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
      return JBUI.emptySize();
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
