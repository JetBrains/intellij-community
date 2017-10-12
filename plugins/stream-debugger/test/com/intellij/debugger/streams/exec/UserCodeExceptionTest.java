/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
