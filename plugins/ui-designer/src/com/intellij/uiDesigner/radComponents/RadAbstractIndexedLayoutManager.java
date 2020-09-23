// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.XmlWriter;

public abstract class RadAbstractIndexedLayoutManager extends RadLayoutManager {
  @Override
  public void addComponentToContainer(final RadContainer container, final RadComponent component, final int index) {
    container.getDelegee().add(component.getDelegee(), index);
  }

  @Override
  public boolean isIndexed() {
    return true;
  }

  @Override
  public void writeChildConstraints(final XmlWriter writer, final RadComponent child) {
  }

  @Override
  public boolean canMoveComponent(RadComponent c, int rowDelta, int colDelta, final int rowSpanDelta, final int colSpanDelta) {
    if (colDelta == 1 || colDelta == -1) {
      int newIndex = c.getParent().indexOfComponent(c) + colDelta;
      return newIndex >= 0 && newIndex < c.getParent().getComponentCount();
    }
    return false;
  }

  @Override
  public void moveComponent(RadComponent c, int rowDelta, int colDelta, final int rowSpanDelta, final int colSpanDelta) {
    final RadContainer container = c.getParent();
    int newIndex = container.indexOfComponent(c) + colDelta;
    container.removeComponent(c);
    container.addComponent(c, newIndex);
  }
}
