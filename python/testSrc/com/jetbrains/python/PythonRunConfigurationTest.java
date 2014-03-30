/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyCommandLineTestCase;
import com.jetbrains.python.run.PythonConfigurationType;
import com.jetbrains.python.run.PythonRunConfiguration;
import com.jetbrains.python.testing.PythonTestConfigurationType;
import com.jetbrains.python.testing.unittest.PythonUnitTestRunConfiguration;
import junit.framework.Assert;

import java.util.List;

/**
 * @author yole
 */
public class PythonRunConfigurationTest extends PyCommandLineTestCase {
  private static final String PY_SCRIPT = "foo.py";

  public void testUnitTestCommandLine() {
    PythonUnitTestRunConfiguration configuration = createConfiguration(PythonTestConfigurationType.getInstance(),
                                                                       PythonUnitTestRunConfiguration.class);
    configuration.setScriptName(PY_SCRIPT);
    final List<String> params = buildRunCommandLine(configuration);
    Assert.assertTrue(params.get(0).endsWith("utrunner.py"));
    Assert.assertTrue(params.get(1).equals(PY_SCRIPT));
  }

  public void testDebugCommandLine() {
    PythonRunConfiguration configuration = createConfiguration(PythonConfigurationType.getInstance(),
                                                               PythonRunConfiguration.class);
    configuration.setScriptName(PY_SCRIPT);
    final List<String> params = buildDebugCommandLine(configuration);
    final int index = PyCommandLineTestCase.verifyPyDevDParameters(params);
    Assert.assertEquals(PY_SCRIPT, params.get(index));
  }
}
