// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.jetbrains.python.fixture.PythonCommonCodeInsightTestFixture;
import com.jetbrains.python.fixtures.PythonPlatformCodeInsightTestFixture;
import com.jetbrains.python.psi.LanguageLevel;

public class PythonFormatterTest extends PythonCommonFormatterTest {
  @Override
  protected PythonCommonCodeInsightTestFixture getFixture() {
    return new PythonPlatformCodeInsightTestFixture(LANGUAGE_LEVEL);
  }
}
