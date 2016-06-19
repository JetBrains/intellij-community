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
import com.intellij.xdebugger.XDebuggerTestUtil;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.Staging;
import com.jetbrains.env.python.debug.PyDebuggerTask;
import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.array.AsyncArrayTableModel;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Set;

import static com.intellij.testFramework.UsefulTestCase.assertSameElements;
import static org.junit.Assert.assertEquals;

/**
 * Created by Yuli Fiterman on 5/10/2016.
 */
public class PythonDataViewerTest extends PyEnvTestCase {

  @Test
  @Staging
  public void testDataFrameChunkRetrieval() throws Exception {
    runPythonTest(new PyDebuggerTask("/debug", "test_dataframe.py") {
      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptName(), 7);
        toggleBreakpoint(getScriptName(), 15);
        toggleBreakpoint(getScriptName(), 22);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        ArrayChunk df1 = getDefaultChunk("df1");
        assertEquals(5, df1.getColumns());
        assertEquals(3, df1.getRows());
        resume();

        waitForPause();
        ArrayChunk df2 = getDefaultChunk("df2");
        assertEquals(6, df2.getColumns());
        assertEquals(3, df2.getRows());
        assertSameElements(df2.getColHeaders().stream().map((header -> header.getLabel())).toArray(),
                           new String[]{"LABELS", "One_X", "One_Y", "Two_X", "Two_Y", "row"});
        resume();

        waitForPause();
        ArrayChunk df3 = getDefaultChunk("df3");
        assertEquals(3, df3.getColumns());
        assertEquals(7, df3.getRows());
        ArrayChunk.ColHeader header = df3.getColHeaders().get(2);
        assertEquals("Sales", header.getLabel());
        assertEquals(16, (int)Integer.valueOf(header.getMax()));
        assertEquals(1, (int)Integer.valueOf(header.getMin()));
        resume();
      }

      private ArrayChunk getDefaultChunk(String varName) throws PyDebuggerException {
        PyDebugValue dbgVal = (PyDebugValue)XDebuggerTestUtil.evaluate(mySession, varName).first;
        return dbgVal.getFrameAccessor()
          .getArrayItems(dbgVal, 0, 0, -1, -1, ".%5f");
      }


      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("pandas");
      }
    });
  }
}
