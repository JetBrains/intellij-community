/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui.popup.util;

import com.intellij.openapi.ui.popup.MnemonicNavigationFilter;
import com.intellij.openapi.ui.popup.SpeedSearchFilter;
import com.intellij.openapi.ui.popup.PopupStep;

public abstract class BaseStep implements PopupStep, SpeedSearchFilter, MnemonicNavigationFilter {

  public boolean isSpeedSearchEnabled() {
    return false;
  }

  public boolean isAutoSelectionEnabled() {
    return true;
  }

  public SpeedSearchFilter getSpeedSearchFilter() {
    return this;
  }

  public boolean canBeHidden(Object value) {
    return true;
  }

  public String getIndexedString(Object value) {
    return getTextFor(value);
  }

  public boolean isMnemonicsNavigationEnabled() {
    return false;
  }

  public int getMnemonicPos(Object value) {
    return value.toString().indexOf("&");
  }

  public MnemonicNavigationFilter getMnemonicNavigationFilter() {
    return this;
  }
}
