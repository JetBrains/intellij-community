package com.jetbrains.env.ut;

import com.google.common.collect.ImmutableSet;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.testing.AbstractPythonTestRunConfiguration;
import com.jetbrains.python.testing.PythonTestConfigurationType;
import com.jetbrains.python.testing.pytest.PyTestRunConfiguration;

import java.util.Set;

/**
 * User : catherine
 */
public abstract class PyTestTestTask extends PyUnitTestTask {
  public PyTestTestTask(String workingFolder, String scriptName, String scriptParameters) {
    super(workingFolder, scriptName, scriptParameters);
  }

  public PyTestTestTask(String workingFolder, String scriptName) {
    this(workingFolder, scriptName, null);
  }

  public void runTestOn(String sdkHome) throws Exception {
    final Project project = getProject();
    final ConfigurationFactory factory = PythonTestConfigurationType.getInstance().PY_PYTEST_FACTORY;
    runConfiguration(factory, sdkHome, project);
  }

  @Override
  public Set<String> getTags() {
    return ImmutableSet.of("pytest");
  }

  protected void configure(AbstractPythonTestRunConfiguration config) {
    if (config instanceof PyTestRunConfiguration)
      ((PyTestRunConfiguration)config).setTestToRun(getScriptPath());
  }

}
