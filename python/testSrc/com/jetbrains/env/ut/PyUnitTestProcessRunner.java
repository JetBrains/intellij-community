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

import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.env.ProcessWithConsoleRunner;
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.testing.PyUnitTestConfiguration;
import com.jetbrains.python.testing.PyUnitTestFactory;
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant;
import com.jetbrains.python.testing.PythonTestConfigurationType;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Set;

/**
 * {@link ProcessWithConsoleRunner} to run unittest
 *
 * @author Ilya.Kazakevich
 */
public class PyUnitTestProcessRunner extends PyScriptTestProcessRunner<PyUnitTestConfiguration> {
  /**
   * Prefix to use test pattern. See {@link #TEST_TARGET_PREFIX} doc because it is similar
   */
  public static final String TEST_PATTERN_PREFIX = "pattern:";

  private static final Set<Integer> EXIT_CODES_FOR_SKIPPED_TESTS = Set.of(0, 5);

  public PyUnitTestProcessRunner(@NotNull final String scriptName, final int timesToRerunFailedTests) {
    super(new PyUnitTestFactory(PythonTestConfigurationType.getInstance()),
          PyUnitTestConfiguration.class, scriptName, timesToRerunFailedTests);
  }

  @Override
  protected void configurationCreatedAndWillLaunch(@NotNull PyUnitTestConfiguration configuration) throws IOException {
    super.configurationCreatedAndWillLaunch(configuration);
    final Sdk sdk = configuration.getSdk();
    if (sdk == null || PythonSdkFlavor.getFlavor(sdk) instanceof CPythonSdkFlavor) {
      // -Werror checks we do not use deprecated API in runners, but only works for cpython (not iron nor jython)
      // and we can't use it for pytest/nose, since it is not our responsibility to check them for deprecation api usage
      // while unit is part of stdlib and does not use deprecated api, so only runners are checked
      configuration.setInterpreterOptions("-Werror");
    }

    if (myScriptName.startsWith(TEST_PATTERN_PREFIX)) {
      configuration.getTarget().setTargetType(PyRunTargetVariant.PATH);
      configuration.getTarget().setTarget(".");
      configuration.setPattern(myScriptName.substring(TEST_PATTERN_PREFIX.length()));
    }
  }

  @Override
  protected void assertExitCodeForSkippedTests(int code) {
    //TODO: Return `isPython312OrGreater ? 5 : 0` when the bug in СPython is fixed
    MatcherAssert.assertThat("Exit code must be 0 or 5 if all tests are ignored", code, Matchers.isIn(EXIT_CODES_FOR_SKIPPED_TESTS));
  }
}
