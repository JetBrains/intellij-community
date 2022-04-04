// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec.breakpoint;

/**
 * @author Shumaf Lovpache
 */
public class TerminalOperationTest extends BreakpointBasedTraceExecutionTestCase {
  public void testForEachBreakpointBased() {
    doTest(true);
  }

  public void testForEachOrderedBreakpointBased() {
    doTest(true);
  }
}
