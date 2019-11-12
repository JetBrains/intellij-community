// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.fixture.CommonPythonCodeInsightTestFixture;
import com.jetbrains.python.fixtures.PlatformPythonCodeInsightTestFixture;

@TestDataPath("$CONTENT_ROOT/../testData/completion")
public class PythonCompletionTest extends CommonPythonCompletionTest {
  private final CommonPythonCodeInsightTestFixture myBackingFixture = new PlatformPythonCodeInsightTestFixture();

  @Override
  protected void doTest() {
    CamelHumpMatcher.forceStartMatching(myBackingFixture.getTestRootDisposable());
    super.doTest();
  }

  @Override
  protected CommonPythonCodeInsightTestFixture getFixture() {
    return myBackingFixture;
  }
}
