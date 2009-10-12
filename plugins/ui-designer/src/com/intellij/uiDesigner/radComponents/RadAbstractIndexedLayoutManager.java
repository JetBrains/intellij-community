/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 31.10.2006
 * Time: 14:54:37
 */
package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.XmlWriter;

import javax.swing.*;

public abstract class RadAbstractIndexedLayoutManager extends RadLayoutManager {
  public void addComponentToContainer(final RadContainer container, final RadComponent component, final int index) {
    container.getDelegee().add(component.getDelegee(), index);
  }

  @Override
  public boolean isIndexed() {
    return true;
  }

  public void writeChildConstraints(final XmlWriter writer, final RadComponent child) {
  }

  @Override
  public void addSnapshotComponent(final JComponent parent,
                                   final JComponent child,
                                   final RadContainer container,
                                   final RadComponent component) {
    container.addComponent(component);
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
