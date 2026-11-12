// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;

import com.jetbrains.python.fixture.PythonCommonCodeInsightTestFixture;
import com.jetbrains.python.fixtures.PythonPlatformCodeInsightTestFixture;
import com.jetbrains.python.psi.LanguageLevel;

@Subsystems.CodeInsight
@Layers.Functional
public class Py2ResolveTest extends PyCommonResolveTest {

  private final PythonCommonCodeInsightTestFixture myBackingFixture = new PythonPlatformCodeInsightTestFixture(LanguageLevel.PYTHON27);

  @Override
  protected PythonCommonCodeInsightTestFixture getFixture() {
    return myBackingFixture;
  }
}
