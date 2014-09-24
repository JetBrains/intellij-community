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

/**
 * @author amarch
 */
public class ArrayChunk {
  private String fullSlice;
  private int columns;
  private int rows;
  private int cOffset;
  private int rOffset;
  private Object[][] data;

  public static final int DEFAULT_CHUNK_HEIGHT = 10;
  public static final int DEFAULT_CHUNK_WIDTH = 10;

  public ArrayChunk(String slice, int cOffset, int rOffset) {
    this(slice, DEFAULT_CHUNK_HEIGHT, DEFAULT_CHUNK_WIDTH, cOffset, rOffset);
  }

  public ArrayChunk(String slice, int rows, int columns, int cOffset, int rOffset) {
    fullSlice = slice;
    this.columns = columns;
    this.rows = rows;
    this.rOffset = rOffset;
    this.cOffset = cOffset;
    data = new Object[rows][columns];
  }

  public void loadData() {
    String command = getDataCommand();
    fillData(command);
  }

  private void fillData(String command) {
    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < columns; j++) {
        data[i][j] = "(" + (i+rOffset) + "," + (j+cOffset) + ")";
      }
    }
    try {
      Thread.sleep(400);
    }
    catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  public String getDataCommand() {
    return fullSlice + "[" + rOffset + ":" + (rOffset + rows) + ", " + cOffset + ":" + (cOffset + columns) + "]";
  }

  public int getRows() {
    return rows;
  }

  public int getColumns() {
    return columns;
  }

  public Object[][] getData() {
    return data;
  }
}
