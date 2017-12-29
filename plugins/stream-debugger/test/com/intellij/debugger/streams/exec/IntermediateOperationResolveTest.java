// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.test.TraceExecutionTestCase;

/**
 * @author Vitaliy.Bibaev
 */
public class IntermediateOperationResolveTest extends TraceExecutionTestCase {
  public void testFilter() {
    doTest(false);
  }

  public void testMap() {
    doTest(false);
  }

  public void testFlatMap() {
    doTest(false);
  }

  public void testDistinctSame() {
    doTest(false);
  }

  public void testDistinctEquals() {
    doTest(false);
  }

  public void testDistinctHardCase() {
    doTest(false);
  }

  public void testSorted() {
    doTest(false);
  }

  public void testPeek() {
    doTest(false);
  }

  public void testSkip() {
    doTest(false);
  }

  public void testLimit() {
    doTest(false);
  }
}
