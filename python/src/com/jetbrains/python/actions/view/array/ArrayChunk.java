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

    abstract int getRows();

    abstract int getColumns();

    abstract Object[][] getData();

    abstract String getPresentation();

    abstract void fillData(Runnable callback);
}
