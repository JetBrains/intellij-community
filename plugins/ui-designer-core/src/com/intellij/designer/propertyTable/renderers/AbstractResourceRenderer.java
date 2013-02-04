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
package com.intellij.designer.propertyTable.renderers;

import com.intellij.designer.model.PropertiesContainer;
import com.intellij.designer.model.PropertyContext;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.designer.propertyTable.PropertyTable;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public abstract class AbstractResourceRenderer<T> implements PropertyRenderer {
  protected final ColorIcon myColorIcon = new ColorIcon(10, 9);
  protected final SimpleColoredComponent myColoredComponent = new SimpleColoredComponent() {
    @Override
    protected void doPaintIcon(Graphics2D g, Icon icon, int offset) {
      g.setColor(getBackground());
      g.fillRect(offset, 0, icon.getIconWidth() + getIpad().left + myIconTextGap, getHeight());
      paintIcon(g, icon, offset + getIpad().left);
    }
  };

  @NotNull
  @Override
  public JComponent getComponent(@Nullable PropertiesContainer container,
                                 PropertyContext context,
                                 @Nullable Object value,
                                 boolean selected,
                                 boolean hasFocus) {
    myColoredComponent.clear();
    PropertyTable.updateRenderer(myColoredComponent, selected);
    formatValue((RadComponent)container, (T)value);

    return myColoredComponent;
  }

  protected abstract void formatValue(RadComponent container, T value);

  @Override
  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myColoredComponent);
  }

  protected final static class ColorIcon extends EmptyIcon {
    private final int myColorSize;
    private Color myColor;

    private ColorIcon(int size, int colorSize) {
      super(size, size);
      myColorSize = colorSize;
    }

    public void setColor(Color color) {
      myColor = color;
    }

    @Override
    public void paintIcon(Component component, Graphics g, final int left, final int top) {
      int iconWidth = getIconWidth();
      int iconHeight = getIconHeight();

      SimpleColoredComponent coloredComponent = (SimpleColoredComponent)component;
      g.setColor(component.getBackground());
      g.fillRect(left - coloredComponent.getIpad().left, 0,
                 iconWidth + coloredComponent.getIpad().left + coloredComponent.getIconTextGap(), component.getHeight());

      int x = left + (iconWidth - myColorSize) / 2;
      int y = top + (iconHeight - myColorSize) / 2;

      g.setColor(myColor);
      g.fillRect(x, y, myColorSize, myColorSize);

      g.setColor(Color.BLACK);
      g.drawRect(x, y, myColorSize, myColorSize);
    }
  }
}