/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.Pair;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;

import javax.swing.table.AbstractTableModel;
import java.util.concurrent.*;

/**
 * @author traff
 */
public class AsyncArrayTableModel extends AbstractTableModel {
  private static final int CHUNK_COL_SIZE = 30;
  private static final int CHUNK_ROW_SIZE = 30;
  public static final String EMPTY_CELL_VALUE = "";

  private int myRows;
  private int myColumns;
  private final NumpyArrayTable myProvider;


  private final ExecutorService myExecutorService = Executors.newSingleThreadExecutor(ConcurrencyUtil.newNamedThreadFactory("Python async table"));


  private LoadingCache<Pair<Integer, Integer>, ListenableFuture<ArrayChunk>> myChunkCache = CacheBuilder.newBuilder().build(
    new CacheLoader<Pair<Integer, Integer>, ListenableFuture<ArrayChunk>>() {
      @Override
      public ListenableFuture<ArrayChunk> load(final Pair<Integer, Integer> key) throws Exception {
        final PyDebugValue value = myProvider.getDebugValue();
        final PyDebugValue slicedValue =
          new PyDebugValue(myProvider.getSliceText(), value.getType(), value.getValue(), value.isContainer(), value.isErrorOnEval(),
                           value.getParent(), value.getFrameAccessor());

        ListenableFutureTask<ArrayChunk> task = ListenableFutureTask.create(new Callable<ArrayChunk>() {
          @Override
          public ArrayChunk call() throws Exception {
            return value.getFrameAccessor()
              .getArrayItems(slicedValue, key.first, key.second, Math.min(CHUNK_ROW_SIZE, getRowCount() - key.first),
                             Math.min(CHUNK_COL_SIZE, getColumnCount() - key.second),
                             myProvider.getFormat());
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
  public boolean isCellEditable(int row, int col) {
    //Pair<Integer, Integer> key = itemToChunkKey(row, col);
    //try {
    //  return myChunkCache.get(key).isDone();
    //}
    //catch (ExecutionException e) {
    //  return false;
    //}
    //TODO: make it editable
    return false;
  }

  public Object getValueAt(final int row, final int col) {
    Pair<Integer, Integer> key = itemToChunkKey(row, col);

    try {
      ListenableFuture<ArrayChunk> chunk = myChunkCache.get(key);

      if (chunk.isDone()) {
        Object[][] data = chunk.get().getData();
        int r = row % CHUNK_ROW_SIZE;
        int c = col % CHUNK_COL_SIZE;

        if (r < data.length) {
          if (c < data[r].length) {
            return myProvider.correctStringValue(data[r][c]);
          }
        }
      }
      else {
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
      }
      return EMPTY_CELL_VALUE;
    }
    catch (Exception e) {
      myProvider.showError(e.getMessage());
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
    Future<ArrayChunk> chunk = myChunkCache.getIfPresent(itemToChunkKey(row, col));
    if (chunk != null && chunk.isDone()) {
      try {
        chunk.get().getData()[row - getPageRowStart(row)][col - getPageColStart(col)] = value;
      }
      catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
    else {
      throw new IllegalArgumentException("Forced to change empty cell in " + row + " row and " + col + " column.");
    }
  }

  public void addToCache(final ArrayChunk chunk) {
    Object[][] data = chunk.getData();
    int cols = data.length;
    int rows = data[0].length;
    for (int roffset = 0; roffset < rows / CHUNK_ROW_SIZE; roffset++) {
      for (int coffset = 0; coffset < cols / CHUNK_COL_SIZE; coffset++) {
        Pair<Integer, Integer> key = itemToChunkKey(roffset * CHUNK_ROW_SIZE, coffset * CHUNK_COL_SIZE);
        final Object[][] chunkData = new Object[CHUNK_ROW_SIZE][CHUNK_COL_SIZE];
        for (int r = 0; r < CHUNK_ROW_SIZE; r++) {
          for (int c = 0; c < CHUNK_COL_SIZE; c++) {
            chunkData[r][c] = data[roffset * CHUNK_ROW_SIZE + r][coffset * CHUNK_COL_SIZE + c];
          }
        }
        myChunkCache.put(key, new ListenableFuture<ArrayChunk>() {
          @Override
          public void addListener(Runnable listener, Executor executor) {

          }

          @Override
          public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
          }

          @Override
          public boolean isCancelled() {
            return false;
          }

          @Override
          public boolean isDone() {
            return true;
          }

          @Override
          public ArrayChunk get() throws InterruptedException, ExecutionException {
            return new ArrayChunk(chunk.getValue(), null, 0, 0, null, null, null, null, chunkData);
          }

          @Override
          public ArrayChunk get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return new ArrayChunk(chunk.getValue(), null, 0, 0, null, null, null, null, chunkData);
          }
        });
      }
    }
  }
}