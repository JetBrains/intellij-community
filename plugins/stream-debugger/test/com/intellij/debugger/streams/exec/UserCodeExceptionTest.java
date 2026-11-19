// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper;
import com.intellij.debugger.streams.core.trace.TracingResult;
import com.intellij.debugger.streams.core.wrapper.StreamChain;
import com.intellij.debugger.streams.test.ExecutionTestCaseHelper;
import com.intellij.debugger.streams.test.TraceExecutionFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.debugger.streams.test.TraceExecutionFixtureKt.traceExecutionFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
public class UserCodeExceptionTest {
  protected final TestFixture<TraceExecutionFixture> traceFixture = traceExecutionFixture(this::getHelper);

  @Test
  public void testExceptionInProducerCall() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testExceptionInTerminatorCall() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testExceptionInIntermediateCall() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testExceptionInStreamWithPrimitiveResult() {
    traceFixture.get().doTest(false);
  }

  protected @NotNull TraceExecutionTestHelper getHelper(@NotNull TraceExecutionFixture trace, @NotNull XDebugSession session) {
    return new ExecutionTestCaseHelper(trace, session) {
      @Override
      protected void handleSuccess(@Nullable StreamChain chain, @Nullable TracingResult result, @Nullable Boolean resultMustBeNull) {
        assertNotNull(result);
        super.handleSuccess(chain, result, resultMustBeNull);
        assertTrue(result.exceptionThrown());
      }
    };
  }
}
