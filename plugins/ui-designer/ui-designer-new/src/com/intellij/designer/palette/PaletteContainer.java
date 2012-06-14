/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.palette;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class PaletteContainer extends JPanel implements Scrollable {
  public PaletteContainer() {
    super(new PaletteContainerLayout());
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

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private static class PaletteContainerLayout implements LayoutManager {
    @Override
    public void layoutContainer(Container parent) {
      int width = parent.getWidth();
      int height = 0;

      for (Component component : parent.getComponents()) {
        if (component instanceof PaletteGroupComponent) {
          PaletteGroupComponent groupComponent = (PaletteGroupComponent)component;
          groupComponent.setLocation(0, height);
          if (groupComponent.isVisible()) {
            int groupHeight = groupComponent.getPreferredSize().height;
            groupComponent.setSize(width, groupHeight);
            height += groupHeight;
          }
          else {
            groupComponent.setSize(0, 0);
          }
          if (groupComponent.isSelected() || !groupComponent.isVisible()) {
            PaletteItemsComponent itemsComponent = groupComponent.getItemsComponent();
            int itemsHeight = itemsComponent.getPreferredSize().height;
            itemsComponent.setBounds(0, height, width, itemsHeight);
            height += itemsHeight;
          }
        }
      }
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      int width = parent.getWidth();
      int height = 0;

      for (Component component : parent.getComponents()) {
        if (component instanceof PaletteGroupComponent) {
          PaletteGroupComponent groupComponent = (PaletteGroupComponent)component;
          height += groupComponent.getHeight();
          if (groupComponent.isSelected()) {
            height += groupComponent.getItemsComponent().getPreferredHeight(width);
          }
        }
      }

      return new Dimension(10, height);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      return new Dimension();
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }
  }
}