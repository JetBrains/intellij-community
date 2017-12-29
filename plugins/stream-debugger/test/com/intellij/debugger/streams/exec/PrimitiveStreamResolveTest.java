// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
