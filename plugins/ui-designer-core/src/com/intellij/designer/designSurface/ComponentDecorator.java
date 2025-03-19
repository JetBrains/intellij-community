// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.designSurface;

import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.model.RadComponent;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public abstract class ComponentDecorator {
  public abstract @Nullable InputTool findTargetTool(DecorationLayer layer, RadComponent component, int x, int y);

  public void decorate(DecorationLayer layer, Graphics2D host, RadComponent component) {
    Graphics2D child = (Graphics2D)host.create();
    try {
      paint(layer, child, component);
    }
    finally {
      child.dispose();
    }
  }

  protected abstract void paint(DecorationLayer layer, Graphics2D g, RadComponent component);
}