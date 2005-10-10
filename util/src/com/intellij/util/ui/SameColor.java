/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.ui;

import java.awt.*;

/**
 * @author Eugene Belyaev
 */
public class SameColor extends Color {
  public SameColor(int i) {
    super(i, i, i);
  }
}
