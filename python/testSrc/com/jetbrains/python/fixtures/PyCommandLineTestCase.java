package com.jetbrains.python.fixtures;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerRegistry;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.PythonCommandLineState;

import java.util.List;

/**
 * @author yole
 */
public abstract class PyCommandLineTestCase extends PyTestCase {
  private static final int PORT = 123;

  protected static int verifyPyDevDParameters(List<String> params) {
    params = Lists.newArrayList(params);
    int debugParam = params.remove("--DEBUG_RECORD_SOCKET_READS") ? 1 : 0;
    assertEquals(PythonHelpersLocator.getHelperPath("pydev/pydevd.py"), params.get(0));
    assertEquals("--client", params.get(1));
    assertEquals("--port", params.get(3));
    assertEquals("" + PORT, params.get(4));
    assertEquals("--file", params.get(5));
    return 6 + debugParam;
  }

  protected <T extends AbstractPythonRunConfiguration> T createConfiguration(final ConfigurationType configurationType, Class<T> cls) {
    final ConfigurationFactory factory = configurationType.getConfigurationFactories()[0];
    final Project project = myFixture.getProject();
    return cls.cast(factory.createTemplateConfiguration(project));
  }

  protected static List<String> buildRunCommandLine(AbstractPythonRunConfiguration configuration) {
    try {
      final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
      final PythonCommandLineState state = (PythonCommandLineState)configuration.getState(executor, new ExecutionEnvironment());
      final GeneralCommandLine generalCommandLine = state.generateCommandLine();
      return generalCommandLine.getParametersList().getList();
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  protected static List<String> buildDebugCommandLine(AbstractPythonRunConfiguration configuration) {
    try {
      final Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();
      final PythonCommandLineState state = (PythonCommandLineState)configuration.getState(executor, new ExecutionEnvironment());
      PyDebugRunner debugRunner = (PyDebugRunner)RunnerRegistry.getInstance().findRunnerById(PyDebugRunner.PY_DEBUG_RUNNER);
      final GeneralCommandLine generalCommandLine =
        state.generateCommandLine(PyDebugRunner.createCommandLinePatchers(state, configuration, PORT));
      return generalCommandLine.getParametersList().getList();
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }
}
