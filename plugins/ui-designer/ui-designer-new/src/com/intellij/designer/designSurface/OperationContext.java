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

import com.intellij.designer.model.RadComponent;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class OperationContext {
  private final Object myType;
  private EditableArea myArea;
  private List<RadComponent> myComponents;
  private InputEvent myInputEvent;
  private Point myLocation;
  private Point myMoveDelta;
  private Dimension mySizeDelta;
  private int myResizeDirection;
  private Object myNewObject;

  public OperationContext(Object type) {
    myType = type;
  }

  public Object getType() {
    return myType;
  }

  public boolean is(Object type) {
    return type == null ? myType == null : type.equals(myType);
  }

  public EditableArea getArea() {
    return myArea;
  }

  public void setArea(EditableArea area) {
    myArea = area;
  }

  public List<RadComponent> getComponents() {
    return myComponents;
  }

  public void setComponents(List<RadComponent> components) {
    myComponents = components;
  }

  public InputEvent getInputEvent() {
    return myInputEvent;
  }

  public void setInputEvent(InputEvent inputEvent) {
    myInputEvent = inputEvent;
  }

  public Point getLocation() {
    return myLocation;
  }

  public void setLocation(Point location) {
    myLocation = location;
  }

  public Point getMoveDelta() {
    return myMoveDelta;
  }

  public void setMoveDelta(Point moveDelta) {
    myMoveDelta = moveDelta;
  }

  public Dimension getSizeDelta() {
    return mySizeDelta;
  }

  public void setSizeDelta(Dimension sizeDelta) {
    mySizeDelta = sizeDelta;
  }

  public Rectangle getTransformedRectangle(Rectangle r) {
    return new Rectangle(r.x + myMoveDelta.x, r.y + myMoveDelta.y, r.width + mySizeDelta.width, r.height + mySizeDelta.height);
  }

  public int getResizeDirection() {
    return myResizeDirection;
  }

  public void setResizeDirection(int resizeDirection) {
    myResizeDirection = resizeDirection;
  }

  public Object getNewObject() {
    return myNewObject;
  }

  public void setNewObject(Object newObject) {
    myNewObject = newObject;
  }
}