/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
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
}
