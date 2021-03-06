// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.wizard;

import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class BeanPropertyTableCellRenderer extends ColoredTableCellRenderer{
  private final SimpleTextAttributes myAttrs1;
  private final SimpleTextAttributes myAttrs2;
  private final SimpleTextAttributes myAttrs3;

  BeanPropertyTableCellRenderer() {
    myAttrs1 = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
    myAttrs2 = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    myAttrs3 = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, Color.GRAY);
  }

  @Override
  protected void customizeCellRenderer(
    final @NotNull JTable table,
    final Object value,
    final boolean selected,
    final boolean hasFocus,
    final int row,
    final int column
  ) {
    final BeanProperty property = (BeanProperty)value;
    if(property == null){
      append(UIDesignerBundle.message("property.not.defined"), myAttrs2);
    }
    else{
      append(property.myName, myAttrs1);
      append(" ", myAttrs1);

      // Short type name
      final String shortClassName;
      final String packageName;
      final int lastDotIndex = property.myType.lastIndexOf('.');
      if(lastDotIndex != -1){
        shortClassName = property.myType.substring(lastDotIndex + 1);
        packageName = property.myType.substring(0, lastDotIndex);
      }
      else{
        shortClassName = property.myType;
        packageName = null;
      }

      append(shortClassName, myAttrs2);

      if(packageName != null){
        append(" (", myAttrs3);
        append(packageName, myAttrs3);
        append(")", myAttrs3);
      }
    }
  }
}
