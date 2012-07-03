package com.jetbrains.env.ut;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.testing.PythonTestConfigurationType;

/**
 * User : catherine
 */
public abstract class PyDocTestTask extends PyUnitTestTask {
  public PyDocTestTask(String workingFolder, String scriptName, String scriptParameters) {
    super(workingFolder, scriptName, scriptParameters);
  }

  public PyDocTestTask(String workingFolder, String scriptName) {
    this(workingFolder, scriptName, null);
  }

  public void runTestOn(String sdkHome) throws Exception {
    final Project project = getProject();
    final ConfigurationFactory factory = PythonTestConfigurationType.getInstance().PY_DOCTEST_FACTORY;
    runConfiguration(factory, sdkHome, project);
  }
}
