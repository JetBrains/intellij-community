package com.jetbrains.python.testing.unittest;

import com.jetbrains.python.testing.AbstractPythonTestRunConfigurationParams;

/**
 * @author Leonid Shalupov
 */
public interface PythonUnitTestRunConfigurationParams{
  String getPattern();
  void setPattern(String pattern);
  AbstractPythonTestRunConfigurationParams getTestRunConfigurationParams();
  boolean isPureUnittest();
  void setPureUnittest(boolean isPureUnittest);
}
