/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
    protected void doPaintIcon(@NotNull Graphics2D g, @NotNull Icon icon, int offset) {
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
}