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
package com.jetbrains.python.debugger.array;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.debugger.containerview.ColoredCellRenderer;
import com.jetbrains.python.debugger.containerview.PyNumericViewUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * @author amarch
 */
class ArrayTableCellRenderer extends DefaultTableCellRenderer implements ColoredCellRenderer {
  private static final Logger LOG = Logger.getInstance(ArrayTableCellRenderer.class);
  private double myMin = Double.MIN_VALUE;
  private double myMax = Double.MIN_VALUE;
  private String myComplexMin;
  private String myComplexMax;
  private boolean myColored = true;
  private final String myType;

  public ArrayTableCellRenderer(double min, double max, String type) {
    setHorizontalAlignment(CENTER);
    setHorizontalTextPosition(LEFT);
    setVerticalAlignment(BOTTOM);
    myMin = min;
    myMax = max;
    myType = type;
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

    if (hasFocus) {
      this.setBorder(new LineBorder(JBColor.BLUE, 2));
    }

    if (myMax != myMin) {
      if (myColored && value != null) {
        try {
          double rangedValue = PyNumericViewUtil.getRangedValue(value.toString(), myType, myMin, myMax, myComplexMax, myComplexMin);
          this.setBackground(PyNumericViewUtil.rangedValueToColor(rangedValue));
        }
        catch (NumberFormatException ignored) {
        }
      }
      else {
        this.setBackground(UIUtil.getBgFillColor(table));
      }
    }

    return this;
  }

  public void setMin(double min) {
    myMin = min;
  }

  public void setMax(double max) {
    myMax = max;
  }

  public double getMin() {
    return myMin;
  }

  public double getMax() {
    return myMax;
  }

  public void fillColorRange(@NotNull String minValue, @NotNull String maxValue) {
    if ("c".equals(myType)) {
      myMin = 0;
      myMax = 1;
      myComplexMin = minValue;
      myComplexMax = maxValue;
      return;
    }
    if ("b".equals(myType)) {
      myMin = "True".equals(minValue) ? 1 : 0;
      myMax = "True".equals(maxValue) ? 1 : 0;
      return;
    }
    try {
      myMin = Double.parseDouble(minValue);
      myMax = Double.parseDouble(maxValue);
    }
    catch (NumberFormatException e) {
      LOG.error(String.format("Wrong bounds for '%s' type: minValue = %s, maxValue = %s", myType, minValue, maxValue));
    }
  }
}
