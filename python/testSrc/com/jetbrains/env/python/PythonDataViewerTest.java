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
package com.jetbrains.env.python;

import com.google.common.collect.ImmutableSet;
import com.intellij.util.Consumer;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerTestUtil;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.Staging;
import com.jetbrains.env.python.debug.PyDebuggerTask;
import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.intellij.testFramework.UsefulTestCase.assertSameElements;
import static org.junit.Assert.assertEquals;

public class PythonDataViewerTest extends PyEnvTestCase {

  @Test
  @Staging
  public void testDataFrameChunkRetrieval() {
    runPythonTest(new PyDataFrameDebuggerTask(getRelativeTestDataPath(), "test_dataframe.py", ImmutableSet.of(7, 15, 22)) {
      @Override
      public void testing() throws Exception {
        doTest("df1", 3, 5, null);

        doTest("df2", 3, 6, arrayChunk -> {
          List<ArrayChunk.ColHeader> colHeaders = arrayChunk.getColHeaders();
          assertSameElements(colHeaders.stream().map(ArrayChunk.ColHeader::getLabel).toArray(),
                             "LABELS", "One_X", "One_Y", "Two_X", "Two_Y", "row");
        });

        doTest("df3", 7, 3, arrayChunk -> {
          ArrayChunk.ColHeader header = arrayChunk.getColHeaders().get(2);
          assertEquals("Sales", header.getLabel());
          assertEquals(16, Float.valueOf(header.getMax()).intValue());
          assertEquals(1, Float.valueOf(header.getMin()).intValue());
        });
      }
    });
  }

  @Test
  public void testMultiIndexDataFrame() {
    runPythonTest(new PyDataFrameDebuggerTask(getRelativeTestDataPath(), "test_dataframe_multiindex.py", ImmutableSet.of(5, 10)) {
      @Override
      public void testing() throws Exception {
        doTest("frame1", 4, 2, arrayChunk -> assertSameElements(arrayChunk.getRowLabels(),
                                                                "s/2", "s/3", "d/2", "d/3"));
        doTest("frame2", 4, 4, arrayChunk -> {
          List<ArrayChunk.ColHeader> headers = arrayChunk.getColHeaders();
          assertSameElements(headers.stream().map(ArrayChunk.ColHeader::getLabel).toArray(), "1/1", "1/B", "2/1", "2/B");
        });
      }
    });
  }

  @Test
  @Staging
  public void testSeries() {
    runPythonTest(new PyDataFrameDebuggerTask(getRelativeTestDataPath(), "test_series.py", ImmutableSet.of(7)) {
      @Override
      public void testing() throws Exception {
        doTest("series", 4, 1, arrayChunk -> {
          List<String> labels = arrayChunk.getRowLabels();
          assertSameElements(labels, "s/2", "s/3", "d/2", "d/3");
        });
      }
    });
  }

  @Test
  public void testPandasRepeatingColumnNames() {
    runPythonTest(new PyDataFrameDebuggerTask(getRelativeTestDataPath(), "test_pandas_repeating_column_names.py",
                                              ImmutableSet.of(7)) {
      @Override
      public void testing() throws Exception {
        doTest("c", 10, 2, (varName, session) -> getChunk(varName, "%d", session), arrayChunk -> {
          for (ArrayChunk.ColHeader header : arrayChunk.getColHeaders())
            assertEquals("A", header.getLabel());
          Object[][] data = arrayChunk.getData();
          assertEquals("'0'", data[0][0].toString());
          assertEquals("'0'", data[0][1].toString());
          assertEquals("'6'", data[6][0].toString());
          assertEquals("'9'", data[9][0].toString());
        });
      }
    });
  }

  @Test
  @Staging
  public void testDataFrameFloatFormatting() {
    runPythonTest(new PyDataFrameDebuggerTask(getRelativeTestDataPath(), "test_dataframe.py", ImmutableSet.of(7)) {
      @Override
      public void testing() throws Exception {
        doTest("df1", 3, 5, (varName, session) -> getChunk(varName, "%.2f", session), arrayChunk -> {
          Object[][] data = arrayChunk.getData();
          assertEquals("'1.10'", data[0][1].toString());
          assertEquals("'1.22'", data[1][4].toString());
          assertEquals("'2019.00'", data[1][2].toString());
          assertEquals("'1.00'", data[2][3].toString());
        });
      }
    });
  }

