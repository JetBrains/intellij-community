// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner;

import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.openapi.actionSystem.DataKey;


public class CaptionSelection {
  private final RadContainer myContainer;
  private final boolean myIsRow;
  private final int[] mySelection;
  private final int myFocusedIndex;

  public static final DataKey<CaptionSelection> DATA_KEY = DataKey.create(CaptionSelection.class.getName());

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
