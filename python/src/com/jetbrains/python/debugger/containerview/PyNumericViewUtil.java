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
package com.jetbrains.python.debugger.containerview;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Yuli Fiterman on 5/10/2016.
 */
public class PyNumericViewUtil {
  private static final Pattern PY_COMPLEX_NUMBER = Pattern.compile("([+-]?[.\\d^j]*)([+-]?[e.\\d]*j)?");

  /**
   * @return double presentation from [0:1] range
   */
  public static double getRangedValue(String value, String type, double min, double max, String complexMax, String complexMin) {
    if ("iuf".contains(type)) {
      return (Double.parseDouble(value) - min) / (max - min);
    }
    else if ("b".equals(type)) {
      return value.equals("True") ? 1 : 0;
    }
    else if ("c".equals(type)) {
      return getComplexRangedValue(value, complexMax, complexMin);
    }
    return 0;
  }

  public static Color rangedValueToColor(double rangedValue)
  {
    //noinspection UseJBColor
    return  new Color((int)Math.round(255 * rangedValue), 0, (int)Math.round(255 * (1 - rangedValue)), 130);
  }

  /**
   * type complex128 in numpy is compared by next rule:
   * A + Bj > C +Dj if A > C or A == C and B > D
   */
  private static double getComplexRangedValue(String value, String complexMax, String complexMin) {
    Pair<Double, Double> med = parsePyComplex(value);
    Pair<Double, Double> max = parsePyComplex(complexMax);
    Pair<Double, Double> min = parsePyComplex(complexMin);
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
        return new Pair<>(new Double(0.0), Double.parseDouble(real.substring(0, real.length() - 1)));
      }
      else {
        return new Pair<>(Double.parseDouble(real), Double.parseDouble(imag.substring(0, imag.length() - 1)));
      }
    }
    else {
      throw new IllegalArgumentException("Not a valid python complex value: " + pyComplexValue);
    }
  }
}
