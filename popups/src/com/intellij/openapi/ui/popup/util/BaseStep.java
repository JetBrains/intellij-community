/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui.popup.util;

import com.intellij.openapi.ui.popup.MnemonicNavigationFilter;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.SpeedSearchFilter;
import com.intellij.util.ui.UIUtil;

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
    final String text = getTextFor(value);
    int i = text.indexOf("&");
    if (i < 0) {
      i = text.indexOf(UIUtil.MNEMONIC);
    }
    return i;
  }

  public MnemonicNavigationFilter getMnemonicNavigationFilter() {
    return this;
  }
}
