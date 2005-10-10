/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.ui.popup.PopupStep;

import javax.swing.*;

public interface ListPopupStep extends PopupStep {

  Object[] getValues();

  boolean isSelectable(Object value);

  Icon getIconFor(Object aValue);

  String getTextFor(Object value);

  int getDefaultOptionIndex();

}
