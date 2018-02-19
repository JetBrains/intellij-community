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
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.ArrayChunkBuilder;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.containerview.DataViewStrategy;
import com.jetbrains.python.debugger.containerview.PyDataViewerPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import java.util.concurrent.*;

/**
 * @author traff
 */
public class AsyncArrayTableModel extends AbstractTableModel {
  private static final int CHUNK_COL_SIZE = 30;
  private static final int CHUNK_ROW_SIZE = 30;
  public static final String EMPTY_CELL_VALUE = "";

  private final int myRows;
  private final int myColumns;
  private final PyDataViewerPanel myDataProvider;


  private final ExecutorService myExecutorService = ConcurrencyUtil.newSingleThreadExecutor("Python async table");
  private final MergingUpdateQueue myQueue = new MergingUpdateQueue("Python async table queue", 100, true, null);

  private PyDebugValue myDebugValue;
  private final DataViewStrategy myStrategy;
  private final LoadingCache<Pair<Integer, Integer>, ListenableFuture<ArrayChunk>> myChunkCache = CacheBuilder.newBuilder().build(
    new CacheLoader<Pair<Integer, Integer>, ListenableFuture<ArrayChunk>>() {
      @Override
      public ListenableFuture<ArrayChunk> load(@NotNull final Pair<Integer, Integer> key) throws Exception {

        return ListenableFutureTask.create(() -> {
          ArrayChunk chunk = myDebugValue.getFrameAccessor()
            .getArrayItems(myDebugValue, key.first, key.second, Math.min(CHUNK_ROW_SIZE, getRowCount() - key.first),
                           Math.min(CHUNK_COL_SIZE, getColumnCount() - key.second), myDataProvider.getFormat());
          handleChunkAdded(key.first, key.second, chunk);
          return chunk;
        });
      }
    });

  public AsyncArrayTableModel(int rows,
                              int columns,
                              PyDataViewerPanel provider,
                              PyDebugValue debugValue,
                              DataViewStrategy strategy) {
    myRows = rows;
    myColumns = columns;
    myDataProvider = provider;
    myDebugValue = debugValue;
    myStrategy = strategy;
  }

  @Override
  public boolean isCellEditable(int row, int col) {
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
            return correctStringValue(data[r][c]);
          }
        }
      }
      else {
        myQueue.queue(new Update("get chunk from debugger") {
          @Override
          public void run() {
            chunk.addListener(() -> UIUtil.invokeLaterIfNeeded(() -> fireTableDataChanged()), myExecutorService);
            myExecutorService.execute(((ListenableFutureTask<ArrayChunk>)chunk));
          }
        });
      }
      return EMPTY_CELL_VALUE;
    }
    catch (Exception e) {
      return EMPTY_CELL_VALUE; //TODO: handle it
    }
  }

  public String correctStringValue(@NotNull Object value) {
    if (value instanceof String) {
      String corrected = (String)value;
      if (myStrategy.isNumeric(myDebugValue.getType())) {
        if (corrected.startsWith("\'") || corrected.startsWith("\"")) {
          corrected = corrected.substring(1, corrected.length() - 1);
        }
      }
      return corrected;
    }
    else if (value instanceof Integer) {
      return Integer.toString((Integer)value);
    }
    return value.toString();
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
    int rows = data.length;
    int cols = data[0].length;
    for (int roffset = 0; roffset < rows / CHUNK_ROW_SIZE; roffset++) {
      for (int coffset = 0; coffset < cols / CHUNK_COL_SIZE; coffset++) {
        Pair<Integer, Integer> key = itemToChunkKey(roffset * CHUNK_ROW_SIZE, coffset * CHUNK_COL_SIZE);
        final Object[][] chunkData = new Object[CHUNK_ROW_SIZE][CHUNK_COL_SIZE];
        for (int r = 0; r < CHUNK_ROW_SIZE; r++) {
          System.arraycopy(data[roffset * CHUNK_ROW_SIZE + r], coffset * CHUNK_COL_SIZE, chunkData[r], 0, CHUNK_COL_SIZE);
        }
        myChunkCache.put(key, new ListenableFuture<ArrayChunk>() {
          @Override
          public void addListener(@NotNull Runnable listener, @NotNull Executor executor) {

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
            return new ArrayChunkBuilder().setValue(chunk.getValue()).setSlicePresentation(null).setRows(0).setColumns(0).setMax(null)
              .setMin(null).setFormat(null).setType(null).setData(chunkData).createArrayChunk();
          }

          @Override
          public ArrayChunk get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return new ArrayChunkBuilder().setValue(chunk.getValue()).setSlicePresentation(null).setRows(0).setColumns(0).setMax(null)
              .setMin(null).setFormat(null).setType(null).setData(chunkData).createArrayChunk();
          }
        });
      }
    }
    handleChunkAdded(0, 0, chunk);
  }

  protected void handleChunkAdded(Integer rowOffset, Integer colOffset, ArrayChunk chunk) {

  }

  public TableModel getRowHeaderModel() {
    return new RowNumberHeaderModel();
  }


  private class RowNumberHeaderModel extends AbstractTableModel {

    @Override
    public int getRowCount() {
      return AsyncArrayTableModel.this.getRowCount();
    }

    @Override
    public int getColumnCount() {
      return 1;
    }

    @Override
    public String getColumnName(int column) {
      if (column == 0) {
        return "   ";
      }
      throw new IllegalArgumentException("Table only has one column");
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return Integer.toString(rowIndex);
    }
  }

  public void invalidateCache() {
    myChunkCache.invalidateAll();
  }

  public PyDebugValue getDebugValue() {
    return myDebugValue;
  }

  public void setDebugValue(PyDebugValue debugValue) {
    myDebugValue = debugValue;
  }
}