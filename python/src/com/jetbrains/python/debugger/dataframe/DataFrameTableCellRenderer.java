// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.dataframe;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.debugger.containerview.ColoredCellRenderer;
import com.jetbrains.python.debugger.containerview.PyNumericViewUtil;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;


class DataFrameTableCellRenderer extends DefaultTableCellRenderer implements ColoredCellRenderer {


  private boolean myColored = true;

  public DataFrameTableCellRenderer() {
    setHorizontalAlignment(CENTER);
    setHorizontalTextPosition(LEFT);
    setVerticalAlignment(BOTTOM);
  }

  @Override
  public void setColored(boolean colored) {
    myColored = colored;
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value,
                                                 boolean isSelected, boolean hasFocus, int row, int col) {
    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
    if (value != null) {
      setText(value.toString());
    }

    if (!(value instanceof TableValueDescriptor)) {
      return this;
    }

    TableValueDescriptor descriptor = (TableValueDescriptor)value;

    if (hasFocus) {
      this.setBorder(new LineBorder(JBColor.BLUE, 2));
    }

    if (myColored) {
      try {
        double rangedValue = descriptor.getRangedValue();
        if (!Double.isNaN(rangedValue)) {
          this.setBackground(PyNumericViewUtil.rangedValueToColor(rangedValue));
        }
      }
      catch (NumberFormatException ignored) {

      }
    }
    else {
      this.setBackground(new JBColor(UIUtil.getBgFillColor(table), UIUtil.getBgFillColor(table)));
    }


    return this;
  }
}
