// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.wizard;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

final class BeanPropertyListCellRenderer extends ColoredListCellRenderer{
  private final SimpleTextAttributes myAttrs1;
  private final SimpleTextAttributes myAttrs2;

  BeanPropertyListCellRenderer() {
    myAttrs1 = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
    myAttrs2 = SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

  @Override
  protected void customizeCellRenderer(
    @NotNull final JList list,
    final Object value,
    final int index,
    final boolean selected,
    final boolean hasFocus
  ) {
    if (value instanceof BeanProperty) {
      final BeanProperty property = (BeanProperty)value;
      append(property.myName, myAttrs1);
      append(" ", myAttrs1);
      append(property.myType, myAttrs2);
    }
    else {
      append(UIDesignerBundle.message("property.not.defined"), myAttrs2);
    }
  }
}
