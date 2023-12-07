// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.LoaderFactory;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.radComponents.RadContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;


public class ComponentItemDragObject implements ComponentDragObject {
  private final ComponentItem myItem;

  public ComponentItemDragObject(final @NotNull ComponentItem item) {
    myItem = item;
  }

  public ComponentItem getItem() {
    return myItem;
  }

  @Override
  public int getComponentCount() {
    return 1;
  }

  @Override
  public boolean isHGrow() {
    return (myItem.getDefaultConstraints().getHSizePolicy() & GridConstraints.SIZEPOLICY_WANT_GROW) != 0;
  }

  @Override
  public boolean isVGrow() {
    return (myItem.getDefaultConstraints().getVSizePolicy() & GridConstraints.SIZEPOLICY_WANT_GROW) != 0;
  }

  @Override
  public int getRelativeRow(int componentIndex) {
    return 0;
  }

  @Override
  public int getRelativeCol(int componentIndex) {
    return 0;
  }

  @Override
  public int getRowSpan(int componentIndex) {
    return 1;
  }

  @Override
  public int getColSpan(int componentIndex) {
    return 1;
  }

  @Override
  public @Nullable Point getDelta(int componentIndex) {
    return null;
  }

  @Override
  public @NotNull Dimension getInitialSize(final RadContainer targetContainer) {
    final ClassLoader loader = LoaderFactory.getInstance(targetContainer.getProject()).getLoader(targetContainer.getModule());
    return myItem.getInitialSize(targetContainer.getDelegee(), loader);
  }
}
