/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.LoaderFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author yole
 */
public class ComponentItemDragObject implements ComponentDragObject {
  private ComponentItem myItem;

  public ComponentItemDragObject(final ComponentItem item) {
    myItem = item;
  }

  public ComponentItem getItem() {
    return myItem;
  }

  public int getComponentCount() {
    return 1;
  }

  public boolean isHGrow() {
    return (myItem.getDefaultConstraints().getHSizePolicy() & GridConstraints.SIZEPOLICY_WANT_GROW) != 0;
  }

  public boolean isVGrow() {
    return (myItem.getDefaultConstraints().getVSizePolicy() & GridConstraints.SIZEPOLICY_WANT_GROW) != 0;
  }

  public int getRelativeRow(int componentIndex) {
    return 0;
  }

  public int getRelativeCol(int componentIndex) {
    return 0;
  }

  public int getRowSpan(int componentIndex) {
    return 1;
  }

  public int getColSpan(int componentIndex) {
    return 1;
  }

  @Nullable
  public Point getDelta(int componentIndex) {
    return null;
  }

  @NotNull
  public Dimension getInitialSize(final RadContainer targetContainer) {
    final ClassLoader loader = LoaderFactory.getInstance(targetContainer.getProject()).getLoader(targetContainer.getModule());
    return myItem.getInitialSize(targetContainer.getDelegee(), loader);
  }
}
