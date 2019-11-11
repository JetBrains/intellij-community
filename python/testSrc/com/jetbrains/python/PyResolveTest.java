// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.jetbrains.python.fixture.CommonPythonCodeInsightTestFixture;
import com.jetbrains.python.fixtures.PlatformPythonCodeInsightTestFixture;

public class PyResolveTest extends CommonPyResolveTest {

  private final CommonPythonCodeInsightTestFixture myBackingFixture = new PlatformPythonCodeInsightTestFixture();

  @Override
  protected CommonPythonCodeInsightTestFixture getFixture() {
    return myBackingFixture;
  }
}
