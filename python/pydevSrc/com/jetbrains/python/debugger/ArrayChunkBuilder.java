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
package com.jetbrains.python.debugger;

import java.util.List;

public class ArrayChunkBuilder {
  private PyDebugValue myValue;
  private String myPresentation;
  private int myRows;
  private int myColumns;
  private String myMax;
  private String myMin;
  private String myFormat;
  private String myType;
  private Object[][] myData = null;
  private List<String> myRowLabels = null;
  private List<ArrayChunk.ColHeader> myColHeaders = null;

  public ArrayChunkBuilder setValue(PyDebugValue value) {
    myValue = value;
    return this;
  }

  public ArrayChunkBuilder setSlicePresentation(String presentation) {
    myPresentation = presentation;
    return this;
  }

  public ArrayChunkBuilder setRows(int rows) {
    myRows = rows;
    return this;
  }

  public ArrayChunkBuilder setColumns(int columns) {
    myColumns = columns;
    return this;
  }

  public ArrayChunkBuilder setMax(String max) {
    myMax = max;
    return this;
  }

  public ArrayChunkBuilder setMin(String min) {
    myMin = min;
    return this;
  }

  public ArrayChunkBuilder setFormat(String format) {
    myFormat = format;
    return this;
  }

  public ArrayChunkBuilder setType(String type) {
    myType = type;
    return this;
  }

  public ArrayChunkBuilder setData(Object[][] data) {
    myData = data;
    return this;
  }

  public void setRowLabels(List<String> rowLabels) {
    myRowLabels = rowLabels;
  }

  public void setColHeaders(List<ArrayChunk.ColHeader> colHeaders) {
    myColHeaders = colHeaders;
  }

  public ArrayChunk createArrayChunk() {
    return new ArrayChunk(myValue, myPresentation, myRows, myColumns, myMax, myMin, myFormat, myType, myData, myRowLabels, myColHeaders);
  }
}