/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui.popup;

import javax.swing.*;
import java.util.List;

public interface ListPopupStep<T> extends PopupStep<T> {

  List<T> getValues();

  boolean isSelectable(T value);

  Icon getIconFor(T aValue);

  String getTextFor(T value);

  ListSeparator getSeparatorAbove(T value);

  int getDefaultOptionIndex();
}
