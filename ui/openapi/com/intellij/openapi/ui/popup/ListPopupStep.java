/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui.popup;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public interface ListPopupStep<T> extends PopupStep<T> {

  @Nullable
  List<T> getValues();

  boolean isSelectable(T value);

  @Nullable
  Icon getIconFor(T aValue);

  @NotNull
  String getTextFor(T value);

  @Nullable
  ListSeparator getSeparatorAbove(T value);

  int getDefaultOptionIndex();
}
