package com.jetbrains.python.testing.attest;

import com.jetbrains.python.testing.AbstractPythonTestRunConfigurationParams;

/**
 * User: catherine
 */
public interface PythonAtTestRunConfigurationParams {
  String getPattern();
  void setPattern(String pattern);
  AbstractPythonTestRunConfigurationParams getTestRunConfigurationParams();

}
