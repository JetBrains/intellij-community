// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper;
import com.intellij.debugger.streams.test.ExecutionTestCaseHelper;
import com.intellij.debugger.streams.test.TraceExecutionFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.debugger.streams.test.TraceExecutionFixtureKt.traceExecutionFixture;

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
public class IntermediateOperationResolveTest {
  protected final TestFixture<TraceExecutionFixture> traceFixture = traceExecutionFixture(this::getHelper);

  @Test
  public void testFilter() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testMap() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testFlatMap() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testMapMulti() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testMapMultiFilter() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testMapMultiEmpty() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testDistinctSame() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testDistinctEquals() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testDistinctHardCase() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testSorted() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testPeek() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testSkip() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testLimit() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testTakeWhile() {
    traceFixture.get().doTest(false);
  }

  protected @NotNull TraceExecutionTestHelper getHelper(@NotNull TraceExecutionFixture trace, @NotNull XDebugSession session) {
    return new ExecutionTestCaseHelper(trace, session);
  }
}
