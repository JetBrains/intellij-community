/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.refactoring.classes.ui;

import com.intellij.openapi.util.Iconable;
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
  public static String getClassText(PyClass aClass) {
    return aClass.getName();
  }
}
