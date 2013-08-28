package com.jetbrains.python.testing;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ModuleRunConfiguration;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;

/**
 * @author Roman.Chernyatchik
 */
public class PythonTRunnerConsoleProperties extends SMTRunnerConsoleProperties {
  public static final String FRAMEWORK_NAME = "PythonUnitTestRunner";

  private final boolean myIsEditable;

  public PythonTRunnerConsoleProperties(final ModuleRunConfiguration config, final Executor executor, boolean editable) {
    super(config, FRAMEWORK_NAME, executor);

    myIsEditable = editable;
  }

  @Override
  public boolean isEditable() {
    return myIsEditable;
  }
}
