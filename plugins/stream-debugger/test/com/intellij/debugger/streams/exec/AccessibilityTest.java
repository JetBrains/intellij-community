// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.test.TraceExecutionTestCase;

/**
 * @author Vitaliy.Bibaev
 */
public class AccessibilityTest extends TraceExecutionTestCase {
  public void testAccessToPrivateMethodsInStaticContext() {
    doTest(false);
  }

  public void testAccessToPrivateMethods() {
    doTest(false);
  }

  public void testAccessToPrivateClassInStaticContext() {
    doTest(false);
  }

  public void testAccessToPrivateClass() {
    doTest(false);
  }

  public void testAccessToPrivateClassWithMethodReference() {
    doTest(false);
  }

  public void testNotImportedLambdaResult() {
    doTest(false);
  }
}
