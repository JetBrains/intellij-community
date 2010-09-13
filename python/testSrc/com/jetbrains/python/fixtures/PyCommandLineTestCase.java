package com.jetbrains.python.fixtures;

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
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.PythonCommandLineState;

import java.util.List;

/**
 * @author yole
 */
public abstract class PyCommandLineTestCase extends PyLightFixtureTestCase {
  protected static final int PORT = 123;

  protected <T extends AbstractPythonRunConfiguration> T createConfiguration(final ConfigurationType configurationType, Class<T> cls) {
    final ConfigurationFactory factory = configurationType.getConfigurationFactories()[0];
    final Project project = myFixture.getProject();
    return cls.cast(factory.createTemplateConfiguration(project));
  }

  protected static List<String> buildRunCommandLine(AbstractPythonRunConfiguration configuration) throws ExecutionException {
    final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    final PythonCommandLineState state = (PythonCommandLineState)configuration.getState(executor, new ExecutionEnvironment());
    final GeneralCommandLine generalCommandLine = state.generateCommandLine();
    return generalCommandLine.getParametersList().getList();
  }

  protected static List<String> buildDebugCommandLine(AbstractPythonRunConfiguration configuration) throws ExecutionException {
    final Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();
    final PythonCommandLineState state = (PythonCommandLineState)configuration.getState(executor, new ExecutionEnvironment());
    PyDebugRunner debugRunner = (PyDebugRunner)RunnerRegistry.getInstance().findRunnerById(PyDebugRunner.PY_DEBUG_RUNNER);
    final GeneralCommandLine generalCommandLine =
      state.generateCommandLine(debugRunner.createCommandLinePatchers(state, configuration, PORT));
    return generalCommandLine.getParametersList().getList();
  }
}
