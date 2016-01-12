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
package com.jetbrains.env;

import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdkTools.SdkCreationType;
import com.jetbrains.python.testing.tox.PyToxConfiguration;
import com.jetbrains.python.testing.tox.PyToxConfigurationFactory;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.Set;

/**
 * Ensure tox runner works
 * @author Ilya.Kazakevich
 */
public final class PyToxTest extends PyEnvTestCase {
  public PyToxTest() {
    super("tox");
  }

  public void testTox() {
    runPythonTest(new MyPyProcessWithConsoleTestTask());
  }

  private static class MyPyProcessWithConsoleTestTask extends PyProcessWithConsoleTestTask<MyTestProcessRunner> {
    private MyPyProcessWithConsoleTestTask() {
      super(SdkCreationType.SDK_PACKAGES_ONLY);
    }

    @Override
    protected void checkTestResults(@NotNull MyTestProcessRunner runner,
                                    @NotNull String stdout,
                                    @NotNull String stderr,
                                    @NotNull String all) {
      //all --py26, py27
      // 26 and 27 are used in tox.ini, so there should be such text
      Assert.assertThat("No 26 used from tox.ini", all, Matchers.containsString("py26"));
      Assert.assertThat("No 27 used from tox.ini", all, Matchers.containsString("py27"));

      //At least one interpreter tests should passed
      Assert.assertThat("No test passed, should 2 at least", runner.getPassedTestsCount(), Matchers.greaterThanOrEqualTo(2));

      if (stderr.isEmpty()) {
        return;
      }
      Logger.getInstance(PyToxTest.class).warn(stderr);
    }

    @NotNull
    @Override
    public Set<String> getTags() {
      return Sets.newHashSet("tox");
    }

    @NotNull
    @Override
    protected MyTestProcessRunner createProcessRunner() throws Exception {
      return new MyTestProcessRunner();
    }
  }

  private static class MyTestProcessRunner extends PyAbstractTestProcessRunner<PyToxConfiguration> {
    private MyTestProcessRunner() {
      super(PyToxConfigurationFactory.INSTANCE, PyToxConfiguration.class,
            PythonHelpersLocator.getPythonCommunityPath() + "/testData/toxtest", 0);
    }
  }
}
