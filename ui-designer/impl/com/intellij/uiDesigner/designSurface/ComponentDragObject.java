/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
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
  Point getDelta(int componentIndex);
  @NotNull Dimension getInitialSize(final JComponent parent);
}
