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
public class PrimitiveStreamResolveTest extends TraceExecutionTestCase {
  public void testDistinctPrimitive() {
    doTest(false);
  }

  public void testDistinctHardPrimitive() {
    doTest(false);
  }

  public void testFilterPrimitive() {
    doTest(false);
  }

  public void testMapPrimitive() {
    doTest(false);
  }

  public void testFlatMapPrimitive() {
    doTest(false);
  }

  public void testSortedPrimitive() {
    doTest(false);
  }
}
