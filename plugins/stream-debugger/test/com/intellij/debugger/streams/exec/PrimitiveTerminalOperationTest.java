// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper;
import com.intellij.debugger.streams.core.trace.PrimitiveValue;
import com.intellij.debugger.streams.core.trace.Value;
import com.intellij.debugger.streams.test.ExecutionTestCaseHelper;
import com.intellij.debugger.streams.test.TraceExecutionFixture;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.debugger.streams.test.TraceExecutionFixtureKt.traceExecutionFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
public class PrimitiveTerminalOperationTest {
  protected final TestFixture<TraceExecutionFixture> traceFixture = traceExecutionFixture(this::getHelper);

  @Test
  public void testPrimitiveResultBoolean() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testPrimitiveResultInt() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testPrimitiveResultDouble() {
    traceFixture.get().doTest(false);
  }

  @Test
  public void testPrimitiveResultLong() {
    traceFixture.get().doTest(false);
  }

  protected @NotNull TraceExecutionTestHelper getHelper(@NotNull TraceExecutionFixture trace, @NotNull XDebugSession session) {
    return new ExecutionTestCaseHelper(trace, session) {
      @Override
      protected void handleResultValue(@Nullable Value result, boolean mustBeNull) {
        assertFalse(mustBeNull);
        assertNotNull(result);
        assertInstanceOf(PrimitiveValue.class, result);
        println("Result type:" + result.typeName(), ProcessOutputTypes.SYSTEM);
        println("value = " + result, ProcessOutputTypes.SYSTEM);
      }
    };
  }
}
