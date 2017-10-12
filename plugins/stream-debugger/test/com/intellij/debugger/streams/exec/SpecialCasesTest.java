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

/**
 * @author Vitaliy.Bibaev
 */
public class SpecialCasesTest extends TraceExecutionTestCase {
  public void testSortedSignedDoubleZeros() {
    doTest(false);
  }

  public void testExceptionAsStreamResult() {
    doTest(false);
  }

  public void testShortCircuitingAfterSorted() {
    doTest(false);
  }

  public void testParallelStream() {
    doTest(false);
  }

  public void testNulls() {
    doTest(false);
  }

  public void testMapToNull() {
    doTest(false);
  }

  public void testMapNullToValue() {
    doTest(false);
  }

  // https://youtrack.jetbrains.com/issue/IDEA-178008
  public void testToCollection() {
    doTest(false);
  }
}
