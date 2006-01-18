/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui.popup;

import org.jetbrains.annotations.Nullable;

public interface PopupStep<T> {
  PopupStep FINAL_CHOICE = null;

  @Nullable
  String getTitle();

  @Nullable
  PopupStep onChosen(T selectedValue, final boolean finalChoice);

  boolean hasSubstep(T selectedValue);

  void canceled();

  boolean isMnemonicsNavigationEnabled();

  @Nullable
  MnemonicNavigationFilter<T> getMnemonicNavigationFilter();

  boolean isSpeedSearchEnabled();
  boolean isAutoSelectionEnabled();

  @Nullable
  SpeedSearchFilter<T> getSpeedSearchFilter();
}
