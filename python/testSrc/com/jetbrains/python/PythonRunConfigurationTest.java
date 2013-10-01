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
