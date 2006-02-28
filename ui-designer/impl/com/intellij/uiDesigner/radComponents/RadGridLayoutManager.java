/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.core.GridLayoutManager;

import java.awt.*;

/**
 * @author yole
 */
public class RadGridLayoutManager extends RadLayoutManager {
  public String getName() {
    return "GridLayoutManager";
  }

  public LayoutManager createLayout() {
    return new GridLayoutManager(1, 1);
  }
}
