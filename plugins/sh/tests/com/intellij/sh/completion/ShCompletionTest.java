// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.completion;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class ShCompletionTest extends BasePlatformTestCase {
  public void testWordsCompletion() {
    myFixture.configureByText("a.sh", "echo\nech<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("echo\necho <caret>");
  }
}
