// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.test.TraceExecutionTestCase;
import com.intellij.debugger.streams.trace.TracingResult;
import com.intellij.debugger.streams.wrapper.StreamChain;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public class UserCodeExceptionTest extends TraceExecutionTestCase {
  public void testExceptionInProducerCall() {
    doTest(false);
  }

  public void testExceptionInTerminatorCall() {
    doTest(false);
  }

  public void testExceptionInIntermediateCall() {
    doTest(false);
  }

  public void testExceptionInStreamWithPrimitiveResult() {
    doTest(false);
  }

  @Override
  protected void handleSuccess(@Nullable StreamChain chain, @Nullable TracingResult result, boolean resultMustBeNull) {
    assertNotNull(result);
    super.handleSuccess(chain, result, resultMustBeNull);
    assertTrue(result.exceptionThrown());
  }
}
