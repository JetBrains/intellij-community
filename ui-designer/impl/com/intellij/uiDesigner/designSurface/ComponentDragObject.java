/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import java.awt.*;

/**
 * @author yole
 */
public interface ComponentDragObject {
  int getComponentCount();
  int getHSizePolicy();
  int getVSizePolicy();
  int getRelativeRow(int componentIndex);
  int getRelativeCol(int componentIndex);
  int getRowSpan(int componentIndex);
  int getColSpan(int componentIndex);
  Point getDelta(int componentIndex);
}
