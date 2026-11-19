// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
public class SpecialCasesTest {
  protected final TestFixture<TraceExecutionFixture> traceFixture = traceExecutionFixture(this::getHelper);

  @Test
  public void testSortedSignedDoubleZeros() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testExceptionAsStreamResult() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testShortCircuitingAfterSorted() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testParallelStream() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testNulls() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testMapToNull() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testMapNullToValue() {
    traceFixture.get().doTest(false);
  }

  // https://youtrack.jetbrains.com/issue/IDEA-178008
  @Test
  public void testToCollection() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testChainInSourceExpression() {
    traceFixture.get().doTest(false);
  }

  protected @NotNull TraceExecutionTestHelper getHelper(@NotNull TraceExecutionFixture trace, @NotNull XDebugSession session) {
    return new ExecutionTestCaseHelper(trace, session);
  }
}
