// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.test.TraceExecutionTestCase;

public class MatchTest extends TraceExecutionTestCase {
  public void testAllMatchExtraCalls() {
    doTest(false);
  }

  public void testAnyMatchExtraCalls() {
    doTest(false);
  }

  public void testNoneMatchExtraCalls() {
    doTest(false);
  }
}
