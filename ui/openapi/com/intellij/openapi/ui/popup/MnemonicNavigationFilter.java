/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui.popup;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface MnemonicNavigationFilter<T> {

  int getMnemonicPos(T value);

  String getTextFor(T value);

  @NotNull
  List<T> getValues();
}
