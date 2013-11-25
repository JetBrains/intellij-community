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

import com.intellij.designer.DesignerBundle;
import com.intellij.designer.model.RadComponent;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class OperationContext {
  public static final String MOVE = "move_children";
  public static final String ADD = "add_children";
  public static final String CREATE = "create_children";
  public static final String PASTE = "paste_children";

  private Object myType;
  private EditableArea myArea;
  private RadComponent myContainer;
  private List<RadComponent> myComponents;
  private InputEvent myInputEvent;
  private int myModifiers;
  private Point myLocation;
  private Point myMoveDelta;
  private Dimension mySizeDelta;
  private int myResizeDirection;

  private boolean myMoveEnabled = true;
  private boolean myAddEnabled = true;

  public OperationContext() {
  }

  public OperationContext(Object type) {
    myType = type;
  }

  public Object getType() {
    return myType;
  }

  public void setType(@Nullable Object type) {
    myType = type;
  }

  public String getMessage() {
    return DesignerBundle.message(myType == null ? "command.tool_operation" : myType.toString());
  }

  public boolean is(Object type) {
    if (myType == type) {
      return true;
    }
    if (myType != null) {
      return myType.equals(type);
    }
    return false;
  }

  public boolean isMove() {
    return is(MOVE);
  }

  public boolean isAdd() {
    return is(ADD);
  }

  public boolean isCreate() {
    return is(CREATE);
  }

  public boolean isPaste() {
    return is(PASTE);
  }

  public boolean isTree() {
    return myArea.isTree();
  }

  public EditableArea getArea() {
    return myArea;
  }

  public void setArea(EditableArea area) {
    myArea = area;
  }

  public RadComponent getContainer() {
    return myContainer;
  }

  public void setContainer(RadComponent container) {
    myContainer = container;
  }

  public List<RadComponent> getComponents() {
    return myComponents;
  }

  public void setComponents(@Nullable List<RadComponent> components) {
    myComponents = components;
  }

  public boolean isMoveEnabled() {
    return myMoveEnabled;
  }

  public void resetMoveAddEnabled() {
    myMoveEnabled = true;
    myAddEnabled = true;
  }

  public void setMoveEnabled(boolean enabled) {
    myMoveEnabled &= enabled;
  }

  public void setAddEnabled(boolean enabled) {
    myAddEnabled &= enabled;
  }

  public boolean isAddEnabled() {
    return myAddEnabled;
  }

  public InputEvent getInputEvent() {
    return myInputEvent;
  }

  public void setInputEvent(InputEvent inputEvent) {
    myInputEvent = inputEvent;
  }

  public int getModifiers() {
    return myModifiers;
  }

  public void setModifiers(int modifiers) {
    myModifiers = modifiers;
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
}