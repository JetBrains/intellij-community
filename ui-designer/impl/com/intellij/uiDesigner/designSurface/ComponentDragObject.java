/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

/**
 * @author yole
 */
public interface ComponentDragObject {
  int getComponentCount();
  int getDragRelativeColumn();
  int getHSizePolicy();
  int getVSizePolicy();
}
