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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBViewport;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.Queue;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.debugger.PyDebugValue;

import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.TreeSet;
import java.util.concurrent.*;

/**
 * @author traff
 */
public class AsyncArrayTableModel extends AbstractTableModel {
  private static final int CHUNK_COL_SIZE = 2; //TODO set to 100
  private static final int CHUNK_ROW_SIZE = 2;
  public static final String EMPTY_CELL_VALUE = "";

  private final int myRows;
  private final int myColumns;
  private final NumpyArrayTable myProvider;


  private final ExecutorService myExecutorService = Executors.newSingleThreadExecutor();


  private LoadingCache<Pair<Integer, Integer>, ListenableFuture<Object[][]>> myChunkCache = CacheBuilder.newBuilder().build(
    new CacheLoader<Pair<Integer, Integer>, ListenableFuture<Object[][]>>() {
      @Override
      public ListenableFuture<Object[][]> load(final Pair<Integer, Integer> key) throws Exception {
        final PyDebugValue value = myProvider.getDebugValue();
        final PyDebugValue slicedValue =
          new PyDebugValue(myProvider.getSliceText(), value.getType(), value.getValue(), value.isContainer(), value.isErrorOnEval(),
                           value.getFrameAccessor());

        ListenableFutureTask<Object[][]> task = ListenableFutureTask.create(new Callable<Object[][]>() {
          @Override
          public Object[][] call() throws Exception {
            return value.getFrameAccessor()
              .getArrayItems(slicedValue, key.first, key.second, CHUNK_COL_SIZE, CHUNK_ROW_SIZE, myProvider.getFormat());
          }
        });

        myExecutorService.execute(task);

        return task;
      }
    });

  public AsyncArrayTableModel(int rows, int columns, NumpyArrayTable provider) {
    myRows = rows;
    myColumns = columns;
    myProvider = provider;
  }

  @Override
  public boolean isCellEditable(int row, int column) {
    return !getValueAt(row, column).equals(EMPTY_CELL_VALUE);
  }

  public Object getValueAt(final int row, final int col) {
    Pair<Integer, Integer> key = itemToChunkKey(row, col);

    try {
      ListenableFuture<Object[][]> chunk = myChunkCache.get(key);

      chunk.addListener(new Runnable() {
        @Override
        public void run() {
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            @Override
            public void run() {
              fireTableCellUpdated(row, col);
            }
          });
        }
      }, myExecutorService);


      if (chunk.isDone()) {
        int r = row % CHUNK_ROW_SIZE;
        int c = col % CHUNK_COL_SIZE;

        if (r < chunk.get().length) {
          if (c < chunk.get()[r].length) {
            return chunk.get()[r][c];
          }
        }
      }
      return EMPTY_CELL_VALUE;
    }
    catch (Exception e) {
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

  public void changeValue(int row, int col, Object value) {
    Future<Object[][]> chunk = myChunkCache.getIfPresent(itemToChunkKey(row, col));
    if (chunk != null && chunk.isDone()) {
      try {
        chunk.get()[row - getPageRowStart(row)][col - getPageColStart(col)] = value;
      }
      catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
    else {
      throw new IllegalArgumentException("Forced to change empty cell in " + row + " row and " + col + "column.");
    }
  }
}