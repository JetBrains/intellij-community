// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;

import com.jetbrains.python.fixture.PythonCommonCodeInsightTestFixture;
import com.jetbrains.python.fixtures.PythonPlatformCodeInsightTestFixture;

@Subsystems.Formatter
@Layers.Functional
public class PyFormatterTest extends PythonCommonFormatterTest {
  @Override
  protected PythonCommonCodeInsightTestFixture getFixture() {
    return new PythonPlatformCodeInsightTestFixture(LANGUAGE_LEVEL);
  }
}
