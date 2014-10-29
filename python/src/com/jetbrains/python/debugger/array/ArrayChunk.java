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
package com.jetbrains.python.debugger.array;

import org.jetbrains.annotations.NotNull;

/**
 * @author amarch
 */
public abstract class ArrayChunk implements Comparable<ArrayChunk> {
  private final String myBaseSlice;
  private final int myColumns;
  private final int myRows;
  private final int myColOffset;
  private final int myRowOffset;
  private Object[][] myData;

  public ArrayChunk(String baseSlice, int rows, int columns, int rOffset, int cOffset) {
    myBaseSlice = baseSlice;
    myColumns = columns;
    myRows = rows;
    myRowOffset = rOffset;
    myColOffset = cOffset;
    myData = new Object[rows][columns];
  }

  public int getRows() {
    return myRows;
  }

  public int getColumns() {
    return myColumns;
  }

  public int getColOffset() {
    return myColOffset;
  }

  public int getRowOffset() {
    return myRowOffset;
  }

  public Object[][] getData() {
    return myData;
  }

  public String getBaseSlice() {
    return myBaseSlice;
  }

  public String getPresentation() {
    return "";
  }

  abstract void fillData(Runnable callback);

  public boolean contains(int row, int col) {
    return myRowOffset <= row && row < myRowOffset + myRows && myColOffset <= col && col < myColOffset + myColumns;
  }

  public boolean equals(Object o) {
    return o instanceof ArrayChunk &&
           myBaseSlice == ((ArrayChunk)o).myBaseSlice &&
           myColumns == ((ArrayChunk)o).myColumns &&
           myRows == ((ArrayChunk)o).myRows &&
           myColOffset == ((ArrayChunk)o).myColOffset &&
           myRowOffset == ((ArrayChunk)o).myRowOffset;
  }

  @Override
  public int compareTo(@NotNull ArrayChunk other) {
    int compRow = myRowOffset - other.myRowOffset;
    int compCol = myColOffset - other.myColOffset;
    return compRow != 0 ? compRow : compCol;
  }

  public boolean isOneRow() {
    return getRows() == 1;
  }

  public boolean isOneColumn() {
    return getColumns() == 1;
  }
}
