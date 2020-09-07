// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.classes.ui;

import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsSafe;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Dennis.Ushakov
 */
public class PyClassCellRenderer extends DefaultListCellRenderer {
  private final boolean myShowReadOnly;
  public PyClassCellRenderer() {
    setOpaque(true);
    myShowReadOnly = true;
  }

  public PyClassCellRenderer(boolean showReadOnly) {
    setOpaque(true);
    myShowReadOnly = showReadOnly;
  }

  @Override
  public Component getListCellRendererComponent(
          JList list,
          Object value,
          int index,
          boolean isSelected,
          boolean cellHasFocus) {
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

    return customizeRenderer(value, myShowReadOnly);
  }

  public JLabel customizeRenderer(final Object value, final boolean showReadOnly) {
    PyClass aClass = (PyClass) value;
    setText(getClassText(aClass));

    int flags = Iconable.ICON_FLAG_VISIBILITY;
    if (showReadOnly) {
      flags |= Iconable.ICON_FLAG_READ_STATUS;
    }
    Icon icon = aClass.getIcon(flags);
    if(icon != null) {
      setIcon(icon);
    }
    return this;
  }

  @Nullable
  public static @NlsSafe String getClassText(PyClass aClass) {
    return aClass.getName();
  }
}
