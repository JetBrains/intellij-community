/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.uiDesigner;

import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.openapi.actionSystem.DataKey;

/**
 * @author yole
 */
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
