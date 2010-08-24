package com.jetbrains.python;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerRegistry;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonConfigurationType;
import com.jetbrains.python.run.PythonRunConfiguration;
import com.jetbrains.python.testing.PythonUnitTestConfigurationType;
import com.jetbrains.python.testing.PythonUnitTestRunConfiguration;

import java.util.List;

/**
 * @author yole
 */
public class PythonRunConfigurationTest extends PyLightFixtureTestCase {
  private static final String PY_SCRIPT = "foo.py";
  private static final int PORT = 123;

  public void testUnitTestCommandLine() throws ExecutionException {
    final ConfigurationFactory factory = PythonUnitTestConfigurationType.getInstance().getConfigurationFactories()[0];
    final Project project = myFixture.getProject();
    PythonUnitTestRunConfiguration configuration = (PythonUnitTestRunConfiguration)factory.createTemplateConfiguration(project);
    configuration.setScriptName(PY_SCRIPT);
    final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    final PythonCommandLineState state = (PythonCommandLineState)configuration.getState(executor, new ExecutionEnvironment());
    final GeneralCommandLine generalCommandLine = state.generateCommandLine();
    final List<String> params = generalCommandLine.getParametersList().getList();
    assertTrue(params.get(0).endsWith("utrunner.py"));
    assertTrue(params.get(1).equals(PY_SCRIPT));
  }

  public void testDebugCommandLine() throws ExecutionException {
    final ConfigurationFactory factory = PythonConfigurationType.getInstance().getConfigurationFactories()[0];
    final Project project = myFixture.getProject();
    PythonRunConfiguration configuration = (PythonRunConfiguration)factory.createTemplateConfiguration(project);
    configuration.setScriptName(PY_SCRIPT);
    final Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();
    final PythonCommandLineState state = (PythonCommandLineState)configuration.getState(executor, new ExecutionEnvironment());
    PyDebugRunner debugRunner = (PyDebugRunner)RunnerRegistry.getInstance().findRunnerById(PyDebugRunner.PY_DEBUG_RUNNER);
    final GeneralCommandLine generalCommandLine =
      state.generateCommandLine(debugRunner.createCommandLinePatchers(state, configuration, PORT));
    final List<String> params = generalCommandLine.getParametersList().getList();
    assertEquals(PythonHelpersLocator.getHelperPath("pydev/pydevd.py"), params.get(0));
    assertEquals("--client", params.get(1));
    assertEquals("--port", params.get(3));
    assertEquals("" + PORT, params.get(4));
    assertEquals("--file", params.get(5));
    assertEquals(PY_SCRIPT, params.get(6));
  }
}