  @Test
  @Staging
  public void testDataFrameDefaultFormatting() {
    runPythonTest(new PyDataFrameDebuggerTask(getRelativeTestDataPath(), "test_dataframe.py", ImmutableSet.of(7)) {
      @Override
      public void testing() throws Exception {
        doTest("df1", 3, 5, (varName, session) -> getChunk(varName, "%", session), arrayChunk -> {
          Object[][] data = arrayChunk.getData();
          assertEquals("'1.10000'", data[0][1].toString());
          assertEquals("'1.22000'", data[1][4].toString());
          assertEquals("'2019'", data[1][2].toString());
          assertEquals("'True'", data[2][3].toString());
        });
      }
    });
  }

  @Test
  @Staging
  public void testSeriesFormatting() {
    runPythonTest(new PyDataFrameDebuggerTask(getRelativeTestDataPath(), "test_series.py", ImmutableSet.of(7)) {
      @Override
      public void testing() throws Exception {
        doTest("series", 4, 1, (varName, session) -> getChunk(varName, "%03d", session), arrayChunk -> {
          Object[][] data = arrayChunk.getData();
          assertEquals("'000'", data[0][0].toString());
          assertEquals("'002'", data[1][0].toString());
          assertEquals("'004'", data[2][0].toString());
          assertEquals("'006'", data[3][0].toString());
        });
      }
    });
  }

  @Test
  @Staging
  public void testLabelWithPercentSign() {
    runPythonTest(new PyDataFrameDebuggerTask(getRelativeTestDataPath(), "test_dataframe.py", ImmutableSet.of(33)) {
      @Override
      public void testing() throws Exception {
        doTest("df5", 10, 1, chunk -> {
          final List<ArrayChunk.ColHeader> labels = chunk.getColHeaders();
          assertEquals(1, labels.size());
          assertEquals("foo_%", labels.get(0).getLabel());
        });
      }
    });
  }

  @Test
  @Staging
  public void testTuples() {
    runPythonTest(new PyDataFrameDebuggerTask(getRelativeTestDataPath(), "test_dataframe_tuple.py", ImmutableSet.of(5)) {
      @Override
      public void testing() throws Exception {
        doTest("df1", 3, 3, chunk -> {
          final Object[][] data = chunk.getData();

          assertEquals("'{1: (1, 2)}'", data[0][1].toString());
          assertEquals("'{2: (3,)}'", data[1][1].toString());
          assertEquals("'{4: 5}'", data[2][1].toString());

          assertEquals("'(1, 2, 3)'", data[0][2].toString());
          assertEquals("'()'", data[1][2].toString());
          assertEquals("'(4,)'", data[2][2].toString());
        });
      }
    });
  }

  private static class PyDataFrameDebuggerTask extends PyDebuggerTask {

    private final Set<Integer> myLines;

    @FunctionalInterface
    private interface ChunkSupplier {
      ArrayChunk supply(String varName, XDebugSession session) throws PyDebuggerException;
    }

    PyDataFrameDebuggerTask(@Nullable String relativeTestDataPath, String scriptName, Set<Integer> lines) {
      super(relativeTestDataPath, scriptName);
      myLines = lines;
    }

    protected void testShape(ArrayChunk arrayChunk, int expectedRows, int expectedColumns) {
      assertEquals(expectedRows, arrayChunk.getRows());
      assertEquals(expectedColumns, arrayChunk.getColumns());
    }

    protected void doTest(String name, int expectedRows, int expectedColumns, @Nullable Consumer<ArrayChunk> test)
      throws InterruptedException, PyDebuggerException {
      doTest(name, expectedRows, expectedColumns, PythonDataViewerTest::getDefaultChunk, test);
    }

    protected void doTest(String name, int expectedRows, int expectedColumns, @NotNull ChunkSupplier getChunk,
                          @Nullable Consumer<ArrayChunk> test) throws InterruptedException, PyDebuggerException {
        waitForPause();
        ArrayChunk arrayChunk = getChunk.supply(name, mySession);
        testShape(arrayChunk, expectedRows, expectedColumns);
        if (test != null) {
          test.consume(arrayChunk);
        }
        resume();
    }

    @Override
    public void before() {
      for (Integer line : myLines) {
        toggleBreakpoint(getScriptName(), line);
      }
    }

    @NotNull
    @Override
    public Set<String> getTags() {
      return ImmutableSet.of("pandas");
    }
  }

  private static ArrayChunk getDefaultChunk(String varName, XDebugSession session) throws PyDebuggerException {
    return getChunk(varName, "%.5f", session);
  }

  private static ArrayChunk getChunk(String varName, String format, XDebugSession session) throws PyDebuggerException {
    PyDebugValue dbgVal = (PyDebugValue)XDebuggerTestUtil.evaluate(session, varName).first;
    return dbgVal.getFrameAccessor().getArrayItems(dbgVal, 0, 0, -1, -1, format);
  }

  private static String getRelativeTestDataPath() {
    return "/debug";
  }
}
