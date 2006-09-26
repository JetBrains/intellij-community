/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui.popup;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Interface for a custom step in a list popup.
 *
 * @see ListPopup
 * @see com.intellij.openapi.ui.popup.JBPopupFactory#createListPopup(ListPopupStep)
 * @since 6.0
 */
public interface ListPopupStep<T> extends PopupStep<T> {

  /**
   * Returns the values to be displayed in the list popup.
   *
   * @return the list of values to be displayed in the list popup.
   */
  @NotNull
  List<T> getValues();

  /**
   * Checks if the specified value in the list can be selected.
   *
   * @param value the value to check.
   * @return true if the value can be selected, false otherwise.
   */
  boolean isSelectable(T value);

  /**
   * Returns the icon to display for the specified list item.
   *
   * @param aValue the value for which the icon is requested.
   * @return the icon to display, or null if no icon is necessary.
   */
  @Nullable
  Icon getIconFor(T aValue);

  /**
   * Returns the text to display for the specified list item.
   *
   * @param value the value for which the text is requested.
   * @return the text to display.
   */
  @NotNull
  String getTextFor(T value);

  /**
   * Returns the separator to display above the specified list item.
   *
   * @param value the value for which the separator is requested.
   * @return the separator to display, or null if no separator is necessary.
   */
  @Nullable
  ListSeparator getSeparatorAbove(T value);

  /**
   * Returns the index of the item to be initially selected in the list.
   *
   * @return the index of the item to be initially selected in the list.
   */
  int getDefaultOptionIndex();
}
