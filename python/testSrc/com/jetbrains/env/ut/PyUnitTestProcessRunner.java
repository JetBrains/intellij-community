/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.env.ut;

import com.jetbrains.env.ProcessWithConsoleRunner;
import com.jetbrains.python.testing.universalTests.PyUniversalUnitTestConfiguration;
import com.jetbrains.python.testing.universalTests.PyUniversalUnitTestFactory;
import com.jetbrains.python.testing.universalTests.TestTargetType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * {@link ProcessWithConsoleRunner} to run unittest
 *
 * @author Ilya.Kazakevich
 */
public class PyUnitTestProcessRunner extends PyScriptTestProcessRunner<PyUniversalUnitTestConfiguration> {
  /**
   * Prefix to use test pattern. See {@link #TEST_PATTERN_PREFIX} doc because it is similar
   */
  public static final String TEST_PATTERN_PREFIX = "pattern:";

  public PyUnitTestProcessRunner(@NotNull final String scriptName, final int timesToRerunFailedTests) {
    super(PyUniversalUnitTestFactory.INSTANCE,
          PyUniversalUnitTestConfiguration.class, scriptName, timesToRerunFailedTests);
  }

  @Override
  protected void configurationCreatedAndWillLaunch(@NotNull PyUniversalUnitTestConfiguration configuration) throws IOException {
    super.configurationCreatedAndWillLaunch(configuration);
    if (myScriptName.startsWith(TEST_PATTERN_PREFIX)) {
      configuration.getTarget().setTargetType(TestTargetType.PATH);
      configuration.getTarget().setTarget(".");
      configuration.setPattern(myScriptName.substring(TEST_PATTERN_PREFIX.length()));
    }
  }
}
