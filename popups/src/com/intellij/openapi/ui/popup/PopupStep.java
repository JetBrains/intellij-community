/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui.popup;

import org.jetbrains.annotations.Nullable;

public interface PopupStep {

  PopupStep FINAL_CHOICE = null;

  @Nullable
  String getTitle();

  PopupStep onChosen(Object selectedValue);

  boolean hasSubstep(Object selectedValue);

  void canceled();

  boolean isMnemonicsNavigationEnabled();

  MnemonicNavigationFilter getMnemonicNavigationFilter();

  boolean isSpeedSearchEnabled();
  boolean isAutoSelectionEnabled();

  SpeedSearchFilter getSpeedSearchFilter();

}
