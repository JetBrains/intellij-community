package com.jetbrains.python;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.testing.PythonUnitTestConfigurationType;
import com.jetbrains.python.testing.PythonUnitTestRunConfiguration;

import java.util.List;

/**
 * @author yole
 */
public class PythonUnitTestRunConfigurationTest extends PyLightFixtureTestCase {
  public void testCommandLine() throws ExecutionException {
    final ConfigurationFactory factory = PythonUnitTestConfigurationType.getInstance().getConfigurationFactories()[0];
    final Project project = myFixture.getProject();
    PythonUnitTestRunConfiguration configuration = (PythonUnitTestRunConfiguration) factory.createTemplateConfiguration(project);
    configuration.setScriptName("foo.py");
    final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    final PythonCommandLineState state = (PythonCommandLineState) configuration.getState(executor, new ExecutionEnvironment());
    final GeneralCommandLine generalCommandLine = state.generateCommandLine();
    final List<String> params = generalCommandLine.getParametersList().getList();
    assertTrue(params.get(0).endsWith("utrunner.py"));
    assertTrue(params.get(1).equals("foo.py"));
  }
}
