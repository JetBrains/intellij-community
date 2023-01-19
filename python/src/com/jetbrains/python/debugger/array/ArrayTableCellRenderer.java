// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.array;

import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.python.debugger.containerview.ColoredCellRenderer;
import com.jetbrains.python.debugger.containerview.PyNumericViewUtil;
import com.jetbrains.python.debugger.dataframe.DataViewCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author amarch
 */
class ArrayTableCellRenderer extends DataViewCellRenderer implements ColoredCellRenderer {
  private static final Logger LOG = Logger.getInstance(ArrayTableCellRenderer.class);
  private double myMin = Double.MIN_VALUE;
  private double myMax = Double.MIN_VALUE;
  private String myComplexMin;
  private String myComplexMax;
  private boolean myColored = true;
  private final String myType;

  ArrayTableCellRenderer(double min, double max, String type) {
    setHorizontalAlignment(LEFT);
    setHorizontalTextPosition(LEFT);
    setVerticalAlignment(CENTER);
    myMin = min;
    myMax = max;
    myType = type;
  }

  @Override
  public void setColored(boolean colored) {
    myColored = colored;
  }

  @Override
  protected void colorize(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    Color background = null;
    if (myMax != myMin) {
      if (myColored && value != null) {
        try {
          String valueStr = value.toString();
          if (!valueStr.isEmpty()) {
            double rangedValue = PyNumericViewUtil.getRangedValue(valueStr, myType, myMin, myMax, myComplexMax, myComplexMin);
            background = PyNumericViewUtil.rangedValueToColor(rangedValue);
          }
        }
        catch (NumberFormatException ignored) {
        }
      }
    }
    this.setBackground(background);
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
