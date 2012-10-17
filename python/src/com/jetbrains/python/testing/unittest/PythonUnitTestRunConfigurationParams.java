package com.jetbrains.python.testing.unittest;

import com.jetbrains.python.testing.AbstractPythonTestRunConfigurationParams;

/**
 * @author Leonid Shalupov
 */
public interface PythonUnitTestRunConfigurationParams{
  AbstractPythonTestRunConfigurationParams getTestRunConfigurationParams();
  boolean isPureUnittest();
  void setPureUnittest(boolean isPureUnittest);


  String getParams();
  void setParams(String params);
  boolean useParam();
  void useParam(boolean useParam);
}
