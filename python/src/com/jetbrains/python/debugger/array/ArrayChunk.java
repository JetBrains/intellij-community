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

/**
 * @author amarch
 */
public abstract class ArrayChunk {
  protected String baseSlice;
  protected int columns;
  protected int rows;
  protected int cOffset;
  protected int rOffset;
  protected Object[][] data;

  public ArrayChunk(String baseSlice, int rows, int columns, int rOffset, int cOffset) {
    this.baseSlice = baseSlice;
    this.columns = columns;
    this.rows = rows;
    this.rOffset = rOffset;
    this.cOffset = cOffset;
    data = new Object[rows][columns];
  }

  public int getRows() {
    return rows;
  }

  public int getColumns(){
    return columns;
  }

  public Object[][] getData() {
    return data;
  }

  public String getPresentation() {
    return "";
  }

  abstract void fillData(Runnable callback);

  public boolean contains(int row, int col) {
    return rOffset <= row && row < rOffset + rows && cOffset <= col && col < cOffset + columns;
  }

  public boolean equals(Object o) {
    return o instanceof ArrayChunk &&
           baseSlice == ((ArrayChunk)o).baseSlice &&
           columns == ((ArrayChunk)o).columns &&
           rows == ((ArrayChunk)o).rows &&
           cOffset == ((ArrayChunk)o).cOffset &&
           rOffset == ((ArrayChunk)o).rOffset;
  }
}
