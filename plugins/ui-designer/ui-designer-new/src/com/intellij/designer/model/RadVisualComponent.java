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
package com.intellij.designer.model;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class RadVisualComponent extends RadComponent {
  protected final List<RadComponent> myChildren = new ArrayList<RadComponent>();

  private Component myNativeComponent;
  private final Rectangle myBounds = new Rectangle();

  @Override
  public List<RadComponent> getChildren() {
    return myChildren;
  }

  @Override
  public Rectangle getBounds() {
    return myBounds;
  }

  @Override
  public Rectangle getBounds(Component relativeTo) {
    return myNativeComponent == relativeTo
           ? new Rectangle(myBounds) :
           SwingUtilities.convertRectangle(myNativeComponent, myBounds, relativeTo);
  }

  public void setBounds(int x, int y, int width, int height) {
    myBounds.setBounds(x, y, width, height);
  }

  @Override
  public Point convertPoint(Component relativeFrom, int x, int y) {
    return myNativeComponent == relativeFrom
           ? myBounds.getLocation() :
           SwingUtilities.convertPoint(relativeFrom, x, y, myNativeComponent);
  }

  public Component getNativeComponent() {
    return myNativeComponent;
  }

  public void setNativeComponent(Component nativeComponent) {
    myNativeComponent = nativeComponent;
  }
}