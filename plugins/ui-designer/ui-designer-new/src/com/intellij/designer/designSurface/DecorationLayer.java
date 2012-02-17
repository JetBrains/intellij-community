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
package com.intellij.designer.designSurface;

import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.model.RadComponent;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class DecorationLayer extends JComponent {
  private final EditableArea myArea;

  public DecorationLayer(EditableArea area) {
    myArea = area;
  }

  @Override
  public void paint(Graphics g) {
    painSelection((Graphics2D)g);
  }

  @Nullable
  public InputTool findTargetTool(int x, int y) {
    for (RadComponent component : myArea.getSelection()) {
      ComponentDecorator decorator = getDecorator(component);
      InputTool tracker = decorator.findTargetTool(this, component, x, y);
      if (tracker != null) {
        return tracker;
      }
    }
    return null;
  }

  private void painSelection(Graphics2D g) {
    for (RadComponent component : myArea.getSelection()) {
      ComponentDecorator decorator = getDecorator(component);
      // TODO: set component clipping
      decorator.decorate(this, g, component);
      // TODO: restore Graphics state: color, font, stroke etc.
    }
  }

  private ComponentDecorator getDecorator(RadComponent component) {
    RadComponent parent = component.getParent();
    if (parent == null) {
      return myArea.getRootSelectionDecorator();
    }
    return parent.getLayout().getChildSelectionDecorator(component);
  }

  public Rectangle getComponentBounds(RadComponent component) {
    Rectangle bounds = component.getBounds();
    Point location = component.convertPoint(bounds.x, bounds.y, this);
    return new Rectangle(location.x, location.y, bounds.width, bounds.height);
  }
}