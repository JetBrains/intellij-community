// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.radComponents.RadContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;


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
