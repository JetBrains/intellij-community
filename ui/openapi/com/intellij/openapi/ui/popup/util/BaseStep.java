/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui.popup.util;

import com.intellij.openapi.ui.popup.MnemonicNavigationFilter;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.SpeedSearchFilter;
import com.intellij.util.ui.UIUtil;

public abstract class BaseStep<T> implements PopupStep<T>, SpeedSearchFilter<T>, MnemonicNavigationFilter<T> {

  public boolean isSpeedSearchEnabled() {
    return false;
  }

  public boolean isAutoSelectionEnabled() {
    return true;
  }

  public SpeedSearchFilter<T> getSpeedSearchFilter() {
    return this;
  }

  public boolean canBeHidden(T value) {
    return true;
  }

  public String getIndexedString(T value) {
    return getTextFor(value);
  }

  public boolean isMnemonicsNavigationEnabled() {
    return false;
  }

  public int getMnemonicPos(T value) {
    final String text = getTextFor(value);
    int i = text.indexOf("&");
    if (i < 0) {
      i = text.indexOf(UIUtil.MNEMONIC);
    }
    return i;
  }

  public MnemonicNavigationFilter<T> getMnemonicNavigationFilter() {
    return this;
  }
}
