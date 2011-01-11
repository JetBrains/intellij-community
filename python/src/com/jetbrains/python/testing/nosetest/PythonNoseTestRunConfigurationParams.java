package com.jetbrains.python.testing.nosetest;

import com.jetbrains.python.testing.AbstractPythonTestRunConfigurationParams;

/**
 * User: catherine
 */
public interface PythonNoseTestRunConfigurationParams {
  String getParams();
  void setParams(String params);

  AbstractPythonTestRunConfigurationParams getTestRunConfigurationParams();
}
