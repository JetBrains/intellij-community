/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner;

import com.intellij.uiDesigner.radComponents.RadContainer;

/**
 * @author yole
 */
public class CaptionSelection {
  private RadContainer myContainer;
  private boolean myIsRow;
  private int[] mySelection;
  private int myFocusedIndex;

  public CaptionSelection(final RadContainer container, final boolean isRow, final int[] selection, final int focusedIndex) {
    myContainer = container;
    myIsRow = isRow;
    mySelection = selection;
    myFocusedIndex = focusedIndex;
  }

  public RadContainer getContainer() {
    return myContainer;
  }

  public boolean isRow() {
    return myIsRow;
  }

  public int[] getSelection() {
    return mySelection;
  }

  public int getFocusedIndex() {
    return myFocusedIndex;
  }
}
