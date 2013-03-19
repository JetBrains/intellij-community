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
import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.model.RadComponent;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public abstract class ResizePoint extends ComponentDecorator {
  private static final int DEFAULT_SIZE = 5;
  private final Color myColor;
  private final Color myBorder;

  public ResizePoint() {
    this(Color.GREEN, Color.BLACK);
  }

  public ResizePoint(Color color, Color border) {
    myColor = color;
    myBorder = border;
  }

  @Override
  public InputTool findTargetTool(DecorationLayer layer, RadComponent component, int x, int y) {
    Point location = getLocation(layer, component);
    Rectangle bounds = new Rectangle(location.x, location.y, getSize(), getSize());

    int neighborhood = getNeighborhoodSize();
    if (neighborhood > 0) {
      bounds.grow(neighborhood, neighborhood);
    }

    if (bounds.contains(x, y)) {
      return createTool(component);
    }

    return null;
  }

  @Override
  protected void paint(DecorationLayer layer, Graphics2D g, RadComponent component) {
    Point location = getLocation(layer, component);

    g.setColor(myColor);
    g.fillRect(location.x, location.y, getSize(), getSize());

    g.setColor(myBorder);
    g.drawRect(location.x, location.y, getSize(), getSize());
  }

  public abstract Object getType();

  protected abstract InputTool createTool(RadComponent component);

  protected abstract Point getLocation(DecorationLayer layer, RadComponent component);

  protected int getSize() {
    return DEFAULT_SIZE;
  }

  /**
   * Additional space range around the resize point that will also be considered a match.
   * This lets you get near but not necessarily right on top of the resize point in order
   * to act on it.
   */
  protected int getNeighborhoodSize() {
    return 0;
  }
}