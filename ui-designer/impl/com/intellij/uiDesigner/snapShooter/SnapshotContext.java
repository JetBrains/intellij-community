/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.snapShooter;

import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.uiDesigner.FormEditingUtil;

/**
 * @author yole
 */
public class SnapshotContext {
  private Palette myPalette;
  private RadRootContainer myRootContainer;

  public SnapshotContext() {
    myPalette = new Palette(null);
    myRootContainer = new RadRootContainer(null, "1");
  }

  public RadRootContainer getRootContainer() {
    return myRootContainer;
  }

  public Palette getPalette() {
    return myPalette;
  }

  public String newId() {
    return FormEditingUtil.generateId(myRootContainer);
  }
}
