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

import java.util.List;
import java.util.Set;

import static com.intellij.testFramework.UsefulTestCase.assertSameElements;
import static org.junit.Assert.assertEquals;

public class PythonDataViewerTest extends PyEnvTestCase {

  @Test
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
          assertEquals(16, (int)Integer.valueOf(header.getMax()));
          assertEquals(1, (int)Integer.valueOf(header.getMin()));
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

  private static class PyDataFrameDebuggerTask extends PyDebuggerTask {

    private Set<Integer> myLines;

    public PyDataFrameDebuggerTask(@Nullable String relativeTestDataPath, String scriptName, Set<Integer> lines) {
      super(relativeTestDataPath, scriptName);
      myLines = lines;
    }

    protected void testShape(ArrayChunk arrayChunk, int expectedRows, int expectedColumns) {
      assertEquals(expectedRows, arrayChunk.getRows());
      assertEquals(expectedColumns, arrayChunk.getColumns());
    }

    protected void doTest(String name, int expectedRows, int expectedColumns, @Nullable Consumer<ArrayChunk> test)
      throws InterruptedException, PyDebuggerException {
      waitForPause();
      ArrayChunk arrayChunk = getDefaultChunk(name, mySession);
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
    PyDebugValue dbgVal = (PyDebugValue)XDebuggerTestUtil.evaluate(session, varName).first;
    return dbgVal.getFrameAccessor().getArrayItems(dbgVal, 0, 0, -1, -1, ".%5f");
  }

  private static String getRelativeTestDataPath() {
    return "/debug";
  }
}
