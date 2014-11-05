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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBViewport;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.Queue;
import com.jetbrains.python.debugger.PyDebugValue;

import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

/**
 * @author traff
 */
public class PagingTableModel extends AbstractTableModel {
  private static final int CHUNK_COL_SIZE = 2; //TODO set to 100
  private static final int CHUNK_ROW_SIZE = 2;
  private static final int DEFAULT_MAX_CACHED_SIZE = 100;
  public static final String EMPTY_CELL_VALUE = "...";

  private LoadingCache<Pair<Integer, Integer>, Object[][]> myChunkCache = CacheBuilder.newBuilder().build(
    new CacheLoader<Pair<Integer, Integer>, Object[][]>() {
      @Override
      public Object[][] load(Pair<Integer, Integer> key) throws Exception {
        PyDebugValue value = myProvider.getDebugValue();
        value = new PyDebugValue(myProvider.getSliceText(), value.getType(), value.getValue(), value.isContainer(), value.isErrorOnEval(),
                                 value.getFrameAccessor());
        return value.getFrameAccessor().getArrayItems(value, key.first, key.second, CHUNK_COL_SIZE, CHUNK_ROW_SIZE, myProvider.getFormat());
      }
    });

  private int myRows = 0;
  private int myColumns = 0;
  private NumpyArrayTable myProvider;


  public PagingTableModel(int rows, int columns, NumpyArrayTable provider) {
    myRows = rows;
    myColumns = columns;
    myProvider = provider;
  }

  @Override
  public boolean isCellEditable(int row, int column) {
    return !getValueAt(row, column).equals(EMPTY_CELL_VALUE);
  }

  public Object getValueAt(int row, int col) {
    Pair<Integer, Integer> key = itemToChunkKey(row, col);

    try {
      Object[][] chunk = myChunkCache.get(key);

      int r = row % CHUNK_ROW_SIZE;
      int c = col % CHUNK_COL_SIZE;

      if (r < chunk.length) {
        if (c < chunk[r].length) {
          return chunk[r][c];
        }
        else {
          return EMPTY_CELL_VALUE;
        }
      }
      else {
        return EMPTY_CELL_VALUE;
      }
    }
    catch (ExecutionException e) {
      return EMPTY_CELL_VALUE; //TODO: handle it
    }
  }

  private static Pair<Integer, Integer> itemToChunkKey(int row, int col) {
    return Pair.create(getPageRowStart(row), getPageColStart(col));
  }

  private static int getPageRowStart(int rowOffset) {
    return rowOffset - (rowOffset % CHUNK_ROW_SIZE);
  }

  private static int getPageColStart(int colOffset) {
    return colOffset - (colOffset % CHUNK_COL_SIZE);
  }

  public int getColumnCount() {
    return myColumns;
  }

  public String getColumnName(int col) {
    return String.valueOf(col);
  }

  public int getRowCount() {
    return myRows;
  }

  public void forcedChange(int row, int col, Object value) {
    Object[][] chunk = myChunkCache.getIfPresent(itemToChunkKey(row, col));
    if (chunk != null) {
      chunk[row - getPageRowStart(row)][col - getPageColStart(col)] = value;
    }
    else {
      throw new IllegalArgumentException("Forced to change empty cell in " + row + " row and " + col + "column.");
    }
  }
}