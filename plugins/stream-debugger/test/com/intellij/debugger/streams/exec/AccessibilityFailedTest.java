// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class AccessibilityFailedTest extends FailEvaluationTestCase {
  @NotNull
  @Override
  protected String replaceAdditionalInOutput(@NotNull String str) {
    // stack traces in jdk 9 include module name. The following code removes module name from the stack trace.
    return super.replaceAdditionalInOutput(str).replace("at java.base/", "at ");
  }

  /**
   * Now, evaluation of such test case is not supported. MagicAccessorImpl cannot be parent for a subclass of the class "Super"
   */
  public void testAccessNotObjectSubclass() {
    doTest(false);
  }
}
