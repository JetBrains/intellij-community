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
package com.intellij.designer.designSurface.selection;

import com.intellij.designer.designSurface.ComponentDecorator;
import com.intellij.designer.designSurface.DecorationLayer;
import com.intellij.designer.designSurface.tools.DragTracker;
import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.model.RadComponent;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class NonResizeSelectionDecorator extends ComponentDecorator {
  protected final Color myColor;
  private final int myLineWidth;
  private final BasicStroke myStroke;

  public NonResizeSelectionDecorator(Color color, int lineWidth) {
    myColor = color;
    myLineWidth = Math.max(lineWidth, 1);
    myStroke = myLineWidth > 1 ? new BasicStroke(myLineWidth) : null;
  }

  @Override
  public InputTool findTargetTool(DecorationLayer layer, RadComponent component, int x, int y) {
    Rectangle bounds = getBounds(layer, component);
    int lineWidth = Math.max(myLineWidth, 2);

    Rectangle top = new Rectangle(bounds.x, bounds.y, bounds.width, lineWidth);
    Rectangle bottom = new Rectangle(bounds.x, bounds.y + bounds.height - lineWidth, bounds.width, lineWidth);
    Rectangle left = new Rectangle(bounds.x, bounds.y, lineWidth, bounds.height);
    Rectangle right = new Rectangle(bounds.x + bounds.width - lineWidth, bounds.y, lineWidth, bounds.height);

    if (top.contains(x, y) || bottom.contains(x, y) || left.contains(x, y) || right.contains(x, y)) {
      return new DragTracker(component);
    }

    return null;
  }

  @Override
  protected void paint(DecorationLayer layer, Graphics2D g, RadComponent component) {
    g.setColor(myColor);
    if (myStroke != null) {
      g.setStroke(myStroke);
    }

    Rectangle bounds = getBounds(layer, component);
    g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
  }

  protected Rectangle getBounds(DecorationLayer layer, RadComponent component) {
    return component.getBounds(layer);
  }
}