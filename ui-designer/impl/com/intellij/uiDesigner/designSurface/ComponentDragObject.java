/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.radComponents.RadContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author yole
 */
public interface ComponentDragObject {
  int getComponentCount();
  boolean isHGrow();
  boolean isVGrow();
  int getRelativeRow(int componentIndex);
  int getRelativeCol(int componentIndex);
  int getRowSpan(int componentIndex);
  int getColSpan(int componentIndex);
  @Nullable Point getDelta(int componentIndex);
  @NotNull Dimension getInitialSize(final RadContainer targetContainer);
}
