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

import com.intellij.designer.designSurface.DecorationLayer;
import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.designSurface.tools.ResizeTracker;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class DirectionResizePoint extends ResizePoint {
  protected int myDirection;
  final private Object myType;
  protected double myXSeparator;
  protected double myYSeparator;
  private final String myDescription;

  public DirectionResizePoint(int direction, Object type, @Nullable String description) {
    setDirection(direction);
    myType = type;
    myDescription = description;
  }

  public DirectionResizePoint(Color color, Color border, int direction, Object type, @Nullable String description) {
    super(color, border);
    setDirection(direction);
    myType = type;
    myDescription = description;
  }

  private void setDirection(int direction) {
    myDirection = direction;

    int yDirection = myDirection & Position.EAST_WEST;
    if (yDirection == Position.WEST) {
      myXSeparator = 0;
    }
    else if (yDirection == Position.EAST) {
      myXSeparator = 1;
    }
    else {
      myXSeparator = 0.5;
    }

    int xDirection = myDirection & Position.NORTH_SOUTH;
    if (xDirection == Position.NORTH) {
      myYSeparator = 0;
    }
    else if (xDirection == Position.SOUTH) {
      myYSeparator = 1;
    }
    else {
      myYSeparator = 0.5;
    }
  }

  public DirectionResizePoint move(double xSeparator, double ySeparator) {
    myXSeparator = xSeparator;
    myYSeparator = ySeparator;
    return this;
  }

  public int getDirection() {
    return myDirection;
  }

  @Override
  public Object getType() {
    return myType;
  }

  @Override
  protected InputTool createTool(RadComponent component) {
    return new ResizeTracker(myDirection, myType, myDescription);
  }

  @Override
  protected Point getLocation(DecorationLayer layer, RadComponent component) {
    Rectangle bounds = getBounds(layer, component);
    int size = (getSize() + 1) / 2;
    int x = bounds.x + (int)(bounds.width * myXSeparator) - size;
    int y = bounds.y + (int)(bounds.height * myYSeparator) - size;

    return new Point(x, y);
  }

  protected Rectangle getBounds(DecorationLayer layer, RadComponent component) {
    return component.getBounds(layer);
  }
}