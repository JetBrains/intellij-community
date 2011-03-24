package com.jetbrains.python.testing;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RuntimeConfiguration;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.jetbrains.django.testRunner.DjangoTestsRunConfiguration;

/**
 * @author Roman.Chernyatchik
 */
public class PythonTRunnerConsoleProperties extends SMTRunnerConsoleProperties {
  public static final String FRAMEWORK_NAME = "PythonUnitTestRunner";

  private final boolean myIsEditable;

  public PythonTRunnerConsoleProperties(final RuntimeConfiguration config, final Executor executor) {
    super(config, FRAMEWORK_NAME, executor);

    myIsEditable = config instanceof DjangoTestsRunConfiguration;
  }

  @Override
  public boolean isEditable() {
    return myIsEditable;
  }
}
