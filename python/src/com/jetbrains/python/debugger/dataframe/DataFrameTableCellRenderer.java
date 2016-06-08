/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

  public void setColored(boolean colored) {
    myColored = colored;
  }

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
