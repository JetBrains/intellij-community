package com.intellij.debugger.ui.tree.actions;

import com.intellij.debugger.settings.NodeRendererSettings;


/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class ShowAllAsDecimal extends ShowAllAs{
  public ShowAllAsDecimal() {
    super(NodeRendererSettings.getInstance().getPrimitiveRenderer());
  }
}
