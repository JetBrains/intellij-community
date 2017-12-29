// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec;

/**
 * @author Vitaliy.Bibaev
 */
public class AccessibilityFailedTest extends FailEvaluationTestCase {

  /**
   * Now, evaluation of such test case is not supported. MagicAccessorImpl cannot be parent for a subclass of the class "Super"
   */
  public void testAccessNotObjectSubclass() {
    doTest(false);
  }
}
