// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner;

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.ApiStatus;

import java.awt.*;

/**
 * Used in design time only. 
 */
@ApiStatus.Internal
public abstract class DesignSpacer extends Spacer{
  protected static final int HANDLE_ATOM_WIDTH = 5;
  protected static final int HANDLE_ATOM_HEIGHT = 3;
  protected static final int HANDLE_ATOM_SPACE = 1;

  protected static final int SPRING_PRERIOD = 4;

  protected static final Color ourColor1 = new JBColor(new Color(8,8,108), Gray._168);
  protected static final Color ourColor2 = new JBColor(new Color(3, 26, 142), Gray._128);
  protected static final Color ourColor3 = new JBColor(Gray._0, Gray._128);
}
