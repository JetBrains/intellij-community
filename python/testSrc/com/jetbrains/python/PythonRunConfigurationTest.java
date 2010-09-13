package com.jetbrains.python;

import com.intellij.execution.ExecutionException;
import com.jetbrains.python.fixtures.PyCommandLineTestCase;
import com.jetbrains.python.run.PythonConfigurationType;
import com.jetbrains.python.run.PythonRunConfiguration;
import com.jetbrains.python.testing.PythonUnitTestConfigurationType;
import com.jetbrains.python.testing.PythonUnitTestRunConfiguration;

import java.util.List;

/**
 * @author yole
 */
public class PythonRunConfigurationTest extends PyCommandLineTestCase {
  private static final String PY_SCRIPT = "foo.py";

  public void testUnitTestCommandLine() throws ExecutionException {
    PythonUnitTestRunConfiguration configuration = createConfiguration(PythonUnitTestConfigurationType.getInstance(),
                                                                       PythonUnitTestRunConfiguration.class);
    configuration.setScriptName(PY_SCRIPT);
    final List<String> params = buildRunCommandLine(configuration);
    assertTrue(params.get(0).endsWith("utrunner.py"));
    assertTrue(params.get(1).equals(PY_SCRIPT));
  }

  public void testDebugCommandLine() throws ExecutionException {
    PythonRunConfiguration configuration = createConfiguration(PythonConfigurationType.getInstance(),
                                                               PythonRunConfiguration.class);
    configuration.setScriptName(PY_SCRIPT);
    final List<String> params = buildDebugCommandLine(configuration);
    assertEquals(PythonHelpersLocator.getHelperPath("pydev/pydevd.py"), params.get(0));
    assertEquals("--client", params.get(1));
    assertEquals("--port", params.get(3));
    assertEquals("" + PORT, params.get(4));
    assertEquals("--file", params.get(5));
    assertEquals(PY_SCRIPT, params.get(6));
  }
}
