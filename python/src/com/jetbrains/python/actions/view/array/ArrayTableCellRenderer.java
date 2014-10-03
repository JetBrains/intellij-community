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
package com.jetbrains.python.actions.view.array;

import com.intellij.openapi.util.Pair;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author amarch
 */
class ArrayTableCellRenderer extends DefaultTableCellRenderer {

  private double myMin = Double.MIN_VALUE;
  private double myMax = Double.MIN_VALUE;
  private String myComplexMin;
  private String myComplexMax;
  private boolean colored = true;
  private String myType;

  private static final Pattern PY_COMPLEX_NUMBER = Pattern.compile("([+-]?[.\\d^j]*)([+-]?[.\\d]*j)?");

  public ArrayTableCellRenderer(double min, double max, String type) {
    setHorizontalAlignment(SwingConstants.CENTER);
    myMin = min;
    myMax = max;
    myType = type;
  }

  public void setColored(boolean colored) {
    this.colored = colored;
  }

  public Component getTableCellRendererComponent(JTable table, Object value,
                                                 boolean isSelected, boolean hasFocus, int rowIndex, int vColIndex) {
    if (value != null) {
      setText(value.toString());
    }

    if (myMax != myMin) {
      if (colored && value != null) {
        try {
          double rangedValue = getRangedValue(value.toString());
          this.setBackground(
            new JBColor(new Color((int)Math.round(255 * rangedValue), 0, (int)Math.round(255 * (1 - rangedValue)), 130),
                        new Color(0, 0, 0, 0)));
        }
        catch (NumberFormatException ignored) {
        }
      }
      else {
        this.setBackground(new JBColor(new Color(255, 255, 255, 0), new Color(255, 255, 255, 0)));
      }
    }

    return this;
  }

  /**
   * @return double presentation from [0:1] range
   */
  private double getRangedValue(String value) {
    if ("iuf".contains(myType)) {
      return (Double.parseDouble(value) - myMin) / (myMax - myMin);
    }
    else if ("b".equals(myType)) {
      return value.equals("True") ? 1 : 0;
    }
    else if ("c".equals(myType)) {
      return getComplexRangedValue(value);
    }
    return 0;
  }

  /**
   * type complex128 in numpy is compared by next rule:
   * A + Bj > C +Dj if A > C or A == C and B > D
   */
  private double getComplexRangedValue(String value) {
    Pair<Double, Double> med = parsePyComplex(value);
    Pair<Double, Double> max = parsePyComplex(myComplexMax);
    Pair<Double, Double> min = parsePyComplex(myComplexMin);
    double range = (med.first - min.first) / (max.first - min.first);
    if (max.first.equals(min.first)) {
      range = (med.second - min.second) / (max.second - min.second);
    }
    return range;
  }

  private static Pair<Double, Double> parsePyComplex(@NotNull String pyComplexValue) {
    if (pyComplexValue.startsWith("(") && pyComplexValue.endsWith(")")) {
      pyComplexValue = pyComplexValue.substring(1, pyComplexValue.length() - 1);
    }
    Matcher matcher = PY_COMPLEX_NUMBER.matcher(pyComplexValue);
    if (matcher.matches()) {
      String real = matcher.group(1);
      String imag = matcher.group(2);
      if (real.contains("j") && imag == null) {
        return new Pair(new Double(0.0), Double.parseDouble(real.substring(0, real.length() - 1)));
      }
      else {
        return new Pair(Double.parseDouble(real), Double.parseDouble(imag.substring(0, imag.length() - 1)));
      }
    }
    else {
      throw new IllegalArgumentException("Not a valid python complex value: " + pyComplexValue);
    }
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

  public void setComplexMin(String complexMin) {
    myComplexMin = complexMin;
  }

  public void setComplexMax(String complexMax) {
    myComplexMax = complexMax;
  }
}
