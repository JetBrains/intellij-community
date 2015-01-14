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
package com.jetbrains.python.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author amarch
 */
public class ArrayChunk {
  private final PyDebugValue myValue;
  private final String mySlicePresentation;
  private final int myRows;
  private final int myColumns;
  private final String myMax;
  private final String myMin;
  private final String myFormat;
  private final String myType;
  private final Object[][] myData;

  public ArrayChunk(@NotNull PyDebugValue value,
                    String slicePresentation,
                    int rows,
                    int columns,
                    String max,
                    String min,
                    String format,
                    String type,
                    @Nullable Object[][] data) {
    myValue = value;
    mySlicePresentation = slicePresentation;
    myRows = rows;
    myColumns = columns;
    myMax = max;
    myMin = min;
    myFormat = format;
    myType = type;
    myData = data;
  }

  public PyDebugValue getValue() {
    return myValue;
  }

  public String getSlicePresentation() {
    return mySlicePresentation;
  }

  public int getRows() {
    return myRows;
  }

  public int getColumns() {
    return myColumns;
  }

  public String getMax() {
    return myMax;
  }

  public String getMin() {
    return myMin;
  }

  public String getFormat() {
    return myFormat;
  }

  public String getType() {
    return myType;
  }

  public Object[][] getData() {
    return myData;
  }
}
