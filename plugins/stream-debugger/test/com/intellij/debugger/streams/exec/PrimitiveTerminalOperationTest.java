// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.test.TraceExecutionTestCase;
import com.intellij.debugger.streams.trace.PrimitiveValue;
import com.intellij.debugger.streams.trace.Value;
import com.intellij.execution.process.ProcessOutputTypes;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public class PrimitiveTerminalOperationTest extends TraceExecutionTestCase {
  public void testPrimitiveResultBoolean() {
    doTest(false);
  }

  public void testPrimitiveResultInt() {
    doTest(false);
  }

  public void testPrimitiveResultDouble() {
    doTest(false);
  }

  public void testPrimitiveResultLong() {
    doTest(false);
  }

  @Override
  protected void handleResultValue(@Nullable Value result, boolean mustBeNull) {
    assertFalse(mustBeNull);
    assertNotNull(result);
    assertInstanceOf(result, PrimitiveValue.class);
    println("Result type:" + result.typeName(), ProcessOutputTypes.SYSTEM);
    println("value = " + result.toString(), ProcessOutputTypes.SYSTEM);
  }
}
