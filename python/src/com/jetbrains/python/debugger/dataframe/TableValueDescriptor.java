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

import com.google.common.base.MoreObjects;
import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.containerview.PyNumericViewUtil;

/**
 * Created by Yuli Fiterman on 4/26/2016.
 */
public class TableValueDescriptor {
  private final String myValue;
  private final ArrayChunk.ColHeader myHeader;

  public TableValueDescriptor(String value, ArrayChunk.ColHeader header) {
    myValue = value;
    myHeader = header;
  }

  public String getValue() {
    return myValue;
  }

  public double getRangedValue() {
    if (myValue == null || myHeader == null)
    {
       return Double.NaN;
    }
    String dataType = myHeader.getType();
    if ("o".equals(dataType))
    {
      return Double.NaN;
    }
    String minValue = MoreObjects.firstNonNull(myHeader.getMin(), "0");
    String maxValue = MoreObjects.firstNonNull(myHeader.getMax(), "0");

    double min;
    double max;
    if ("c".equals(dataType)) {
      min = 0;
      max = 1;
    }
    else if ("b".equals(dataType)) {
      min = minValue.equals("True") ? 1 : 0;
      max = maxValue.equals("True") ? 1 : 0;
    }
    else {
      min = Double.parseDouble(minValue);
      max = Double.parseDouble(maxValue);
    }

    return (min == max)? 0 : PyNumericViewUtil.getRangedValue(myValue, dataType, min, max, minValue, maxValue);
  }

  @Override
  public String toString() {
    if (myValue == null || myHeader == null)
    {
      return "";
    }
    else
    {
       return myValue;
    }
  }
}