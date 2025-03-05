// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.designSurface.selection;

import com.intellij.designer.designSurface.DecorationLayer;
import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.designSurface.tools.ResizeTracker;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class DirectionResizePoint extends ResizePoint {
  protected int myDirection;
  private final Object myType;
  protected double myXSeparator;
  protected double myYSeparator;
  private final @Nls String myDescription;

  public DirectionResizePoint(int direction, Object type, @Nullable @Nls String description) {
    setDirection(direction);
    myType = type;
    myDescription = description;
  }

  public DirectionResizePoint(Color color, Color border, int direction, Object type, @Nullable @Nls String description) {
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