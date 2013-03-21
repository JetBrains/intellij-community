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

import com.intellij.designer.designSurface.ScalableComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public abstract class RadVisualComponent extends RadComponent {
  private Component myNativeComponent;
  private final Rectangle myBounds = new Rectangle();

  @Override
  public Rectangle getBounds() {
    return myBounds;
  }

  @Override
  public Rectangle getBounds(Component relativeTo) {
    return fromModel(relativeTo, getBounds());
  }

  @Override
  public Rectangle fromModel(@NotNull Component target, @NotNull Rectangle bounds) {
    if (target != myNativeComponent && myNativeComponent instanceof ScalableComponent) {
      ScalableComponent scalableComponent = (ScalableComponent)myNativeComponent;
      double zoom = scalableComponent.getScale();

      if (zoom != 1) {
        bounds = new Rectangle(bounds);
        bounds.x *= zoom;
        bounds.y *= zoom;
        bounds.width *= zoom;
        bounds.height *= zoom;
      }
    }

    return myNativeComponent == target
           ? new Rectangle(bounds) :
           SwingUtilities.convertRectangle(myNativeComponent, bounds, target);
  }

  @Override
  public Rectangle toModel(@NotNull Component source, @NotNull Rectangle rectangle) {
    Rectangle bounds = myNativeComponent == source
                       ? new Rectangle(rectangle) : SwingUtilities.convertRectangle(source, rectangle, myNativeComponent);

    if (myNativeComponent != source && myNativeComponent instanceof ScalableComponent) {
      ScalableComponent scalableComponent = (ScalableComponent)myNativeComponent;
      double zoom = scalableComponent.getScale();

      if (zoom != 1) {
        bounds = new Rectangle(bounds);
        bounds.x /= zoom;
        bounds.y /= zoom;
        bounds.width /= zoom;
        bounds.height /= zoom;
      }
    }

    return bounds;
  }

  @Override
  public Point fromModel(@NotNull Component target, @NotNull Point point) {
    if (target != myNativeComponent && myNativeComponent instanceof ScalableComponent) {
      ScalableComponent scalableComponent = (ScalableComponent)myNativeComponent;
      double zoom = scalableComponent.getScale();

      if (zoom != 1) {
        point = new Point(point);
        point.x *= zoom;
        point.y *= zoom;
      }
    }

    return myNativeComponent == target
           ? new Point(point) :
           SwingUtilities.convertPoint(myNativeComponent, point, target);
  }

  @Override
  public Point toModel(@NotNull Component source, @NotNull Point point) {
    Point p = myNativeComponent == source
              ? new Point(point) : SwingUtilities.convertPoint(source, point, myNativeComponent);

    if (myNativeComponent != source && myNativeComponent instanceof ScalableComponent) {
      ScalableComponent scalableComponent = (ScalableComponent)myNativeComponent;
      double zoom = scalableComponent.getScale();

      if (zoom != 1) {
        p = new Point(p);
        p.x /= zoom;
        p.y /= zoom;
      }
    }

    return p;
  }

  @Override
  public Dimension fromModel(@NotNull Component target, @NotNull Dimension size) {
    size = new Dimension(size);

    if (target != myNativeComponent && myNativeComponent instanceof ScalableComponent) {
      ScalableComponent scalableComponent = (ScalableComponent)myNativeComponent;
      double zoom = scalableComponent.getScale();

      if (zoom != 1) {
        size.width *= zoom;
        size.height *= zoom;
      }
    }

    return size;
  }

  @Override
  public Dimension toModel(@NotNull Component source, @NotNull Dimension size) {
    size = new Dimension(size);

    if (myNativeComponent != source && myNativeComponent instanceof ScalableComponent) {
      ScalableComponent scalableComponent = (ScalableComponent)myNativeComponent;
      double zoom = scalableComponent.getScale();

      if (zoom != 1) {
        size.width /= zoom;
        size.height /= zoom;
      }
    }

    return size;
  }

  public void setBounds(int x, int y, int width, int height) {
    myBounds.setBounds(x, y, width, height);
  }

  @Override
  public Point convertPoint(Component relativeFrom, int x, int y) {
    Point p = myNativeComponent == relativeFrom ? new Point(x, y) : SwingUtilities.convertPoint(relativeFrom, x, y, myNativeComponent);

    if (myNativeComponent != relativeFrom && myNativeComponent instanceof ScalableComponent) {
      ScalableComponent scalableComponent = (ScalableComponent)myNativeComponent;
      double zoom = scalableComponent.getScale();

      if (zoom != 1) {
        p.x /= zoom;
        p.y /= zoom;
      }
    }

    return p;
  }

  public Component getNativeComponent() {
    return myNativeComponent;
  }

  public void setNativeComponent(Component nativeComponent) {
    myNativeComponent = nativeComponent;
  }
}