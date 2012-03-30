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
import com.intellij.designer.model.RadComponent;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class ResizeSelectionDecorator extends NonResizeSelectionDecorator {
  private final ResizePoint[] myPoints;

  public ResizeSelectionDecorator(Color color, int lineWidth, ResizePoint... points) {
    super(color, lineWidth);
    myPoints = points;
  }

  @Override
  public InputTool findTargetTool(DecorationLayer layer, RadComponent component, int x, int y) {
    for (ResizePoint point : myPoints) {
      if (visible(component, point)) {
        InputTool tracker = point.findTargetTool(layer, component, x, y);
        if (tracker != null) {
          return tracker;
        }
      }
    }

    return super.findTargetTool(layer, component, x, y);
  }

  @Override
  public void decorate(DecorationLayer layer, Graphics2D g, RadComponent component) {
    super.decorate(layer, g, component);

    for (ResizePoint point : myPoints) {
      if (visible(component, point)) {
        point.decorate(layer, g, component);
      }
    }
  }

  protected boolean visible(RadComponent component, ResizePoint point) {
    return true;
  }
}